import communicationmod.protocol.ProtocolSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Transport fixture, not a game launcher or an AI. No available actions. */
public final class ProtocolFixtureServer {
    public static void main(String[] args) throws Exception {
        ProtocolSession session = new ProtocolSession(failure -> failure.printStackTrace(System.err));
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = input.readLine()) != null) {
            String response = session.receive(line);
            output.println(response);
            if (new JsonParser().parse(response).getAsJsonObject().get("type").getAsString().equals("hello")) {
                JsonObject runtime = new JsonObject();
                runtime.addProperty("kind", "fixture");
                runtime.addProperty("online_submission", "not_applicable");
                output.println(session.publish(runtime, new JsonObject(), Collections.emptyList(), false, "unsupported"));
            }
        }
    }
}
