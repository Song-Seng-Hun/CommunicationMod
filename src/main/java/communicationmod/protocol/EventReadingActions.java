package communicationmod.protocol;

import communicationmod.observation.EventReading;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Supplements a v2 screen adapter, not a legacy text-command shim or an AI narrator. */
public final class EventReadingActions {
    private EventReadingActions() { }

    public static List<ProtocolSession.Action> offer(EventReading reading, Runnable refresh) {
        Objects.requireNonNull(reading); Objects.requireNonNull(refresh);
        if (!"discussion_required".equals(reading.phase())) return Collections.emptyList();
        String id = reading.readingId();
        JsonObject schema = new JsonObject(), properties = new JsonObject();
        JsonObject readingId = new JsonObject(), commentary = new JsonObject();
        readingId.addProperty("type", "string"); JsonArray allowed = new JsonArray(); allowed.add(id);
        readingId.add("enum", allowed); properties.add("reading_id", readingId);
        commentary.addProperty("type", "string"); commentary.addProperty("minLength", 1); commentary.addProperty("maxLength", 4096);
        properties.add("commentary", commentary);
        schema.addProperty("type", "object"); schema.addProperty("additionalProperties", false);
        schema.add("properties", properties);
        JsonArray required = new JsonArray(); required.add("reading_id"); required.add("commentary"); schema.add("required", required);
        Consumer<JsonObject> validate = args -> {
            refresh.run();
            if (args.entrySet().size() != 2 || !id.equals(string(args, "reading_id"))) throw new IllegalArgumentException("Changed reading");
            reading.validateAcknowledgement(id, string(args, "commentary"));
        };
        return Collections.singletonList(new ProtocolSession.Action("acknowledge_event_reading",
            "Confirm this event page was presented and discussed", schema, validate, args -> {
                validate.accept(args);
                reading.acknowledge(id, string(args, "commentary"));
            }));
    }

    private static String string(JsonObject args, String key) {
        JsonElement value = args.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key);
        return value.getAsString();
    }
}
