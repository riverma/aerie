package gov.nasa.jpl.aerie.merlin.server.http;

import gov.nasa.jpl.aerie.constraints.model.Violation;
import gov.nasa.jpl.aerie.constraints.time.Window;
import gov.nasa.jpl.aerie.json.JsonParseResult.FailureReason;
import gov.nasa.jpl.aerie.merlin.driver.SimulatedActivity;
import gov.nasa.jpl.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.aerie.merlin.protocol.types.Parameter;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.merlin.protocol.types.ValueSchema;
import gov.nasa.jpl.aerie.merlin.server.exceptions.NoSuchPlanException;
import gov.nasa.jpl.aerie.merlin.server.models.AdaptationFacade;
import gov.nasa.jpl.aerie.merlin.server.remotes.AdaptationAccessException;
import gov.nasa.jpl.aerie.merlin.server.services.GetSimulationResultsAction;
import gov.nasa.jpl.aerie.merlin.server.services.LocalAdaptationService;
import gov.nasa.jpl.aerie.merlin.server.services.UnexpectedSubtypeError;
import org.apache.commons.lang3.tuple.Pair;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ResponseSerializers {
  public static <T> JsonValue serializeNullable(final Function<T, JsonValue> serializer, final T value) {
    if (value != null) return serializer.apply(value);
    else return JsonValue.NULL;
  }

  public static <T> JsonValue
  serializeIterable(final Function<T, JsonValue> elementSerializer, final Iterable<T> elements) {
    if (elements == null) return JsonValue.NULL;

    final var builder = Json.createArrayBuilder();
    for (final var element : elements) builder.add(elementSerializer.apply(element));
    return builder.build();
  }

  public static <T> JsonValue serializeMap(final Function<T, JsonValue> fieldSerializer, final Map<String, T> fields) {
    if (fields == null) return JsonValue.NULL;

    final var builder = Json.createObjectBuilder();
    for (final var entry : fields.entrySet()) builder.add(entry.getKey(), fieldSerializer.apply(entry.getValue()));
    return builder.build();
  }

  public static JsonValue serializeValueSchema(final ValueSchema schema) {
    if (schema == null) return JsonValue.NULL;

    return schema.match(new ValueSchemaSerializer());
  }

  public static JsonValue serializeOrderedParameter(final Pair<Integer, Parameter> entry) {
    if (entry == null) return JsonValue.NULL;

    return Json.createObjectBuilder()
        .add("schema", entry.getRight().schema().match(new ValueSchemaSerializer()))
        .add("order", entry.getLeft())
        .build();
  }

  public static JsonValue serializeValueSchemas(final Map<String, ValueSchema> schemas) {
    if (schemas == null) return JsonValue.NULL;

    final var builder = Json.createArrayBuilder();
    schemas.forEach((k, v) -> builder.add(Json.createObjectBuilder()
      .add("name", k)
      .add("schema", serializeValueSchema(v))));
    return builder.build();
  }

  public static JsonValue serializeSample(final Pair<Duration, SerializedValue> element) {
    if (element == null) return JsonValue.NULL;
    return Json
        .createObjectBuilder()
        .add("x", serializeDuration(element.getLeft()))
        .add("y", serializeArgument(element.getRight()))
        .build();
  }

  public static JsonValue serializeString(final String value) {
    if (value == null) return JsonValue.NULL;
    return Json.createValue(value);
  }

  public static JsonValue serializeStringList(final List<String> elements) {
    return serializeIterable(ResponseSerializers::serializeString, elements);
  }

  public static JsonValue serializeArgument(final SerializedValue parameter) {
    if (parameter == null) return JsonValue.NULL;
    return parameter.match(new ArgumentSerializationVisitor());
  }

  public static JsonValue serializeArgumentMap(final Map<String, SerializedValue> fields) {
    return serializeMap(ResponseSerializers::serializeArgument, fields);
  }

  public static JsonValue serializeEffectiveArgumentMap(final Map<String, SerializedValue> fields) {
    return Json.createObjectBuilder()
       .add("success", JsonValue.TRUE)
       .add("arguments", serializeMap(ResponseSerializers::serializeArgument, fields))
       .build();
  }

  public static JsonValue serializeConstraintViolation(final Violation violation) {
    return Json
        .createObjectBuilder()
        .add("associations", Json
            .createObjectBuilder()
            .add("activityInstanceIds", serializeIterable(Json::createValue, violation.activityInstanceIds))
            .add("resourceIds", serializeIterable(Json::createValue, violation.resourceNames))
            .build())
        .add("windows", serializeIterable(ResponseSerializers::serializeWindow, violation.violationWindows))
        .build();
  }

  public static JsonValue serializeWindow(final Window window) {
    return Json.createObjectBuilder()
               .add("start", window.start.in(Duration.MICROSECONDS))
               .add("end", window.end.in(Duration.MICROSECONDS))
               .build();
  }

  public static JsonValue serializeSimulatedActivity(final SimulatedActivity simulatedActivity) {
    return Json
        .createObjectBuilder()
        .add("type", simulatedActivity.type)
        .add("parameters", serializeArgumentMap(simulatedActivity.parameters))
        .add("startTimestamp", serializeTimestamp(simulatedActivity.start))
        .add("duration", serializeDuration(simulatedActivity.duration))
        .add("parent", serializeNullable(Json::createValue, simulatedActivity.parentId))
        .add("children", serializeIterable(Json::createValue, simulatedActivity.childIds))
        .build();
  }

  public static JsonValue serializeSimulationResults(final SimulationResults results, final Map<String, List<Violation>> violations) {
    return Json
        .createObjectBuilder()
        .add("start", serializeTimestamp(results.startTime))
        .add("resources", serializeMap(
            elements -> serializeIterable(ResponseSerializers::serializeSample, elements),
            results.resourceSamples))
        .add("constraints", serializeMap(v -> serializeIterable(ResponseSerializers::serializeConstraintViolation, v), violations))
        .add("activities", serializeMap(ResponseSerializers::serializeSimulatedActivity, results.simulatedActivities))
        .build();
  }

  public static JsonValue serializeSimulationResultsResponse(final GetSimulationResultsAction.Response response) {
    if (response instanceof GetSimulationResultsAction.Response.Incomplete) {
      return Json
          .createObjectBuilder()
          .add("status", "incomplete")
          .build();
    } else if (response instanceof GetSimulationResultsAction.Response.Failed r) {
      return Json
          .createObjectBuilder()
          .add("status", "failed")
          .add("reason", r.reason())
          .build();
    } else if (response instanceof GetSimulationResultsAction.Response.Complete r) {
      return Json
          .createObjectBuilder()
          .add("status", "complete")
          .add("results", serializeSimulationResults(r.results(), r.violations()))
          .build();
     } else {
      throw new UnexpectedSubtypeError(GetSimulationResultsAction.Response.class, response);
    }
  }

  public static JsonValue serializeTimestamp(final TemporalAccessor instant) {
    final var formattedTimestamp = DateTimeFormatter
        .ofPattern("uuuu-DDD'T'HH:mm:ss.SSSSSS")
        .withZone(ZoneOffset.UTC)
        .format(instant);

    return Json.createValue(formattedTimestamp);
  }

  public static JsonValue serializeDuration(final Duration timestamp) {
    return Json.createValue(timestamp.in(Duration.MICROSECONDS));
  }

  public static JsonValue serializeFailures(final List<String> failures) {
    if (failures.size() > 0) {
      return Json.createObjectBuilder()
                 .add("success", JsonValue.FALSE)
                 .add("errors", Json.createArrayBuilder(failures))
                 .build();
    } else {
      return Json.createObjectBuilder()
                 .add("success", JsonValue.TRUE)
                 .build();
    }
  }

  public static JsonValue serializeJsonParsingException(final JsonParsingException ex) {
    // TODO: Improve diagnostic information
    return Json.createObjectBuilder()
        .add("message", "invalid json")
        .build();
  }

  public static JsonValue serializeInvalidJsonException(final InvalidJsonException ex) {
    return Json.createObjectBuilder()
               .add("kind", "invalid-entity")
               .add("message", "invalid json")
               .build();
  }

  public static JsonValue serializeInvalidEntityException(final InvalidEntityException ex) {
    return Json.createObjectBuilder()
               .add("kind", "invalid-entity")
               .add("failures", serializeIterable(ResponseSerializers::serializeFailureReason, ex.failures))
               .build();
  }

  public static JsonValue serializeAdaptationContractException(final AdaptationFacade.AdaptationContractException ex) {
    // TODO: Improve diagnostic information
    return Json.createObjectBuilder()
               .add("message", ex.getMessage())
               .build();
  }

  public static JsonValue serializeAdaptationLoadException(
      final LocalAdaptationService.AdaptationLoadException ex)
  {
    // TODO: Improve diagnostic information?
    return Json.createObjectBuilder()
               .add("message", ex.getMessage())
               .build();
  }

  public static JsonValue serializeAdaptationAccessException(final AdaptationAccessException ex) {
    // TODO: Improve diagnostic information?
    return Json.createObjectBuilder()
               .add("message", ex.getMessage())
               .build();
  }

  public static JsonValue serializeFailureReason(final FailureReason failure) {
    return Json.createObjectBuilder()
               .add("breadcrumbs", serializeIterable(ResponseSerializers::serializeParseFailureBreadcrumb, failure.breadcrumbs()))
               .add("message", failure.reason())
               .build();
  }

  public static JsonValue serializeParseFailureBreadcrumb(final gov.nasa.jpl.aerie.json.Breadcrumb breadcrumb) {
    return breadcrumb.visit(new gov.nasa.jpl.aerie.json.Breadcrumb.BreadcrumbVisitor<>() {
      @Override
      public JsonValue onString(final String s) {
        return Json.createValue(s);
      }

      @Override
      public JsonValue onInteger(final Integer i) {
        return Json.createValue(i);
      }
    });
  }

  public static JsonValue serializeNoSuchPlanException(final NoSuchPlanException ex) {
    // TODO: Improve diagnostic information
    return Json.createObjectBuilder()
        .add("message", "no such plan")
        .build();
  }

  private static final class ValueSchemaSerializer implements ValueSchema.Visitor<JsonValue> {
    @Override
    public JsonValue onReal() {
      return Json
          .createObjectBuilder()
          .add("type", "real")
          .build();
    }

    @Override
    public JsonValue onInt() {
      return Json
          .createObjectBuilder()
          .add("type", "int")
          .build();
    }

    @Override
    public JsonValue onBoolean() {
      return Json
          .createObjectBuilder()
          .add("type", "boolean")
          .build();
    }

    @Override
    public JsonValue onString() {
      return Json
          .createObjectBuilder()
          .add("type", "string")
          .build();
    }

    @Override
    public JsonValue onDuration() {
      return Json
          .createObjectBuilder()
          .add("type", "duration")
          .build();
    }

    @Override
    public JsonValue onPath() {
      return Json
          .createObjectBuilder()
          .add("type", "path")
          .build();
    }

    @Override
    public JsonValue onSeries(final ValueSchema itemSchema) {
      return Json
          .createObjectBuilder()
          .add("type", "series")
          .add("items", itemSchema.match(this))
          .build();
    }

    @Override
    public JsonValue onStruct(final Map<String, ValueSchema> parameterSchemas) {
      return Json
          .createObjectBuilder()
          .add("type", "struct")
          .add("items", serializeMap(x -> x.match(this), parameterSchemas))
          .build();
    }

    @Override
    public JsonValue onVariant(final List<ValueSchema.Variant> variants) {
      return Json
          .createObjectBuilder()
          .add("type", "variant")
          .add("variants", serializeIterable(
              v -> Json
                  .createObjectBuilder()
                  .add("key", v.key)
                  .add("label", v.label)
                  .build(),
              variants))
          .build();
    }
  }

  private static final class ArgumentSerializationVisitor implements SerializedValue.Visitor<JsonValue> {
    @Override
    public JsonValue onNull() {
      return JsonValue.NULL;
    }

    @Override
    public JsonValue onReal(final double value) {
      return Json.createValue(value);
    }

    @Override
    public JsonValue onInt(final long value) {
      return Json.createValue(value);
    }

    @Override
    public JsonValue onBoolean(final boolean value) {
      return (value) ? JsonValue.TRUE : JsonValue.FALSE;
    }

    @Override
    public JsonValue onString(final String value) {
      return Json.createValue(value);
    }

    @Override
    public JsonValue onMap(final Map<String, SerializedValue> fields) {
      return serializeMap(x -> x.match(this), fields);
    }

    @Override
    public JsonValue onList(final List<SerializedValue> elements) {
      return serializeIterable(x -> x.match(this), elements);
    }
  }
}
