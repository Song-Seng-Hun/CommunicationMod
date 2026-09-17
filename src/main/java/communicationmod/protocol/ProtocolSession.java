package communicationmod.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Game-thread-confined v2 core. Does not load game classes, launch processes or use Steam. */
public final class ProtocolSession {
    public static final int VERSION = 2;
    private static final int MAX_LINE_CHARS = 1024 * 1024;
    private static final int MAX_REQUESTS = 16384;
    private final String sessionId = UUID.randomUUID().toString();
    private final Set<String> consumedRequests = new HashSet<>();
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Consumer<RuntimeException> diagnostics;
    private boolean connected;
    private boolean ready;
    private long stateId;
    private JsonObject snapshot;

    public ProtocolSession(Consumer<RuntimeException> diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public String receive(String line) {
        if (line == null || line.length() > MAX_LINE_CHARS) return error("INVALID_MESSAGE");
        if (line.trim().equalsIgnoreCase("ready")) return error("VERSION_MISMATCH");
        final JsonObject request;
        final String type;
        try {
            JsonElement parsed = StrictJson.parse(line);
            if (!parsed.isJsonObject()) return error("INVALID_MESSAGE");
            request = parsed.getAsJsonObject();
            type = string(request, "type");
        } catch (RuntimeException malformed) { return error("INVALID_MESSAGE"); }
        if (type.equals("hello")) {
            try {
                if (integer(request, "protocol_version") != VERSION) return error("VERSION_MISMATCH");
            } catch (RuntimeException malformed) { return error("VERSION_MISMATCH"); }
            if (connected) return error("ALREADY_CONNECTED");
            connected = true;
            JsonObject reply = envelope("hello");
            JsonArray capabilities = new JsonArray();
            capabilities.add("state_ids");
            capabilities.add("action_validation");
            capabilities.add("duplicate_rejection");
            reply.add("capabilities", capabilities);
            return reply.toString();
        }
        if (!connected) return error("HANDSHAKE_REQUIRED");
        if (type.equals("get_state")) return snapshot == null ? error("STATE_UNAVAILABLE") : snapshot.toString();
        if (!type.equals("act")) return error("UNKNOWN_MESSAGE");
        return act(request);
    }

    private String act(JsonObject request) {
        final String requestId;
        final String actionId;
        final JsonObject arguments;
        final long expectedState;
        try {
            if (!string(request, "session_id").equals(sessionId)) return error("SESSION_MISMATCH");
            requestId = string(request, "request_id");
            actionId = string(request, "action_id");
            expectedState = integer(request, "state_id");
            JsonElement args = request.get("arguments");
            if (args == null || !args.isJsonObject()) return error("INVALID_ARGUMENTS");
            arguments = args.getAsJsonObject();
        } catch (RuntimeException malformed) { return error("INVALID_MESSAGE"); }
        if (consumedRequests.contains(requestId)) return error("DUPLICATE_REQUEST");
        if (expectedState != stateId) return error("STALE_STATE");
        if (!ready) return error("NOT_READY");
        Action action = actions.get(actionId);
        if (action == null) return error("UNKNOWN_ACTION");
        if (consumedRequests.size() >= MAX_REQUESTS) return error("SESSION_LIMIT");
        try { action.validate.accept(copy(arguments)); }
        catch (IllegalArgumentException invalid) { return error("INVALID_ARGUMENTS"); }
        catch (RuntimeException failure) {
            report(failure);
            invalidate();
            return error("VALIDATION_FAILED");
        }
        // Consume before the callback: a partially applied failing action must not replay.
        consumedRequests.add(requestId);
        invalidate();
        JsonObject result = envelope("result");
        result.addProperty("request_id", requestId);
        result.addProperty("state_id", expectedState);
        try {
            action.execute.accept(copy(arguments));
            result.addProperty("status", "applied");
        } catch (RuntimeException failure) {
            report(failure);
            invalidate();
            result.addProperty("status", "failed");
            result.addProperty("code", "ACTION_FAILED");
        }
        return result.toString();
    }

    /** Caller must supply only player-visible data and already legal UI actions. */
    public String publish(JsonObject runtime, JsonObject observation, List<Action> offered,
                          boolean stable, String support) {
        if (!connected) throw new IllegalStateException("Handshake required");
        Objects.requireNonNull(support);
        Map<String, Action> next = new LinkedHashMap<>();
        for (Action action : offered) {
            if (next.put(action.id, action) != null) throw new IllegalArgumentException("Duplicate action ID");
        }
        JsonObject nextSnapshot = envelope("state");
        nextSnapshot.add("runtime", copy(runtime));
        nextSnapshot.add("observation", copy(observation));
        nextSnapshot.addProperty("support", support);
        boolean nextReady = stable && support.equals("supported");
        JsonArray choices = new JsonArray();
        if (nextReady) {
            for (Action action : next.values()) {
                JsonObject choice = new JsonObject();
                choice.addProperty("id", action.id);
                choice.addProperty("label", action.label);
                choice.add("parameters", copy(action.parameters));
                choices.add(choice);
            }
        }
        nextSnapshot.addProperty("state_id", ++stateId);
        nextSnapshot.addProperty("ready", nextReady);
        nextSnapshot.add("actions", choices);
        actions.clear();
        if (nextReady) actions.putAll(next);
        ready = nextReady;
        snapshot = nextSnapshot;
        return snapshot.toString();
    }

    /** Must be called on any external/manual change or transition before accepting commands. */
    public void invalidate() {
        ready = false;
        actions.clear();
        if (snapshot != null) {
            snapshot = copy(snapshot);
            snapshot.addProperty("state_id", ++stateId);
            snapshot.addProperty("ready", false);
            snapshot.add("actions", new JsonArray());
        }
    }

    private JsonObject envelope(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("protocol_version", VERSION);
        object.addProperty("session_id", sessionId);
        return object;
    }

    private String error(String code) {
        JsonObject object = envelope("error");
        object.addProperty("code", code);
        return object.toString();
    }

    private void report(RuntimeException failure) {
        try { diagnostics.accept(failure); }
        catch (RuntimeException ignored) { /* Diagnostics must not re-enable/replay actions. */ }
    }

    private static JsonObject copy(JsonObject object) {
        return new JsonParser().parse(Objects.requireNonNull(object).toString()).getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key);
        String text = value.getAsString();
        if (text.isEmpty() || text.length() > 128) throw new IllegalArgumentException(key);
        return text;
    }

    private static long integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
        return value.getAsBigDecimal().longValueExact();
    }

    public static final class Action {
        private final String id;
        private final String label;
        private final JsonObject parameters;
        private final Consumer<JsonObject> validate;
        private final Consumer<JsonObject> execute;

        public Action(String id, String label, JsonObject parameters,
                      Consumer<JsonObject> validate, Consumer<JsonObject> execute) {
            if (id == null || id.isEmpty() || id.length() > 128) throw new IllegalArgumentException("Invalid action ID");
            this.id = id;
            this.label = Objects.requireNonNull(label);
            this.parameters = copy(parameters);
            this.validate = Objects.requireNonNull(validate);
            this.execute = Objects.requireNonNull(execute);
        }
    }
}
