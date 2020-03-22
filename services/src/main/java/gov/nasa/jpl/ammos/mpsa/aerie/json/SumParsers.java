package gov.nasa.jpl.ammos.mpsa.aerie.json;

import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

public abstract class SumParsers {
  private SumParsers() {}

  public static <T> VariantJsonParser<T> sumP() {
    return new VariantJsonParser<>();
  }

  public static final class VariantJsonParser<T> implements JsonParser<T> {
    private final JsonParser<T> nullParser;
    private final JsonParser<T> trueParser;
    private final JsonParser<T> falseParser;
    private final JsonParser<T> stringParser;
    private final JsonParser<T> numberParser;
    private final JsonParser<T> arrayParser;
    private final JsonParser<T> objectParser;

    private VariantJsonParser() {
      this.nullParser = null;
      this.trueParser = null;
      this.falseParser = null;
      this.stringParser = null;
      this.numberParser = null;
      this.arrayParser = null;
      this.objectParser = null;
    }

    private VariantJsonParser(final VariantJsonParser<T> other, final ValueType valueType, final JsonParser<T> valueParser) {
      this.nullParser = (valueType == ValueType.NULL) ? valueParser : other.nullParser;
      this.trueParser = (valueType == ValueType.TRUE) ? valueParser : other.trueParser;
      this.falseParser = (valueType == ValueType.FALSE) ? valueParser : other.falseParser;
      this.stringParser = (valueType == ValueType.STRING) ? valueParser : other.stringParser;
      this.numberParser = (valueType == ValueType.NUMBER) ? valueParser : other.numberParser;
      this.arrayParser = (valueType == ValueType.ARRAY) ? valueParser : other.arrayParser;
      this.objectParser = (valueType == ValueType.OBJECT) ? valueParser : other.objectParser;
    }

    @Override
    public JsonParseResult<T> parse(final JsonValue json) {
      final JsonParser<T> parser;
      switch (json.getValueType()) {
        case NULL: parser = nullParser; break;
        case TRUE: parser = trueParser; break;
        case FALSE: parser = falseParser; break;
        case STRING: parser = stringParser; break;
        case NUMBER: parser = numberParser; break;
        case ARRAY: parser = arrayParser; break;
        case OBJECT: parser = objectParser; break;
        default: throw new Error("Unexpected JSON value type");
      }

      if (parser == null) return JsonParseResult.failure();
      return parser.parse(json);
    }

    public VariantJsonParser<T> when(final ValueType valueType, final JsonParser<T> valueParser) {
      return new VariantJsonParser<>(this, valueType, valueParser);
    }
  }
}
