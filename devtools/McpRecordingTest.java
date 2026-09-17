import java.io.*;import java.lang.reflect.*;
public final class McpRecordingTest {
 public static void main(String[] args)throws Exception {
  Class<?> c;try{c=Class.forName("communicationmod.devclient.McpRecording");}catch(ClassNotFoundException e){throw new AssertionError("Missing non-fatal bounded recording",e);}
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();Object log=c.getConstructor(OutputStream.class,long.class).newInstance(bytes,20L);Method append=c.getMethod("append",String.class,String.class);
  append.invoke(log,"hello","a");append.invoke(log,"hello","a");if(bytes.size()!=6)throw new AssertionError("Duplicate state persisted");
  append.invoke(log,"12345678901234567890","b");append.invoke(log,"later","c");if(bytes.size()!=6)throw new AssertionError("Cap not bounded");
  OutputStream broken=new OutputStream(){public void write(int b)throws IOException{throw new IOException("fixture");}};
  Object failed=c.getConstructor(OutputStream.class,long.class).newInstance(broken,20L);append.invoke(failed,"record","x");append.invoke(failed,"next","y");
  System.out.println("PASS: duplicate suppression, hard cap and non-fatal disk failure");
 }
}
