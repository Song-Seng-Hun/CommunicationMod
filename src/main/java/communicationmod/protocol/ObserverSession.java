package communicationmod.protocol;

import com.google.gson.*;
import java.util.Collections;

/** Passive v2 connection for human-play validation. Never installs action callbacks. */
public final class ObserverSession {
    private final ProtocolSession protocol = new ProtocolSession(e -> System.err.println("[COMM-OBSERVER] " + e));
    private boolean connected;

    public String receive(String line) {
        String response = protocol.receive(line);
        JsonObject reply = new JsonParser().parse(response).getAsJsonObject();
        if ("hello".equals(reply.get("type").getAsString())) {
            connected = true;
            reply.addProperty("mode", "observation_only");
            reply.getAsJsonArray("capabilities").add("localized_observation");
            return reply.toString();
        }
        return response;
    }

    public boolean connected() { return connected; }

    public String publish(JsonObject runtime, JsonObject legacyObservation) {
        return protocol.publish(runtime, publicObservation(legacyObservation), Collections.emptyList(), false, "observation_only");
    }

    public static JsonObject publicObservation(JsonObject legacyObservation) {
        JsonObject view = new JsonParser().parse(legacyObservation.toString()).getAsJsonObject();
        view.remove("available_commands");
        view.remove("ready_for_command");
        // The old converter also includes internal debugging identifiers. They are
        // not UI information and must not reach the observation client.
        removeInternalFields(view);
        return view;
    }

    private static void removeInternalFields(JsonElement value) {
        if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) removeInternalFields(child);
        else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String key : new String[]{"current_action", "move_id", "last_move_id", "second_last_move_id", "move_base_damage"}) object.remove(key);
            if (object.has("intent") && !object.get("intent").getAsString().startsWith("ATTACK")) {
                object.remove("move_adjusted_damage");object.remove("move_hits");
            }
            for (java.util.Map.Entry<String,JsonElement> entry : object.entrySet()) removeInternalFields(entry.getValue());
        }
    }
}
