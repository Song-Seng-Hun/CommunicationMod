import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public final class MenuInboxTest {
    public static void main(String[] args)throws Exception {
        Path output=Files.createTempDirectory("menu-inbox-test-");
        Process child=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java.exe").toString(),"-Xmx64m","-cp",args[0],
            "communicationmod.devclient.ObservationClient",output.toString(),"--menu-control").redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            BufferedReader from=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter to=new BufferedWriter(new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8));
            await(()->from.ready() || !child.isAlive());
            String hello=from.readLine();check(hello!=null && hello.contains("hello"),"menu client must start and greet");
            String command="{\"type\":\"act\",\"session_id\":\"session\",\"state_id\":4,\"request_id\":\"menu-one\",\"action_id\":\"menu.play\",\"arguments\":{}}";
            Files.write(output.resolve("menu-request.json"),command.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
            await(()->from.ready() || !child.isAlive());check(command.equals(from.readLine()),"forward exactly one request");
            String reply="{\"type\":\"result\",\"request_id\":\"menu-one\",\"status\":\"applied\"}";
            to.write(reply);to.newLine();to.flush();
            await(()->Files.exists(output.resolve("menu-response.json")));
            JsonObject response=new JsonParser().parse(new String(Files.readAllBytes(output.resolve("menu-response.json")),StandardCharsets.UTF_8)).getAsJsonObject();
            check(response.get("request_id").getAsString().equals("menu-one"),"correlated local reply");
            check(response.getAsJsonObject("response").get("status").getAsString().equals("applied"),"game reply preserved");
            check(!Files.exists(output.resolve("menu-request.json")),"request consumed");
            to.close();check(child.waitFor(8,TimeUnit.SECONDS) && child.exitValue()==0,"EOF stops client");
            check(from.readLine()==null,"no repeat commands");
            System.out.println("PASS: explicit menu client real subprocess forwarding/reply/consume/no-replay/EOF");
        } finally {if(child.isAlive())child.destroyForcibly();}
    }
    private interface Check {boolean ready()throws Exception;}
    private static void await(Check c)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!c.ready()){if(System.nanoTime()>end)throw new AssertionError("Timed out awaiting menu transport");Thread.sleep(20);}}
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
}
