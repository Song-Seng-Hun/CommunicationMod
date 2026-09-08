package communicationmod.devclient;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Authenticated local stream. Never blocks the game/observation reader on a slow MCP client. */
public final class McpBridge implements AutoCloseable {
    private final ServerSocket server;
    private final String token;
    private final Consumer<String> command;
    private final LinkedHashMap<String,JsonObject> receipts=new LinkedHashMap<>();
    private volatile Peer peer;
    private volatile boolean closed;
    private JsonObject latest;
    private long latestAt;
    private String pending;
    public McpBridge(Path directory,Consumer<String> command)throws IOException {
        this.command=command;
        byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        server=new ServerSocket();server.bind(new InetSocketAddress("127.0.0.1",0),1);
        JsonObject endpoint=new JsonObject();endpoint.addProperty("protocol",1);endpoint.addProperty("port",server.getLocalPort());endpoint.addProperty("token",token);
        try{Files.write(directory.resolve("mcp-bridge.json"),endpoint.toString().getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);}
        catch(IOException e){server.close();throw e;}
        Thread accept=new Thread(this::accept,"comm-mcp-accept");accept.setDaemon(true);accept.start();
    }
    public synchronized void publish(JsonObject message) {
        String type=string(message,"type");
        if(type.equals("state")){latest=message;latestAt=System.nanoTime();}
        if(type.equals("result") || type.equals("error")) {
            String id=string(message,"request_id");
            if(!id.isEmpty()) {receipts.put(id,message);while(receipts.size()>128)receipts.remove(receipts.keySet().iterator().next());if(id.equals(pending))pending=null;}
        }
        Peer current=peer;if(current!=null)current.offer(message.toString());
    }
    private void accept() {
        while(!closed) {
            try(Socket socket=server.accept()) {
                socket.setTcpNoDelay(true);socket.setSoTimeout(2000);
                BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                String line=readLine(input,1024);if(line==null)continue;
                JsonObject auth=new JsonParser().parse(line).getAsJsonObject();
                if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),string(auth,"token").getBytes(StandardCharsets.UTF_8)))continue;
                socket.setSoTimeout(0);Peer active=new Peer(socket);
                synchronized(this) {
                    peer=active;
                    JsonObject ready=new JsonObject();ready.addProperty("type","bridge_ready");ready.addProperty("pending_request_id",pending);active.offer(ready.toString());
                    for(JsonObject receipt:receipts.values())active.offer(receipt.toString());
                    if(latest!=null){JsonObject snapshot=new JsonParser().parse(latest.toString()).getAsJsonObject();snapshot.addProperty("bridge_state_age_ms",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-latestAt));active.offer(snapshot.toString());}
                }
                while((line=readLine(input,16384))!=null)dispatch(line,active);
            } catch(IOException | RuntimeException ignored) { /* no commands are retried */ }
            finally {synchronized(this){if(peer!=null){peer.close();peer=null;}}}
        }
    }
    private synchronized void dispatch(String line,Peer active) {
        JsonObject request=new JsonParser().parse(line).getAsJsonObject();String id=string(request,"request_id"),action=string(request,"action_id");
        if(!"act".equals(string(request,"type")) || id.isEmpty() || id.length()>128
            || !(action.startsWith("run.") || action.startsWith("menu.") || action.equals("acknowledge_event_reading"))) {reject(active,id,"invalid_request");return;}
        if(receipts.containsKey(id)){active.offer(receipts.get(id).toString());return;}
        if(pending!=null){reject(active,id,"busy_outcome_pending");return;}
        if(latest==null || System.nanoTime()-latestAt>TimeUnit.SECONDS.toNanos(5)){reject(active,id,"stale_connection");return;}
        pending=id;command.accept(line); // consume before forwarding; original v2 validates session/state/arguments
    }
    private static void reject(Peer peer,String id,String code){JsonObject e=new JsonObject();e.addProperty("type","bridge_error");e.addProperty("request_id",id);e.addProperty("code",code);peer.offer(e.toString());}
    private static String string(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static String readLine(Reader input,int limit)throws IOException {
        StringBuilder line=new StringBuilder();int c;while((c=input.read())!=-1){if(c=='\n'){if(line.length()>0&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return line.toString();}if(line.length()>=limit || line.length()>0&&line.charAt(line.length()-1)=='\r')throw new IOException("Invalid line");line.append((char)c);}return line.length()==0?null:line.toString();
    }
    private static final class Peer {
        final Socket socket;final BlockingQueue<String> output=new ArrayBlockingQueue<>(160);final Thread writer;
        final AtomicInteger queuedChars=new AtomicInteger(); // <= 4 MiB UTF-16, including in-flight write
        Peer(Socket socket) {this.socket=socket;writer=new Thread(()->{
            try{Writer stream=new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8);while(!socket.isClosed()){String line=output.take();try{stream.write(line);stream.write('\n');stream.flush();}finally{queuedChars.addAndGet(-line.length());}}}
            catch(IOException | InterruptedException e){close();}
        },"comm-mcp-writer");writer.setDaemon(true);writer.start();}
        void offer(String line){if(socket.isClosed())return;if(queuedChars.addAndGet(line.length())>2*1024*1024 || !output.offer(line)){queuedChars.addAndGet(-line.length());close();}}
        void close(){try{socket.close();}catch(IOException ignored){}writer.interrupt();}
    }
    public synchronized void close(){closed=true;try{server.close();}catch(IOException ignored){}if(peer!=null)peer.close();}
}
