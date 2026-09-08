package communicationmod.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

/** Strict protocol parser: duplicate keys and more than 64 nested containers fail. */
final class StrictJson {
    private StrictJson() { }

    static JsonElement parse(String text) {
        rejectRawStringControls(text);
        try (JsonReader input = new JsonReader(new StringReader(text))) {
            input.setLenient(false);
            JsonElement value = read(input, 0);
            if (input.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing input");
            return value;
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid JSON message", invalid);
        }
    }

    // Gson's strict reader still accepts literal controls within quoted tokens.
    // Scan the original representation, so valid escaped controls remain legal.
    private static void rejectRawStringControls(String text) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!quoted) {
                if (character == '"') quoted = true;
            } else {
                if (character < 32) throw new IllegalArgumentException("Raw control in JSON string");
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            }
        }
    }

    private static JsonElement read(JsonReader input, int depth) throws IOException {
        JsonToken token = input.peek();
        if ((token == JsonToken.BEGIN_OBJECT || token == JsonToken.BEGIN_ARRAY) && depth >= 64) {
            throw new IOException("JSON nesting limit exceeded");
        }
        switch (token) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                input.beginObject();
                while (input.hasNext()) {
                    String name = input.nextName();
                    if (object.has(name)) throw new IOException("Duplicate JSON field");
                    object.add(name, read(input, depth + 1));
                }
                input.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                input.beginArray();
                while (input.hasNext()) array.add(read(input, depth + 1));
                input.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(input.nextString());
            case NUMBER:
                return new JsonPrimitive(new BigDecimal(input.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(input.nextBoolean());
            case NULL:
                input.nextNull();
                return JsonNull.INSTANCE;
            default:
                throw new IOException("Expected a JSON value");
        }
    }
}
