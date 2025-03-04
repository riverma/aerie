package gov.nasa.jpl.aerie.scheduler.worker.services;

import gov.nasa.jpl.aerie.constraints.tree.ActivitySpan;
import gov.nasa.jpl.aerie.constraints.tree.DiscreteResource;
import gov.nasa.jpl.aerie.constraints.tree.DiscreteValue;
import gov.nasa.jpl.aerie.constraints.tree.ForEachActivitySpans;
import gov.nasa.jpl.aerie.constraints.tree.GreaterThan;
import gov.nasa.jpl.aerie.constraints.tree.LessThan;
import gov.nasa.jpl.aerie.constraints.tree.ListExpressionAt;
import gov.nasa.jpl.aerie.constraints.tree.LongerThan;
import gov.nasa.jpl.aerie.constraints.tree.Not;
import gov.nasa.jpl.aerie.constraints.tree.Or;
import gov.nasa.jpl.aerie.constraints.tree.ProfileExpression;
import gov.nasa.jpl.aerie.constraints.tree.RealParameter;
import gov.nasa.jpl.aerie.constraints.tree.RealResource;
import gov.nasa.jpl.aerie.constraints.tree.RealValue;
import gov.nasa.jpl.aerie.constraints.tree.Starts;
import gov.nasa.jpl.aerie.constraints.tree.StructExpressionAt;
import gov.nasa.jpl.aerie.constraints.tree.ValueAt;
import gov.nasa.jpl.aerie.constraints.tree.WindowsFromSpans;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.scheduler.TimeUtility;
import gov.nasa.jpl.aerie.scheduler.constraints.timeexpressions.TimeAnchor;
import gov.nasa.jpl.aerie.scheduler.server.models.MissionModelId;
import gov.nasa.jpl.aerie.scheduler.server.models.PlanId;
import gov.nasa.jpl.aerie.scheduler.server.models.SchedulingDSL;
import gov.nasa.jpl.aerie.scheduler.server.services.MissionModelService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.HOUR;
import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.MICROSECONDS;
import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.SECOND;
import static gov.nasa.jpl.aerie.scheduler.server.services.TypescriptCodeGenerationServiceTestFixtures.MISSION_MODEL_TYPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulingDSLCompilationServiceTests {
  private static final PlanId PLAN_ID = new PlanId(1L);
  private static final MissionModelService missionModelService = new MissionModelService() {
    @Override
    public MissionModelTypes getMissionModelTypes(final PlanId missionModelId)
    {
      return MISSION_MODEL_TYPES;
    }

    @Override
    public MissionModelTypes getMissionModelTypes(final MissionModelId missionModelId)
    {
      return MISSION_MODEL_TYPES;
    }
  };
  SchedulingDSLCompilationService schedulingDSLCompilationService;

  @BeforeAll
  void setUp() throws IOException {
    schedulingDSLCompilationService = new SchedulingDSLCompilationService();
  }

  @AfterAll
  void tearDown() {
    schedulingDSLCompilationService.close();
  }

  @Test
  void  testSchedulingDSL_mutex()
  {
    final var result = schedulingDSLCompilationService.compileGlobalSchedulingCondition(
        missionModelService,
        PLAN_ID, """
                  export default function myCondition() {
                    return GlobalSchedulingCondition.mutex([ActivityTypes.SampleActivity2], [ActivityTypes.SampleActivity1])
                  }
              """);
    final var expectedGoalDefinition = new SchedulingDSL.ConditionSpecifier.AndCondition(List.of(
        new SchedulingDSL.ConditionSpecifier.GlobalSchedulingCondition(
            new Not(
                new Or(
                    new WindowsFromSpans(
                        new ForEachActivitySpans(
                            "SampleActivity1",
                            "span activity alias 0",
                            new ActivitySpan("span activity alias 0"))
                    )
                )
            ),
            List.of("SampleActivity2")
        ),
        new SchedulingDSL.ConditionSpecifier.GlobalSchedulingCondition(
            new Not(
                new Or(
                    new WindowsFromSpans(
                        new ForEachActivitySpans(
                            "SampleActivity2",
                            "span activity alias 1",
                            new ActivitySpan("span activity alias 1"))
                    )
                )
            ),
            List.of("SampleActivity1")
        )
    ));

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success<SchedulingDSL.ConditionSpecifier> r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.ConditionSpecifier> r) {
      fail(r.toString());
    }
  }

  private static StructExpressionAt getSampleActivity1Parameters(){
    return new StructExpressionAt(Map.ofEntries(
        Map.entry("variant", new ProfileExpression<>(new DiscreteValue(SerializedValue.of("option2")))),
        Map.entry("fancy", new ProfileExpression<>(new StructExpressionAt(Map.ofEntries(
                      Map.entry("subfield1", new ProfileExpression<>(new DiscreteValue(SerializedValue.of("value1")))),
                      Map.entry("subfield2", new ProfileExpression<>(new ListExpressionAt(List.of(new ProfileExpression<>(new StructExpressionAt(Map.of("subsubfield1",
                                                                                                        new ProfileExpression<>(new DiscreteValue(SerializedValue.of(2 )))))))))))
                  ))
        ),
        Map.entry("duration", new ProfileExpression<>(new DiscreteValue(SerializedValue.of(Duration.of(1, HOUR).in(MICROSECONDS))))))
    );
  }

  @Test
  void  testSchedulingDSL_basic()
  {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivity1({
                      variant: 'option2',
                      fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2.0}]},
                      duration: Temporal.Duration.from({ hours: 1 })
                    }),
                    interval: Temporal.Duration.from({ hours: 1 })
                  })
                }
            """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
        new SchedulingDSL.ActivityTemplate(
            "SampleActivity1",
            getSampleActivity1Parameters()
        ),
        HOUR);
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success<SchedulingDSL.GoalSpecifier> r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testSchedulingDSL_helper_function()
  {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return myHelper(ActivityTemplates.SampleActivity1({
                    variant: 'option2',
                    fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                    duration: Temporal.Duration.from({ hours: 1 })
                  }))
                }
                function myHelper(activityTemplate) {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate,
                    interval: Temporal.Duration.from({ hours: 1 })
                  })
                }
            """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
        new SchedulingDSL.ActivityTemplate(
            "SampleActivity1",
            getSampleActivity1Parameters()
        ),
        HOUR);
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testSchedulingDSL_variable_not_defined() {
    final SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> actualErrors;
    actualErrors = (SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier>) schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
          PLAN_ID, """
                export default function myGoal() {
                  const x = 4 - 2
                  return myHelper(ActivityTemplates.SampleActivity1({
                    variant: 'option2',
                    fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                    duration: Temporal.Duration.from({ hours: 1 })
                  }))
                }
                function myHelper(activityTemplate) {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate,
                    interval: x
                  })
                }
              """);
    assertTrue(
        actualErrors.errors()
                    .stream()
                    .anyMatch(e -> e.message().contains("TypeError: TS2304 Cannot find name 'x'."))
    );
  }

  @Test
  void testSchedulingDSL_applyWhen()
  {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
        export default function myGoal() {
          return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivity1({
                        variant: 'option2',
                        fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                        duration: Temporal.Duration.from({ hours : 1 })
                    }),
                    interval: Temporal.Duration.from({ hours : 1 })
                  }).applyWhen(Real.Resource(Resources["/sample/resource/1"]).greaterThan(2.0))
        }
        """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.GoalApplyWhen(
        new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
            new SchedulingDSL.ActivityTemplate(
                "SampleActivity1",
                getSampleActivity1Parameters()
            ),
            HOUR
        ),
        new GreaterThan(
            new RealResource("/sample/resource/1"),
            new RealValue(2.0)
        )
    );
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testSchedulingDSL_wrong_return_type() {
    final SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> actualErrors;
    actualErrors = (SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier>) schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
          PLAN_ID, """
                export default function myGoal() {
                  return 5
                }
              """);
    assertTrue(
        actualErrors.errors()
                    .stream()
                    .anyMatch(e -> e.message().contains("TypeError: TS2322 Incorrect return type. Expected: 'Goal | Promise<Goal>', Actual: 'number'."))
    );
  }

  @Test
  void testSchedulingDSL_temporal() {
    final SchedulingDSLCompilationService.SchedulingDSLCompilationResult<?> result;
    result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID,
        """
                    export default () => Goal.ActivityRecurrenceGoal({
                      activityTemplate: ActivityTemplates.SampleActivity1({
                        variant: 'option2',
                        fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                        duration: Temporal.Duration.from({ hours: 1 })
                      }),
                      interval:  Temporal.Duration.from({days: 1})
                    })
                    """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
        new SchedulingDSL.ActivityTemplate(
            "SampleActivity1",
            getSampleActivity1Parameters()
        ),
        Duration.HOURS.times(24)
    );
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error r) {
      fail(r.toString());
    }
  }


  @Test
  void testHugeGoal() {
    // This test is intended to create a Goal that is bigger than the node subprocess's standard input buffer
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivity1({
                      variant: 'option2',
                      fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                      duration: Temporal.Duration.from({ hours: 1 })
                    }),
                    interval: Temporal.Duration.from({ hours: 1 })
                  })
                }
            """ + " ".repeat(9001));
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
        new SchedulingDSL.ActivityTemplate(
            "SampleActivity1",
            getSampleActivity1Parameters()
        ),
        HOUR
    );
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testCoexistenceGoalActivityExpression() {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
          export default function() {
            return Goal.CoexistenceGoal({
              activityTemplate: (span) => ActivityTemplates.SampleActivity1({
                variant: 'option2',
                fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2.0}]},
                duration: Temporal.Duration.from({ hours : 1 })
              }),
              forEach: ActivityExpression.ofType(ActivityTypes.SampleActivity2),
              startsAt: TimingConstraint.singleton(WindowProperty.START).plus(Temporal.Duration.from({ seconds : 1 }))
            })
          }
        """);

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(
          new SchedulingDSL.GoalSpecifier.CoexistenceGoalDefinition(
              new SchedulingDSL.ActivityTemplate("SampleActivity1",
                                                 getSampleActivity1Parameters()
              ),
              "coexistence activity alias 0",
              new SchedulingDSL.ConstraintExpression.ActivityExpression("SampleActivity2"),
              Optional.of(new SchedulingDSL.ActivityTimingConstraint(TimeAnchor.START, TimeUtility.Operator.PLUS, SECOND, true)),
              Optional.empty()
          ),
          r.value()
      );
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testCoexistenceGoalParameterReference() {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
          export default function() {
            return Goal.CoexistenceGoal({
              activityTemplate: (anchorActivity) => ActivityTemplates.SampleActivity1({
                variant: 'option2',
                fancy: { subfield1: 'value1', subfield2: [{subsubfield1: anchorActivity.parameters.quantity}]},
                duration: Temporal.Duration.from({ hours : 1 })
              }),
              forEach: ActivityExpression.ofType(ActivityTypes.SampleActivity2),
              startsAt: TimingConstraint.singleton(WindowProperty.START).plus(Temporal.Duration.from({ seconds : 1 }))
            })
          }
        """);

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(
          new SchedulingDSL.GoalSpecifier.CoexistenceGoalDefinition(
              new SchedulingDSL.ActivityTemplate("SampleActivity1",
                   new StructExpressionAt(Map.ofEntries(
                      Map.entry("variant", new ProfileExpression<>(new DiscreteValue(SerializedValue.of("option2")))),
                      Map.entry("fancy", new ProfileExpression<>(new StructExpressionAt(Map.ofEntries(
                                    Map.entry("subfield1", new ProfileExpression<>(new DiscreteValue(SerializedValue.of("value1")))),
                                    Map.entry("subfield2", new ProfileExpression<>(new ListExpressionAt(List.of(new ProfileExpression<>(new StructExpressionAt(Map.of("subsubfield1",
                                                                                                                                                                      new ProfileExpression<>(new RealParameter("coexistence activity alias 0", "quantity"))))))))))
                                ))
                      ),
                      Map.entry("duration", new ProfileExpression<>(new DiscreteValue(SerializedValue.of(Duration.of(1, HOUR).in(MICROSECONDS))))))
                  )
              ),
              "coexistence activity alias 0",
              new SchedulingDSL.ConstraintExpression.ActivityExpression("SampleActivity2"),
              Optional.of(new SchedulingDSL.ActivityTimingConstraint(TimeAnchor.START, TimeUtility.Operator.PLUS, SECOND, true)),
              Optional.empty()
          ),
          r.value()
      );
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testCoexistenceGoalParameterReferenceValueAt() {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
          export default function() {
            return Goal.CoexistenceGoal({
              activityTemplate: (anchorActivity) => ActivityTemplates.SampleActivity1({
                variant: Discrete.Resource('/sample/resource/3').valueAt(anchorActivity.span().starts()),
                fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2.0}]},
                duration: Temporal.Duration.from({ hours : 1 })
              }),
              forEach: ActivityExpression.ofType(ActivityTypes.SampleActivity2),
              startsAt: TimingConstraint.singleton(WindowProperty.START).plus(Temporal.Duration.from({ seconds : 1 }))
            })
          }
        """);

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(
          new SchedulingDSL.GoalSpecifier.CoexistenceGoalDefinition(
              new SchedulingDSL.ActivityTemplate("SampleActivity1",
                                                 new StructExpressionAt(Map.ofEntries(
                                                     Map.entry("variant", new ProfileExpression<>(new ValueAt<>(new ProfileExpression<>(new DiscreteResource("/sample/resource/3")),new Starts<>(new ActivitySpan("coexistence activity alias 0"))))),
                                                     Map.entry("fancy", new ProfileExpression<>(new StructExpressionAt(Map.ofEntries(
                                                                   Map.entry("subfield1", new ProfileExpression<>(new DiscreteValue(SerializedValue.of("value1")))),
                                                                   Map.entry("subfield2", new ProfileExpression<>(new ListExpressionAt(List.of(new ProfileExpression<>(new StructExpressionAt(Map.of("subsubfield1",
                                                                                                                                                                                                     new ProfileExpression<>(new DiscreteValue(SerializedValue.of(2)))))))))))
                                                               ))
                                                     ),
                                                     Map.entry("duration", new ProfileExpression<>(new DiscreteValue(SerializedValue.of(Duration.of(1, HOUR).in(MICROSECONDS))))))
                                                 )
              ),
              "coexistence activity alias 0",
              new SchedulingDSL.ConstraintExpression.ActivityExpression("SampleActivity2"),
              Optional.of(new SchedulingDSL.ActivityTimingConstraint(TimeAnchor.START, TimeUtility.Operator.PLUS, SECOND, true)),
              Optional.empty()
          ),
          r.value()
      );
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void strictTypeCheckingTest_astNode() {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID,
        """
          interface FakeGoal {
            and(...others: FakeGoal[]): FakeGoal;
            or(...others: FakeGoal[]): FakeGoal;
            applyWhen(window: Windows): FakeGoal;
          }
          export default function() {
            const myFakeGoal: FakeGoal = {
              and: (...others: FakeGoal[]) => {
                return myFakeGoal;
              },
              or: (...others: FakeGoal[]) => {
                return myFakeGoal;
              },
              applyWhen: (window: Windows) => {
                return myFakeGoal;
              },
            };
            return myFakeGoal;
          }
        """);

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      assertEquals(1, r.errors().size());
      assertTrue(r.errors().get(0).message().contains("Incorrect return type. Expected: 'Goal | Promise<Goal>', Actual: 'FakeGoal'."));
    }
  }

  @Test
  void strictTypeCheckingTest_transition() {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID,
        """
          export default function() {
            return Goal.CoexistenceGoal({
              activityTemplate: ActivityTemplates.SampleActivity1({
                variant: 'option2',
                fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                duration: Temporal.Duration.from({ hours: 1 })
              }),
              forEach: Discrete.Resource(Resources["/sample/resource/1"]).transition("Chiquita", "Dole"),
              startsAt: TimingConstraint.singleton(WindowProperty.END)
            })
          }
        """);

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      assertEquals(1, r.errors().size());
      assertEquals(
          "TypeError: TS2345 Argument of type 'string' is not assignable to parameter of type 'number'.",
          r.errors().get(0).message()
      );
    }
  }

  @Test
  void testSchedulingDSL_emptyActivityCorrect()
  {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivityEmpty(),
                    interval: Temporal.Duration.from({ hours : 1 }) // 1 hour in microseconds
                  })
                }
            """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
        new SchedulingDSL.ActivityTemplate(
            "SampleActivityEmpty",
            new StructExpressionAt(Map.of())
        ),
        HOUR
    );
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(expectedGoalDefinition, r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testSchedulingDSL_emptyActivityBogus()
  {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivityEmpty({ fake: "bogus" }),
                    interval: Temporal.Duration.from({ hours : 1 }) // 1 hour in microseconds
                  })
                }
            """);
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      assertEquals(1, r.errors().size());
      assertEquals(
          "TypeError: TS2554 Expected 0 arguments, but got 1.",
          r.errors().get(0).message()
      );
    }
    else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      fail(r.value().toString());
    }
  }

  @Test
  void testCoexistenceGoalStateConstraint() {
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID,
        """
          const micro = (m: number) => Temporal.Duration.from({microseconds: m});
          export default function() {
            return Goal.CoexistenceGoal({
              activityTemplate: ActivityTemplates.SampleActivity1({
                variant: 'option2',
                fancy: { subfield1: 'value1', subfield2: [{subsubfield1: 2}]},
                duration: Temporal.Duration.from({ hours: 1 })
              }),
              forEach: Real.Resource(Resources["/sample/resource/1"]).greaterThan(50.0).longerThan(micro(10)),
              startsAt: TimingConstraint.singleton(WindowProperty.END)
            })
          }
        """);

    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success<?> r) {
      assertEquals(
          new SchedulingDSL.GoalSpecifier.CoexistenceGoalDefinition(
              new SchedulingDSL.ActivityTemplate("SampleActivity1",
                                                 getSampleActivity1Parameters()
              ),
              "coexistence activity alias 0",
              new SchedulingDSL.ConstraintExpression.WindowsExpression(new LongerThan(new GreaterThan(new RealResource("/sample/resource/1"), new RealValue(50.0)), Duration.of(10, Duration.MICROSECOND))),
              Optional.of(new SchedulingDSL.ActivityTimingConstraint(TimeAnchor.END, TimeUtility.Operator.PLUS, Duration.ZERO, true)),
              Optional.empty()
          ),
          r.value()
      );
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error<SchedulingDSL.GoalSpecifier> r) {
      fail(r.toString());
    }
  }

  @Test
  void testWindowsExpression() {
    final var result = schedulingDSLCompilationService.compileGlobalSchedulingCondition(
        missionModelService,
        PLAN_ID,
        """
          export default function() {
            return GlobalSchedulingCondition.scheduleOnlyWhen([], Real.Resource("/sample/resource/1").lessThan(5.0));
          }
        """);
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(
          new SchedulingDSL.ConditionSpecifier.GlobalSchedulingCondition(
              new LessThan(
                  new RealResource("/sample/resource/1"),
                  new RealValue(5.0)),
              List.of()),
          r.value());
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error r) {
      fail(r.toString());
    }
  }

  @Test
  void testAndGoal(){
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivityEmpty(),
                    interval: Temporal.Duration.from({ hours : 1 }) // 1 hour in microseconds
                  }).and(
                    Goal.ActivityRecurrenceGoal({
                      activityTemplate: ActivityTemplates.SampleActivityEmpty(),
                      interval: Temporal.Duration.from({ hours : 2 }) // 2 hour in microseconds
                    })
                  )
                }
            """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.GoalAnd(List.of(
        new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
          new SchedulingDSL.ActivityTemplate(
              "SampleActivityEmpty",
              new StructExpressionAt(Map.of())
          ),
          HOUR
    ), new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
            new SchedulingDSL.ActivityTemplate(
                "SampleActivityEmpty",
                new StructExpressionAt(Map.of())
            ),
            HOUR.times(2)
        )));
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success r) {
      assertEquals(
          expectedGoalDefinition,
          r.value()
      );
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error r) {
      fail(r.toString());
    }
  }

  @Test
  void testOrGoal(){
    final var result = schedulingDSLCompilationService.compileSchedulingGoalDSL(
        missionModelService,
        PLAN_ID, """
                export default function myGoal() {
                  return Goal.ActivityRecurrenceGoal({
                    activityTemplate: ActivityTemplates.SampleActivityEmpty(),
                    interval: Temporal.Duration.from({ hours: 1 }) // 1 hour in microseconds
                  }).or(
                    Goal.ActivityRecurrenceGoal({
                      activityTemplate: ActivityTemplates.SampleActivityEmpty(),
                      interval: Temporal.Duration.from({ hours : 2 }) // 2 hour in microseconds
                    })
                  )
                }
            """);
    final var expectedGoalDefinition = new SchedulingDSL.GoalSpecifier.GoalOr(List.of(
        new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
            new SchedulingDSL.ActivityTemplate(
                "SampleActivityEmpty",
                new StructExpressionAt(Map.of())
            ),
            HOUR
        ), new SchedulingDSL.GoalSpecifier.RecurrenceGoalDefinition(
            new SchedulingDSL.ActivityTemplate(
                "SampleActivityEmpty",
                new StructExpressionAt(Map.of())
            ),
            HOUR.times(2)
        )));
    if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Success<SchedulingDSL.GoalSpecifier> r) {
      assertEquals(
          expectedGoalDefinition,
          r.value()
      );
    } else if (result instanceof SchedulingDSLCompilationService.SchedulingDSLCompilationResult.Error r) {
      fail(r.toString());
    }
  }

}
