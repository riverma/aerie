package gov.nasa.jpl.aerie.merlin.driver;

import gov.nasa.jpl.aerie.merlin.driver.engine.SimulationEngine;
import gov.nasa.jpl.aerie.merlin.driver.timeline.LiveCells;
import gov.nasa.jpl.aerie.merlin.driver.timeline.TemporalEventSource;
import gov.nasa.jpl.aerie.merlin.protocol.driver.Topic;
import gov.nasa.jpl.aerie.merlin.protocol.model.TaskFactory;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.InstantiationException;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SimulationDriver {
  public static <Model>
  SimulationResults simulate(
      final MissionModel<Model> missionModel,
      final Map<ActivityInstanceId, Pair<Duration, SerializedActivity>> schedule,
      final Instant startTime,
      final Duration simulationDuration
  ) {
    /* The top-level simulation timeline. */
    var timeline = new TemporalEventSource();
    try (final var engine = new SimulationEngine(timeline, missionModel.getInitialCells())) {
      /* The current real time. */
      var elapsedTime = Duration.ZERO;

      final var resourceTracker = new ResourceTracker(timeline, missionModel.getInitialCells());

      // Begin tracking all resources.
      for (final var entry : missionModel.getResources().entrySet()) {
        final var name = entry.getKey();
        final var resource = entry.getValue();
        resourceTracker.track(name, resource);
      }

      // Start daemon task(s) immediately, before anything else happens.
      engine.scheduleTask(Duration.ZERO, missionModel.getDaemon());
      {
        final var batch = engine.extractNextJobs(Duration.MAX_VALUE);
        engine.performJobs(batch, Duration.MAX_VALUE);
      }

      // Specify a topic on which tasks can log the activity they're associated with.
      final var activityTopic = new Topic<ActivityInstanceId>();

      // Schedule all activities.
      for (final var entry : schedule.entrySet()) {
        final var directiveId = entry.getKey();
        final var startOffset = entry.getValue().getLeft();
        final var serializedDirective = entry.getValue().getRight();

        final TaskFactory<?> task;
        try {
          task = missionModel.getTaskFactory(serializedDirective);
        } catch (final InstantiationException ex) {
          // All activity instantiations are assumed to be validated by this point
          throw new Error("Unexpected state: activity instantiation %s failed with: %s"
              .formatted(serializedDirective.getTypeName(), ex.toString()));
        }

        final var taskId = engine.scheduleTask(startOffset, emitAndThen(directiveId, activityTopic, task));
      }

      // Drive the engine until we're out of time.
      // TERMINATION: Actually, we might never break if real time never progresses forward.
      // TODO: Advance a dense time counter so that future tasks are strictly ordered relative to these,
      //   even if they occur at the same real time.
      while (true) {
        final var batch = engine.extractNextJobs(simulationDuration);

        timeline.add(batch.offsetFromStart().minus(elapsedTime));
        engine.performJobs(batch, simulationDuration);
        resourceTracker.updateResources();

        elapsedTime = batch.offsetFromStart();
        if (batch.jobs().isEmpty()) break;
      }

      final var topics = missionModel.getTopics();
      return SimulationEngine.computeResults(engine, startTime, elapsedTime, activityTopic, timeline, topics, resourceTracker.resourceProfiles());
    }
  }

  public static <Model, Return>
  void simulateTask(final MissionModel<Model> missionModel, final TaskFactory<Return> task) {
    /* The top-level simulation timeline. */
    var timeline = new TemporalEventSource();
    var cells = new LiveCells(timeline, missionModel.getInitialCells());
    try (final var engine = new SimulationEngine(timeline, cells)) {
      /* The current real time. */
      var elapsedTime = Duration.ZERO;

      // Start daemon task(s) immediately, before anything else happens.
      engine.scheduleTask(Duration.ZERO, missionModel.getDaemon());
      {
        final var batch = engine.extractNextJobs(Duration.MAX_VALUE);
        engine.performJobs(batch, Duration.MAX_VALUE);
      }

      // Schedule all activities.
      final var taskId = engine.scheduleTask(elapsedTime, task);

      // Drive the engine until we're out of time.
      // TERMINATION: Actually, we might never break if real time never progresses forward.
      while (true) {
        final var batch = engine.extractNextJobs(Duration.MAX_VALUE);

        timeline.add(batch.offsetFromStart().minus(elapsedTime));
        engine.performJobs(batch, Duration.MAX_VALUE);

        elapsedTime = batch.offsetFromStart();
        if (engine.isTaskComplete(taskId)) break;
      }
    }
  }

  private static <E, T>
  TaskFactory<T> emitAndThen(final E event, final Topic<E> topic, final TaskFactory<T> continuation) {
    return executor -> scheduler -> {
      scheduler.emit(event, topic);
      return continuation.create(executor).step(scheduler);
    };
  }

  private static <E> Set<E> union(Set<E> a, Set<E> b) {
    final var res = new HashSet<>(a);
    res.addAll(b);
    return res;
  }
}
