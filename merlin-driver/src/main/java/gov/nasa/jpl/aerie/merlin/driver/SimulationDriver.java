package gov.nasa.jpl.aerie.merlin.driver;

import gov.nasa.jpl.aerie.json.Unit;
import gov.nasa.jpl.aerie.merlin.driver.engine.SimulationEngine;
import gov.nasa.jpl.aerie.merlin.driver.timeline.TemporalEventSource;
import gov.nasa.jpl.aerie.merlin.protocol.driver.Topic;
import gov.nasa.jpl.aerie.merlin.protocol.model.TaskFactory;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.InstantiationException;
import gov.nasa.jpl.aerie.merlin.protocol.types.TaskStatus;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Map;

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

      final var resourceTracker = new ResourceTracker(missionModel.getInitialCells());

      // Begin tracking all resources.
      for (final var entry : missionModel.getResources().entrySet()) {
        final var name = entry.getKey();
        final var resource = entry.getValue();
        resourceTracker.track(name, resource);
      }

      // Start daemon task(s) immediately, before anything else happens.
      engine.scheduleTask(Duration.ZERO, missionModel.getDaemon());
      engine.step();

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

      // The sole purpose of this task is to make sure the simulation has "stuff to do" until the simulationDuration.
      engine.scheduleTask(Duration.ZERO, executor -> $ ->
          TaskStatus.delayed(
              simulationDuration,
              $$ -> TaskStatus.completed(Unit.UNIT)));

      // Drive the engine until we're out of time.
      // TERMINATION: Actually, we might never break if real time never progresses forward.
      while (engine.hasStuffToDoThrough(simulationDuration)) {
        engine.step();
      }

      // Replay the timeline to collect resource profiles
      for (final var timePoint : timeline) {
        resourceTracker.updateResources(timePoint);
      }

      final var topics = missionModel.getTopics();
      return SimulationEngine.computeResults(engine, startTime, simulationDuration, activityTopic, timeline, topics, resourceTracker.resourceProfiles());
    }
  }

  public static <Model, Return>
  void simulateTask(final MissionModel<Model> missionModel, final TaskFactory<Return> task) {
    /* The top-level simulation timeline. */
    final var timeline = new TemporalEventSource();
    try (final var engine = new SimulationEngine(timeline, missionModel.getInitialCells())) {
      // Start daemon task(s) immediately, before anything else happens.
      engine.scheduleTask(Duration.ZERO, missionModel.getDaemon());
      engine.step();

      // Drive the engine until we're out of time.
      // TERMINATION: Actually, we might never break if real time never progresses forward.
      final var taskId = engine.scheduleTask(Duration.ZERO, task);
      while (!engine.isTaskComplete(taskId)) {
        engine.step();
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
}
