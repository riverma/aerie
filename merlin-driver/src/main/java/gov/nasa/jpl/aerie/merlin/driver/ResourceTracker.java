package gov.nasa.jpl.aerie.merlin.driver;

import gov.nasa.jpl.aerie.merlin.driver.engine.Profile;
import gov.nasa.jpl.aerie.merlin.driver.engine.ProfilingState;
import gov.nasa.jpl.aerie.merlin.driver.engine.SimulationEngine;
import gov.nasa.jpl.aerie.merlin.driver.engine.Subscriptions;
import gov.nasa.jpl.aerie.merlin.driver.engine.TaskFrame;
import gov.nasa.jpl.aerie.merlin.driver.timeline.Cell;
import gov.nasa.jpl.aerie.merlin.driver.timeline.EventSource;
import gov.nasa.jpl.aerie.merlin.driver.timeline.LiveCells;
import gov.nasa.jpl.aerie.merlin.driver.timeline.TemporalEventSource;
import gov.nasa.jpl.aerie.merlin.protocol.driver.Topic;
import gov.nasa.jpl.aerie.merlin.protocol.model.Resource;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ResourceTracker {
  private final Map<String, Resource<?>> resources = new HashMap<>();
  private final Map<String, ProfilingState<?>> resourceProfiles = new HashMap<>();

  /** The set of queries depending on a given set of topics. */
  private final Subscriptions<Topic<?>, String> waitingResources = new Subscriptions<>();

  private final Map<String, Duration> resourceExpiries = new HashMap<>();

  private final MyInternalTimeline ourOwnTimeline;
  private final LiveCells cells;
  private Duration elapsedTime;

  public ResourceTracker(final TemporalEventSource timeline, final LiveCells initialCells) {
    this.ourOwnTimeline = new MyInternalTimeline(timeline);
    this.cells = new LiveCells(ourOwnTimeline, initialCells);
    this.elapsedTime = Duration.ZERO;
  }


  public void track(final String name, final Resource<?> resource) {
    resourceProfiles.put(name, new ProfilingState<>(resource, new Profile<>()));
    resources.put(name, resource);
    resourceExpiries.put(name, Duration.ZERO);
  }

  public void updateAllResourcesAt(final Duration currentTime) {
    for (final var entry : resources.entrySet()) {
      final var resourceName = entry.getKey();
      final var resource = entry.getValue();

      resourceExpiries.remove(resourceName);
      TaskFrame.run(resource, cells, (job, frame) -> {
        final var querier = new SimulationEngine.EngineQuerier(frame);
        resourceProfiles.get(resourceName).append(currentTime, querier);
        waitingResources.subscribeQuery(resourceName, querier.referencedTopics);

        final var expiry = querier.expiry.map(currentTime::plus);
        // This resource's no-later-than query time needs to be updated
        expiry.ifPresent(duration -> resourceExpiries.put(resourceName, duration));
      });
    }
  }

  /**
   * Post condition: timeline will be stepped up to the endpoint
   */
  public void updateResources(final TemporalEventSource.TimePoint timePoint) {
    if (timePoint instanceof TemporalEventSource.TimePoint.Delta p) {
      updateExpiredResources(p.delta()); // this call updates ourOwnTimeline and elapsedTime
    } else if (timePoint instanceof TemporalEventSource.TimePoint.Commit p) {
      ourOwnTimeline.add(p.events());
      expireInvalidatedResources(p.topics());
    } else {
      throw new Error("Unhandled variant of " + TemporalEventSource.TimePoint.class.getCanonicalName() + ": " + timePoint);
    }
  }

  private void expireInvalidatedResources(final Set<Topic<?>> invalidatedTopics) {
    for (final var topic : invalidatedTopics) {
      for (final var resourceName : waitingResources.invalidateTopic(topic)) {
        resourceExpiries.put(resourceName, Duration.ZERO);
      }
    }
  }

  private void updateExpiredResources(final Duration delta) {
    final var endTime = elapsedTime.plus(delta);

    while (!resourceExpiries.isEmpty()) {
      final var nextExpiry = resourceExpiries
          .entrySet()
          .stream()
          .min(Map.Entry.comparingByValue())
          .orElseThrow();

      final var resourceName = nextExpiry.getKey();
      final var resourceQueryTime = nextExpiry.getValue();

      if (resourceQueryTime.longerThan(endTime)) break;

      ourOwnTimeline.advance(resourceQueryTime.minus(elapsedTime));
      elapsedTime = elapsedTime.plus(resourceQueryTime.minus(elapsedTime));

      resourceExpiries.remove(resourceName);
      TaskFrame.run(resources.get(resourceName), cells, (job, frame) -> {
        final var querier = new SimulationEngine.EngineQuerier(frame);
        resourceProfiles.get(resourceName).append(resourceQueryTime, querier);
        waitingResources.subscribeQuery(resourceName, querier.referencedTopics);

        final var expiry = querier.expiry.map(resourceQueryTime::plus);
        // This resource's no-later-than query time needs to be updated
        expiry.ifPresent(duration -> resourceExpiries.put(resourceName, duration));
      });
    }

    ourOwnTimeline.add(endTime.minus(elapsedTime));
    elapsedTime = endTime;
  }

  public Map<String, ProfilingState<?>> resourceProfiles() {
    return resourceProfiles;
  }

  /**
   * @param pointCount Index into input timeline
   * @param timeAfterPoint Offset from the point indicated by pointCount
   */
  record DenseTime(int pointCount, Duration timeAfterPoint) {}

  class MyInternalTimeline implements EventSource {

    private final TemporalEventSource timeline;
    private DenseTime limit;

    public MyInternalTimeline(TemporalEventSource timeline) {
      this.timeline = timeline;
      this.limit = new DenseTime(0, Duration.ZERO);
    }

    void advance(final DenseTime newLimit) {
      this.limit = newLimit;
    }

    @Override
    public Cursor cursor() {
      return new Cursor() {
        private final Iterator<TemporalEventSource.TimePoint> timelineIterator = MyInternalTimeline.this.timeline.iterator();

        /* The history of an offset includes all points up to but not including timeline.get(pointCount) */
        private DenseTime offset = new DenseTime(0, Duration.ZERO);

        @Override
        public void stepUp(final Cell<?> cell) {
          // Extend timeline iterator to the current limit

          for (var i = this.offset.pointCount; i < MyInternalTimeline.this.limit.pointCount(); i++) {
            final var point = this.timelineIterator.next();

            if (point instanceof TemporalEventSource.TimePoint.Delta p) {
              cell.step(p.delta().minus(this.offset.timeAfterPoint()));
              this.offset = new DenseTime(i + 1, Duration.ZERO);
            } else if (point instanceof TemporalEventSource.TimePoint.Commit p) {
              if (!this.offset.timeAfterPoint().isZero()) throw new Error("Bad.");
              if (cell.isInterestedIn(p.topics())) cell.apply(p.events());
            } else {
              throw new IllegalStateException();
            }
          }

          final var remainingOffset = MyInternalTimeline.this.limit.timeAfterPoint().minus(this.offset.timeAfterPoint());
          if (!remainingOffset.isZero()) {
            cell.step(remainingOffset);
          }

          this.offset = limit;
        }
      };
    }
  }
}
