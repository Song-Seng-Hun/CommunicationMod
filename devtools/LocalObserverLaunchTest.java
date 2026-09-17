import java.lang.reflect.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Pure validation of launch manifests, no game classes loaded. */
public final class LocalObserverLaunchTest {
    public static void main(String[] args)throws Exception {
        Class<?> type;
        try{type=Class.forName("LocalObserverLaunch");}catch(ClassNotFoundException e){throw new AssertionError("Missing hash-checked local launcher",e);}
        Method check=type.getMethod("verify",Path.class);
        Path folder=Files.createTempDirectory("observer-launch-test-");
        rejects(check,folder,"missing manifest");
        Properties report=new Properties();report.setProperty("profile","wrong");write(folder,report);
        rejects(check,folder,"wrong profile");
        report.setProperty("profile","local_observer");write(folder,report);
        rejects(check,folder,"missing artifact hashes");
        // Real prepared output is checked separately by --check in the preparation script.
        System.out.println("PASS: local launcher rejects missing/wrong/incomplete manifests without loading game code");
    }
    private static void write(Path folder,Properties report)throws Exception{try(OutputStream out=Files.newOutputStream(folder.resolve("runtime.properties"))){report.store(out,"");}}
    private static void rejects(Method method,Path path,String message)throws Exception{
        try{method.invoke(null,path);throw new AssertionError("Accepted "+message);}
        catch(InvocationTargetException e){if(!(e.getCause() instanceof IOException))throw e;}
    }
}
