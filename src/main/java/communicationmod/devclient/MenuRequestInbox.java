package communicationmod.devclient;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Local adapter only: one explicitly written request at a time, never automatic actions/retries. */
final class MenuRequestInbox implements AutoCloseable {
    private final Path output;
    private final Thread worker;
    private volatile boolean running=true;
    private String pending;
    private JsonObject response;
    private int replyFailures;
    private final boolean playControl;

    MenuRequestInbox(Path output,boolean playControl) {
        this.output=output;
        this.playControl=playControl;
        worker=new Thread(this::run,"comm-menu-inbox");worker.setDaemon(true);worker.start();
    }
    synchronized void reply(JsonObject message) {
        String type=message.get("type").getAsString();
        if(pending!=null && (type.equals("result") || type.equals("error"))) {
            response=new JsonObject();response.addProperty("request_id",pending);response.add("response",message);
        }
    }
    private void run() {
        try {
            while(running){poll();Thread.sleep(100);}
        } catch(InterruptedException stopped){Thread.currentThread().interrupt();}
        catch(IOException | RuntimeException failure){System.err.println("Menu inbox stopped; no request will be retried automatically: "+failure);}
    }
    private synchronized void poll()throws IOException {
        if(response!=null) {
            try {
                Path temp=output.resolve("menu-response.json.tmp");
                Files.write(temp,response.toString().getBytes(StandardCharsets.UTF_8));
                try{Files.move(temp,output.resolve("menu-response.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,output.resolve("menu-response.json"),StandardCopyOption.REPLACE_EXISTING);}
                pending=null;response=null;replyFailures=0;
            } catch(IOException locked) {
                if(++replyFailures==1)System.err.println("Menu reply cache unavailable; original response remains in observations.jsonl: "+locked);
                if(replyFailures>=100)throw new IOException("Reply cache still blocked; refusing further actions",locked);
            }
        }
        if(pending!=null)return;
        Path request=output.resolve("menu-request.json"), claimed=output.resolve("menu-request.processing.json");
        if(!Files.exists(request))return;
        // No REPLACE_EXISTING: an ambiguous old claimed file is never replayed or overwritten.
        Files.move(request,claimed);
        if(Files.size(claimed)>16384)throw new IOException("Oversized menu request");
        String line=new String(Files.readAllBytes(claimed),StandardCharsets.UTF_8);
        if(line.contains("\n") || line.contains("\r"))throw new IOException("One JSON line required");
        JsonObject command=new JsonParser().parse(line).getAsJsonObject();
        String action=command.get("action_id").getAsString();
        if(!"act".equals(command.get("type").getAsString()) || !(action.startsWith("menu.") || playControl && (action.startsWith("run.") || action.equals("acknowledge_event_reading"))))
            throw new IOException("Only explicit menu actions may enter this adapter");
        String id=command.get("request_id").getAsString();
        if(id.isEmpty() || id.length()>128)throw new IOException("Invalid request ID");
        Files.delete(claimed); // Consume before sending; a broken pipe cannot replay an action.
        pending=id;
        // Preserve original bytes/text semantics for the server's stricter JSON validator.
        System.out.println(line);System.out.flush();
        if(System.out.checkError())throw new IOException("Protocol output closed");
    }
    public void close(){running=false;worker.interrupt();}
}
