package gov.nasa.jpl.aerie.constraints.tree;

import gov.nasa.jpl.aerie.constraints.model.EvaluationEnvironment;
import gov.nasa.jpl.aerie.constraints.model.LinearProfile;
import gov.nasa.jpl.aerie.constraints.model.LinearEquation;
import gov.nasa.jpl.aerie.constraints.model.SimulationResults;
import gov.nasa.jpl.aerie.constraints.time.AbsoluteInterval;
import gov.nasa.jpl.aerie.constraints.time.Interval;
import gov.nasa.jpl.aerie.constraints.time.Segment;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.util.Objects;
import java.util.Set;

public record RealValue(double value, double rate, AbsoluteInterval interval) implements Expression<LinearProfile> {

  public RealValue(final double value) {
    this(value, 0.0, AbsoluteInterval.FOREVER);
  }

  @Override
  public LinearProfile evaluate(final SimulationResults results, final Interval bounds, final EvaluationEnvironment environment) {
    final Interval relativeInterval = interval.toRelative(results.planStart);
    return new LinearProfile(
        Segment.of(Interval.intersect(bounds, relativeInterval), new LinearEquation(Duration.ZERO, value, rate))
    );
  }

  @Override
  public void extractResources(final Set<String> names) {
  }

  @Override
  public String prettyPrint(final String prefix) {
    return String.format(
        "\n%s(value %s)",
        prefix,
        this.value
    );
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof RealValue)) return false;
    final var o = (RealValue) obj;

    return Objects.equals(this.value, o.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.value);
  }
}
