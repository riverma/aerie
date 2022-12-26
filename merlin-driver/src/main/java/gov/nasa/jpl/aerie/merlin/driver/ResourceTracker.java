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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ResourceTracker {
  final Map<String, Resource<?>> resources = new HashMap<>();
  final Map<String, ProfilingState<?>> resourceProfiles = new HashMap<>();

  /** The set of queries depending on a given set of topics. */
  final Subscriptions<Topic<?>, String> waitingResources = new Subscriptions<>();

  final Map<String, Duration> resourceExpiries = new HashMap<>();

  final Set<Topic<?>> invalidatedTopics = new HashSet<>();


  void track(final String name, final Resource<?> resource) {
    resourceProfiles.put(name, new ProfilingState<>(resource, new Profile<>()));
    resources.put(name, resource);
  }

  void updateAllResourcesAt(final Duration currentTime, final LiveCells cells) {
    invalidatedTopics.clear();

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
  void updateResources(final Duration currentTime, final Duration delta, final LiveCells cells, final TemporalEventSource timeline, final boolean includeEndpoint) {
    updateInvalidatedResources(currentTime, cells, timeline);
    updateExpiredResources(currentTime, delta, cells, timeline, includeEndpoint);
  }

  void updateInvalidatedResources(final Duration elapsedTime, final LiveCells cells, final TemporalEventSource timeline) {
    final var invalidatedResources = new HashSet<String>();

    for (final var topic : invalidatedTopics) {
      invalidatedResources.addAll(waitingResources.invalidateTopic(topic));
    }

    invalidatedTopics.clear();

    for (final var resourceName : invalidatedResources) {
      resourceExpiries.remove(resourceName);

      TaskFrame.run(resources.get(resourceName), cells, (job, frame) -> {
        final var querier = new SimulationEngine.EngineQuerier(frame);
        resourceProfiles.get(resourceName).append(elapsedTime, querier);
        waitingResources.subscribeQuery(resourceName, querier.referencedTopics);

        final var expiry = querier.expiry.map(elapsedTime::plus);
        // This resource's no-later-than query time needs to be updated
        expiry.ifPresent(duration -> resourceExpiries.put(resourceName, duration));
      });
    }
  }

  private void updateExpiredResources(final Duration startTime, final Duration delta, final LiveCells cells, final TemporalEventSource timeline, final boolean includeEndpoint) {
    final var endTime = startTime.plus(delta);
    var currentTime = startTime;
    while (true) {
      // Now, we need to query any resources that may expire between elapsedTime and elapsedTime + delta

      var minExpiry = Duration.MAX_VALUE;
      String argMin = null;

      for (final var entry : resourceExpiries.entrySet()) {
        final var resourceName = entry.getKey();
        final var expiry = entry.getValue();

        if (expiry.shorterThan(minExpiry)) {
          minExpiry = expiry;
          argMin = resourceName;
        }
      }
      final var resourceName = argMin;
      final var resourceQueryTime = minExpiry;

      if (resourceName == null) break;
      if (!(resourceQueryTime.shorterThan(endTime) || includeEndpoint && resourceQueryTime.isEqualTo(endTime))) {
        break;
      }

      timeline.add(resourceQueryTime.minus(currentTime));

      resourceExpiries.remove(resourceName);
      TaskFrame.run(resources.get(resourceName), cells, (job, frame) -> {
        final var querier = new SimulationEngine.EngineQuerier(frame);
        resourceProfiles.get(resourceName).append(resourceQueryTime, querier);
        waitingResources.subscribeQuery(resourceName, querier.referencedTopics);

        final var expiry = querier.expiry.map(resourceQueryTime::plus);
        // This resource's no-later-than query time needs to be updated
        expiry.ifPresent(duration -> resourceExpiries.put(resourceName, duration));
      });

      currentTime = resourceQueryTime;
    }

    timeline.add(endTime.minus(currentTime));
  }

  void invalidateTopics(final Set<Topic<?>> topics) {
    invalidatedTopics.addAll(topics);
  }
}
