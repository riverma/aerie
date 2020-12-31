package gov.nasa.jpl.ammos.mpsa.aerie.merlin.framework.resources.discrete;

import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.Approximator;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.DiscreteApproximator;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.ResourceSolver;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.timeline.History;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.DelimitedDynamics;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.SerializedValue;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.ValueSchema;
import gov.nasa.jpl.ammos.mpsa.aerie.time.Duration;
import gov.nasa.jpl.ammos.mpsa.aerie.time.Window;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.typemappers.ValueMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DiscreteResourceSolver<$Schema, Resource>
    implements ResourceSolver<$Schema, DiscreteResource<$Schema, Resource>, Resource, Set<Resource>>
{
  private final ValueMapper<Resource> mapper;

  public DiscreteResourceSolver(final ValueMapper<Resource> mapper) {
    this.mapper = Objects.requireNonNull(mapper);
  }

  @Override
  public DelimitedDynamics<Resource> getDynamics(
      final DiscreteResource<$Schema, Resource> resource,
      final History<? extends $Schema> now)
  {
    return resource.getDynamics(now);
  }

  @Override
  public Approximator<Resource> getApproximator() {
    return Approximator.discrete(new DiscreteApproximator<>() {
      @Override
      public Iterable<DelimitedDynamics<SerializedValue>> approximate(final Resource value) {
        return List.of(DelimitedDynamics.persistent(DiscreteResourceSolver.this.mapper.serializeValue(value)));
      }

      @Override
      public ValueSchema getSchema() {
        return DiscreteResourceSolver.this.mapper.getValueSchema();
      }
    });
  }

  @Override
  public Optional<Duration> firstSatisfied(final Resource value, final Set<Resource> values, final Window selection) {
    if (values.contains(value)) {
      return Optional.of(selection.start);
    } else {
      return Optional.empty();
    }
  }
}
