package communicationmod.observation;

import communicationmod.*;
import communicationmod.protocol.ObserverSession;
import com.google.gson.*;
import com.megacrit.cardcrawl.core.Settings;
import java.io.File;
import java.util.concurrent.*;

/** Called only after a successfully completed render. Human controls remain unchanged. */
public final class LocalObserver {
    private static final ObserverSession SESSION=new ObserverSession();
    private static final BlockingQueue<String> INPUT=new ArrayBlockingQueue<>(16), OUTPUT=new ArrayBlockingQueue<>(16);
    private static Process client;
    private static Thread reader,writer;
    private static boolean attempted,closed,reported;
    private static long nextCapture,helloDeadline;
    private static String previous;

    public static void tick() {
        if(closed || !Boolean.getBoolean("communicationmod.observer"))return;
        try {
            if(!attempted)start();
            if(client==null || !client.isAlive() || !reader.isAlive() || !writer.isAlive()) {
                close();return;
            }
            for(int i=0;i<4;i++) {
                String line=INPUT.poll();if(line==null)break;
                send(SESSION.receive(line));
            }
            if(!SESSION.connected()) {
                if(System.nanoTime()>helloDeadline)throw new IllegalStateException("Observer v2 handshake timed out");
                return;
            }
            long now=System.nanoTime();
            // Capture at most four times per second, plus readiness transitions.
            String decision=CombatObservation.observation().toString();
            if(now<nextCapture && decision.equals(previous))return;
            nextCapture=now+250_000_000L;previous=decision;
            JsonObject runtime=new JsonObject();
            runtime.addProperty("environment","downfall_standalone");
            runtime.addProperty("profile","local_observer");
            runtime.addProperty("language",Settings.language.name());
            runtime.addProperty("build",System.getProperty("communicationmod.build","unknown"));
            runtime.addProperty("control","human");
            runtime.addProperty("test_submissions","disabled_in_test_copy");
            JsonObject view;
            try {view=new JsonParser().parse(GameStateConverter.getCommunicationState()).getAsJsonObject();}
            catch(RuntimeException | LinkageError failure) {
                view=new JsonObject();view.addProperty("status","observation_unavailable");
                view.addProperty("reason","screen_capture_failed");
                report(failure);
            }
            send(SESSION.publish(runtime,view));
        } catch(RuntimeException | LinkageError failure) {report(failure);close();}
    }

    private static void start() {
        attempted=true;
        String jar=System.getProperty("communicationmod.client.jar"), logs=System.getProperty("communicationmod.recording");
        if(jar==null || logs==null)throw new IllegalStateException("Observer launcher settings missing");
        try {
            File directory=new File(logs);
            if(!directory.isDirectory())throw new IllegalStateException("Recording directory missing");
            ProcessBuilder builder=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java.exe").toString(),
                "-Xmx64m","-Dfile.encoding=UTF-8","-cp",jar,"communicationmod.devclient.ObservationClient",logs);
            builder.redirectError(ProcessBuilder.Redirect.appendTo(new File(directory,"client-errors.log")));
            client=builder.start();
            reader=new Thread(new DataReader(INPUT,client.getInputStream(),false),"comm-observer-reader");
            writer=new Thread(new DataWriter(OUTPUT,client.getOutputStream(),false),"comm-observer-writer");
            reader.setDaemon(true);writer.setDaemon(true);reader.start();writer.start();
            helloDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            Runtime.getRuntime().addShutdownHook(new Thread(LocalObserver::close,"comm-observer-shutdown"));
            System.out.println("[COMM-OBSERVER] Passive v2 client started; controls remain with the player. Records: "+logs);
        }catch(java.io.IOException e){throw new IllegalStateException("Unable to start passive client",e);}
    }
    private static void send(String line) {
        if(!OUTPUT.offer(line))throw new IllegalStateException("Observer output stalled; recording stopped");
    }
    private static void report(Throwable failure) {
        if(!reported){reported=true;System.err.println("[COMM-OBSERVER] Observation failure (repeats suppressed); human play continues.");failure.printStackTrace(System.err);}
    }
    public static synchronized void close() {
        closed=true;
        if(reader!=null)reader.interrupt();
        if(writer!=null)writer.interrupt();
        if(client!=null)client.destroy();
    }
}
