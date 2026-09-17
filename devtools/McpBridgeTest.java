import java.nio.file.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import com.google.gson.*;
public final class McpBridgeTest {
 public static void main(String[] args)throws Exception {
  Class<?> c;try{c=Class.forName("communicationmod.devclient.McpBridge");}catch(ClassNotFoundException e){throw new AssertionError("Missing persistent MCP bridge",e);}
  Path dir=Files.createTempDirectory("comm-mcp-test-");List<String> sent=Collections.synchronizedList(new ArrayList<>());
  AutoCloseable bridge=(AutoCloseable)c.getConstructor(Path.class,java.util.function.Consumer.class).newInstance(dir,(java.util.function.Consumer<String>)sent::add);
  try{
   JsonObject endpoint=new JsonParser().parse(new String(Files.readAllBytes(dir.resolve("mcp-bridge.json")),StandardCharsets.UTF_8)).getAsJsonObject();
   try(Socket wrong=new Socket("127.0.0.1",endpoint.get("port").getAsInt())){wrong.setSoTimeout(2000);PrintWriter w=new PrintWriter(wrong.getOutputStream(),true);w.println("{\"token\":\"wrong\"}");if(wrong.getInputStream().read()!=-1)throw new AssertionError("Bad token accepted");}
   try(Socket socket=new Socket("127.0.0.1",endpoint.get("port").getAsInt())) {
    socket.setSoTimeout(3000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));PrintWriter out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
    JsonObject auth=new JsonObject();auth.addProperty("token",endpoint.get("token").getAsString());out.println(auth);
    if(!in.readLine().contains("bridge_ready"))throw new AssertionError("No handshake");
    JsonObject s=new JsonParser().parse("{\"type\":\"state\",\"state_id\":1,\"session_id\":\"s\",\"ready\":true}").getAsJsonObject();c.getMethod("publish",JsonObject.class).invoke(bridge,s);
    if(!in.readLine().contains("state_id"))throw new AssertionError("No live state");
    out.println("{\"type\":\"act\",\"session_id\":\"s\",\"state_id\":1,\"request_id\":\"r\",\"action_id\":\"run.x\",\"arguments\":{}}");
    long until=System.currentTimeMillis()+2000;while(sent.isEmpty()&&System.currentTimeMillis()<until)Thread.sleep(5);
    if(sent.size()!=1)throw new AssertionError("No forwarded action");
    out.println("{\"type\":\"act\",\"request_id\":\"other\",\"action_id\":\"run.x\"}");
    if(!in.readLine().contains("bridge_error"))throw new AssertionError("Concurrent mutation accepted");
    JsonObject result=new JsonParser().parse("{\"type\":\"result\",\"request_id\":\"r\",\"status\":\"applied\"}").getAsJsonObject();c.getMethod("publish",JsonObject.class).invoke(bridge,result);
    if(!in.readLine().contains("applied"))throw new AssertionError("Receipt lost");
    if(sent.size()!=1)throw new AssertionError("Unexpected dispatch");
   }
  }finally{bridge.close();}
  Class<?> peerClass=Class.forName("communicationmod.devclient.McpBridge$Peer");
  java.lang.reflect.Constructor<?> peerConstructor=peerClass.getDeclaredConstructor(Socket.class);peerConstructor.setAccessible(true);
  try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket client=new Socket("127.0.0.1",listener.getLocalPort());Socket server=listener.accept()) {
   Object peer=peerConstructor.newInstance(server);java.lang.reflect.Method offer=peerClass.getDeclaredMethod("offer",String.class);offer.setAccessible(true);
   char[] huge=new char[2*1024*1024+1];Arrays.fill(huge,'x');offer.invoke(peer,new String(huge));
   if(!server.isClosed())throw new AssertionError("Slow-peer queue has no memory budget");
  }
  System.out.println("PASS: loopback authentication, live state, single-flight action and receipt");
 }
}
