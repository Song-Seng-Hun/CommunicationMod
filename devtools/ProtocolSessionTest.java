import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import communicationmod.protocol.ProtocolSession;

public final class ProtocolSessionTest {
    private static JsonObject parse(String line) { return new JsonParser().parse(line).getAsJsonObject(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static JsonObject error(String response, String code) { JsonObject parsed = parse(response); check(parsed.has("code") && parsed.get("code").getAsString().equals(code), response); return parsed; }
    private static void correlated(String response, String code, String request) {
        JsonObject parsed=error(response,code);
        check(parsed.has("request_id") && request.equals(parsed.get("request_id").getAsString()),"uncorrelated action error: "+response);
    }

    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ProtocolSession session = new ProtocolSession(failure -> failures.incrementAndGet());
        for (char control = 0; control < 32; control++) {
            error(session.receive("{\"type\":\"hello\",\"protocol_version\":2,\"padding\":\"" + control + "\"}"), "INVALID_MESSAGE");
            error(session.receive("{\"type\":\"hello\",\"protocol_version\":2,\"padding" + control + "\":0}"), "INVALID_MESSAGE");
        }
        error(session.receive("{\"type\":\"get_state\",\"padding\":\"\\t\\u0001\\n\\r\"}"), "HANDSHAKE_REQUIRED");
        error(session.receive("{type:'hello',protocol_version:2}"), "INVALID_MESSAGE");
        error(session.receive("{\"type\":\"hello\",\"protocol_version\":1,\"protocol_version\":2}"), "INVALID_MESSAGE");
        String deep = "0";
        for (int i = 0; i < 64; i++) deep = "[" + deep + "]";
        error(session.receive("{\"type\":\"hello\",\"protocol_version\":2,\"padding\":" + deep + "}"), "INVALID_MESSAGE");
        error(session.receive("{\"type\":\"get_state\",\"padding\":" + deep.substring(1, deep.length() - 1) + "}"), "HANDSHAKE_REQUIRED");
        error(session.receive("{\"type\":\"hello\",/*comment*/\"protocol_version\":2}"), "INVALID_MESSAGE");
        error(session.receive("Ready"), "VERSION_MISMATCH");
        error(session.receive("[]"), "INVALID_MESSAGE");
        error(session.receive("{\"type\":\"get_state\"}"), "HANDSHAKE_REQUIRED");
        error(session.receive("{\"type\":\"hello\",\"protocol_version\":1}"), "VERSION_MISMATCH");
        error(session.receive("{\"type\":\"hello\",\"protocol_version\":2.5}"), "VERSION_MISMATCH");
        JsonObject hello = parse(session.receive("{\"type\":\"hello\",\"protocol_version\":2}"));
        String sid = hello.get("session_id").getAsString();
        error(session.receive("{\"type\":\"get_state\"}"), "STATE_UNAVAILABLE");
        ProtocolSession.Action action = new ProtocolSession.Action("pick", "Pick card", new JsonObject(),
            arguments -> { if (!arguments.entrySet().isEmpty()) throw new IllegalArgumentException(); },
            arguments -> calls.incrementAndGet());
        JsonObject observation = new JsonObject();
        observation.addProperty("visible", "original");
        JsonObject state = parse(session.publish(new JsonObject(), observation, Arrays.asList(action), true, "supported"));
        observation.addProperty("visible", "mutated");
        check(parse(session.receive("{\"type\":\"get_state\"}")).getAsJsonObject("observation")
            .get("visible").getAsString().equals("original"), "snapshot alias leaked");
        long rev = state.get("state_id").getAsLong();
        String command = command(sid, rev, "r1", "pick", "{}");
        correlated(session.receive(command("wrong", rev, "foreign", "pick", "{}")), "SESSION_MISMATCH", "foreign");
        correlated(session.receive(command(sid, rev - 1, "old", "pick", "{}")), "STALE_STATE", "old");
        correlated(session.receive(command(sid, rev, "bad", "pick", "{\"extra\":1}")), "INVALID_ARGUMENTS", "bad");
        check(parse(session.receive(command)).get("status").getAsString().equals("applied"), "action not applied");
        correlated(session.receive(command), "DUPLICATE_REQUEST", "r1");
        long invalidatedRev = parse(session.receive("{\"type\":\"get_state\"}")).get("state_id").getAsLong();
        check(invalidatedRev > rev, "invalidation changed snapshot content without a new state ID");
        correlated(session.receive(command(sid, rev, "r2", "pick", "{}")), "STALE_STATE", "r2");
        correlated(session.receive(command(sid, invalidatedRev, "r2", "pick", "{}")), "NOT_READY", "r2");
        check(calls.get() == 1, "action applied more than once");
        check(!parse(session.receive("{\"type\":\"get_state\"}")).get("ready").getAsBoolean(), "stale ready flag");
        JsonObject unsupported = parse(session.publish(new JsonObject(), observation, Arrays.asList(action), true, "unsupported"));
        check(!unsupported.get("ready").getAsBoolean() && unsupported.getAsJsonArray("actions").size() == 0, "unsupported screen actionable");
        ProtocolSession.Action broken = new ProtocolSession.Action("broken", "Broken", new JsonObject(), a -> {}, a -> { calls.incrementAndGet(); throw new IllegalStateException("private-details"); });
        state = parse(session.publish(new JsonObject(), observation, Arrays.asList(broken), true, "supported"));
        String failed = session.receive(command(sid, state.get("state_id").getAsLong(), "failure", "broken", "{}"));
        error(failed, "ACTION_FAILED");
        check(parse(failed).has("request_id") && "failure".equals(parse(failed).get("request_id").getAsString()),"failed result lost request correlation");
        check(!failed.contains("private-details") && failures.get() == 1, "diagnostic leaked or missing");
        correlated(session.receive(command(sid, state.get("state_id").getAsLong(), "failure", "broken", "{}")), "DUPLICATE_REQUEST", "failure");
        session.invalidate();
        check(!parse(session.receive("{\"type\":\"get_state\"}")).get("ready").getAsBoolean(), "invalidate failed");
        error(session.receive("{\"type\":\"hello\",\"protocol_version\":2}"), "ALREADY_CONNECTED");
        try {
            session.publish(new JsonObject(), observation, Arrays.asList(action, action), true, "supported");
            throw new AssertionError("duplicate action IDs accepted");
        } catch (IllegalArgumentException expected) { }
        session.publish(new JsonObject(), observation, Collections.emptyList(), false, "supported");
        check(calls.get() == 2, "invalid actions executed");
        ProtocolSession.Action reentrant = new ProtocolSession.Action("reentrant", "Reentrant", new JsonObject(), a -> {}, a -> {
            session.publish(new JsonObject(), observation, Arrays.asList(action), true, "supported");
            throw new IllegalStateException("Failure after publish");
        });
        state = parse(session.publish(new JsonObject(), observation, Arrays.asList(reentrant), true, "supported"));
        String reentrantFailed=session.receive(command(sid, state.get("state_id").getAsLong(), "reentrant", "reentrant", "{}"));
        error(reentrantFailed, "ACTION_FAILED");check("reentrant".equals(parse(reentrantFailed).get("request_id").getAsString()),"reentrant failure lost request correlation");
        JsonObject afterFailure = parse(session.receive("{\"type\":\"get_state\"}"));
        check(!afterFailure.get("ready").getAsBoolean() && afterFailure.getAsJsonArray("actions").size() == 0, "reentrant failing callback left actions enabled");
        System.out.println("PASS: handshake, immutable snapshots, correlated action errors, stale/duplicate/session checks, unsupported states, failure consumption, local diagnostics");
    }

    private static String command(String session, long state, String request, String action, String arguments) {
        return "{\"type\":\"act\",\"session_id\":\"" + session + "\",\"state_id\":" + state
            + ",\"request_id\":\"" + request + "\",\"action_id\":\"" + action + "\",\"arguments\":" + arguments + "}";
    }
}
