package gov.nasa.jpl.aerie.merlin.driver;

import gov.nasa.jpl.aerie.merlin.driver.engine.Profile;
import gov.nasa.jpl.aerie.merlin.driver.engine.ProfilingState;
import gov.nasa.jpl.aerie.merlin.driver.engine.SimulationEngine;
import gov.nasa.jpl.aerie.merlin.driver.engine.Subscriptions;
import gov.nasa.jpl.aerie.merlin.driver.engine.TaskFrame;
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

  private final TemporalEventSource ourOwnTimeline;
  private final LiveCells cells;
  private final Iterator<TemporalEventSource.TimePoint> timelineIterator;
  private Duration elapsedTime;

  public ResourceTracker(final TemporalEventSource timeline, final LiveCells initialCells) {
    this.ourOwnTimeline = new TemporalEventSource();
    this.cells = new LiveCells(ourOwnTimeline, initialCells);
    this.timelineIterator = timeline.iterator();
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
  public void updateResources() {
    while (timelineIterator.hasNext()) {
      final var timePoint = timelineIterator.next();
      if (timePoint instanceof TemporalEventSource.TimePoint.Delta p) {
        updateExpiredResources(p.delta()); // this call updates ourOwnTimeline and elapsedTime
      } else if (timePoint instanceof TemporalEventSource.TimePoint.Commit p) {
        ourOwnTimeline.add(p.events());
        expireInvalidatedResources(p.topics());
      } else {
        throw new Error("Unhandled variant of " + TemporalEventSource.TimePoint.class.getCanonicalName() + ": " + timePoint);
      }
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

      ourOwnTimeline.add(resourceQueryTime.minus(elapsedTime));
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
}
