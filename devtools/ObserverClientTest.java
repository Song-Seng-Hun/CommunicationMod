import com.google.gson.*;
import communicationmod.protocol.ObserverSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.Set;
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
        cacheFailure(args[0], true);
        cacheFailure(args[0], false);
    }

    private static void cacheFailure(String classpath, boolean lockDestination) throws Exception {
        Path output=Files.createTempDirectory("observer-cache-test-");
        Path latest=output.resolve("latest-state.json"), transcript=output.resolve("observations.jsonl");
        Path errors=output.resolve("errors.log"), temp=output.resolve("latest-state.json.tmp");
        Process child=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java.exe").toString(),
            "-Xmx64m","-cp",classpath,"communicationmod.devclient.ObservationClient",output.toString())
            .redirectError(errors.toFile()).start();
        SeekableByteChannel lock=null;
        try {
            BufferedReader from=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter to=new BufferedWriter(new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8));
            await(() -> from.ready() || !child.isAlive(), "client greeting");
            if(from.readLine()==null)throw new AssertionError("No greeting");
            send(to, "{\"type\":\"state\",\"state_id\":1}");
            await(() -> snapshotIs(latest, 1), "initial snapshot");
            if(lockDestination) {
                // Windows sharing violation, not an advisory FileLock or a mocked move.
                Set<OpenOption> options=new HashSet<>();options.add(StandardOpenOption.READ);
                options.add((OpenOption)Class.forName("com.sun.nio.file.ExtendedOpenOption").getField("NOSHARE_DELETE").get(null));
                lock=Files.newByteChannel(latest,options);
            } else {
                // Also exercise a failed temporary write, before the move is attempted.
                Files.createDirectory(temp);
            }
            for(int id=2;id<=41;id++)send(to,"{\"type\":\"state\",\"state_id\":"+id+"}");
            // A non-state marker proves all earlier snapshot attempts were processed.
            send(to,"{\"type\":\"marker\"}");
            await(() -> !child.isAlive() || lines(transcript)==42, "recording during cache failure");
            if(!child.isAlive())throw new AssertionError("Cache failure killed client: "+new String(Files.readAllBytes(errors),StandardCharsets.UTF_8));
            if(!snapshotIs(latest,1))throw new AssertionError("Failed update damaged previous snapshot");
            if(lines(errors)!=1)throw new AssertionError("Repeated cache failures must produce one warning");
            if(lock!=null){lock.close();lock=null;}else Files.delete(temp);
            send(to,"{\"type\":\"state\",\"state_id\":42}");
            await(() -> snapshotIs(latest,42), "cache recovery with newest state");
            to.close();
            if(!child.waitFor(8,TimeUnit.SECONDS) || child.exitValue()!=0)throw new AssertionError("Client failed at EOF");
            if(lines(transcript)!=43)throw new AssertionError("Transcript lost observations");
            if(lines(errors)!=2)throw new AssertionError("Expected one failure and one recovery diagnostic");
            if(from.readLine()!=null)throw new AssertionError("Diagnostics leaked to protocol stdout");
            System.out.println("PASS: "+(lockDestination?"Windows no-delete lock":"temporary write failure")+"; 40 failures preserve JSONL, bounded diagnostics, newest-state recovery");
        } finally {if(lock!=null)lock.close();if(child.isAlive())child.destroyForcibly();}
    }
    private interface Check {boolean ready() throws Exception;}
    private static void await(Check condition,String description)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.ready()){if(System.nanoTime()>deadline)throw new AssertionError("Timed out: "+description);Thread.sleep(20);}
    }
    private static void send(BufferedWriter to,String message)throws IOException {to.write(message);to.newLine();to.flush();}
    private static int lines(Path path)throws IOException {
        return Files.exists(path)?Files.readAllLines(path,StandardCharsets.UTF_8).size():0;
    }
    private static boolean snapshotIs(Path path,int id) {
        try{return new JsonParser().parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)).getAsJsonObject().get("state_id").getAsInt()==id;}
        catch(IOException | RuntimeException unavailable){return false;}
    }
}
