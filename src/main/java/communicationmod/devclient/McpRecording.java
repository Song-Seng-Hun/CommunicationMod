package communicationmod.devclient;
import java.io.*;import java.nio.charset.StandardCharsets;
/** Evidence is bounded and best-effort; transport must not depend on disk health. */
public final class McpRecording implements AutoCloseable {
 private final OutputStream stream;private final long limit;private long bytes;private boolean stopped;private String previous="";
 public McpRecording(OutputStream stream,long limit){this.stream=stream;this.limit=limit;}
 public void append(String line,String stateKey){
  if(stopped || !stateKey.isEmpty()&&stateKey.equals(previous))return;
  if(!stateKey.isEmpty())previous=stateKey;
  byte[] record=(line+"\n").getBytes(StandardCharsets.UTF_8);
  if(bytes+record.length>limit){stop("size limit");return;}
  try{stream.write(record);stream.flush();bytes+=record.length;}catch(IOException e){stop("disk unavailable");}
 }
 private void stop(String reason){stopped=true;System.err.println("MCP disk recording stopped ("+reason+"); live transport continues.");}
 public void close(){try{stream.close();}catch(IOException ignored){}}
}
