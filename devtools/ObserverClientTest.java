import com.google.gson.*;
import communicationmod.protocol.ObserverSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public final class ObserverClientTest {
    public static void main(String[] args) throws Exception {
        Path output=Files.createTempDirectory("observer-client-test-");
        Process child=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java.exe").toString(),
            "-Xmx64m","-cp",args[0],"communicationmod.devclient.ObservationClient",output.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            BufferedReader from=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter to=new BufferedWriter(new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8));
            ObserverSession session=new ObserverSession();
            String line=from.readLine();
            if(line==null)throw new AssertionError("Recording client did not start/handshake");
            String hello=session.receive(line);
            if(!hello.contains("\"type\":\"hello\""))throw new AssertionError("Client sent non-v2 greeting");
            to.write(hello);to.newLine();
            JsonObject view=new JsonObject();view.addProperty("text","가운데 카드: 방어도를 5 얻습니다.");
            String state=session.publish(new JsonObject(),view);
            to.write(state);to.newLine();to.flush();to.close();
            if(!child.waitFor(8,TimeUnit.SECONDS))throw new AssertionError("Client did not exit on EOF");
            if(child.exitValue()!=0)throw new AssertionError("Client failed");
            if(from.readLine()!=null)throw new AssertionError("Passive client emitted commands after hello");
            String latest=new String(Files.readAllBytes(output.resolve("latest-state.json")),StandardCharsets.UTF_8);
            if(!new JsonParser().parse(latest).equals(new JsonParser().parse(state)))throw new AssertionError("Latest snapshot mismatch");
            if(Files.readAllLines(output.resolve("observations.jsonl"),StandardCharsets.UTF_8).size()!=2)throw new AssertionError("Transcript not complete");
            System.out.println("PASS: real subprocess UTF-8 v2 greeting/state/log/EOF; no act or legacy commands");
        } finally {if(child.isAlive())child.destroyForcibly();}
    }
}
