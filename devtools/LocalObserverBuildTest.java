import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javassist.*;
import javassist.expr.*;

/** Builds/inspects bytecode without invoking any game entry point. */
public final class LocalObserverBuildTest {
    public static void main(String[] args)throws Exception {
        Class<?> builder;
        try {builder=Class.forName("BuildLocalObserver");}
        catch(ClassNotFoundException e){throw new AssertionError("Missing non-VM observer runtime builder",e);}
        builder.getMethod("main",String[].class).invoke(null,(Object)args);
        Path out=Paths.get(args[1]);
        ClassPool pool=new ClassPool(true);
        pool.insertClassPath(out.resolve("desktop-1.0-modded.jar").toString());
        CtClass launcher=pool.get("com.evacipated.cardcrawl.modthespire.PackageJar$PrepackagedLauncher");
        List<String> calls=calls(launcher.getDeclaredMethod("main"));
        expect(count(calls,"bustEnums")==1 && count(calls,"callInitializers")==1 && count(calls,"main")==1,"original bootstrap retained once");
        expect(!calls.toString().contains("DevGameLauncher"),"no hand-recreated bootstrap");
        calls=calls(pool.get("com.megacrit.cardcrawl.core.CardCrawlGame").getDeclaredMethod("render"));
        int observer=find(calls,"LocalObserver.tick");
        expect(observer>find(calls,"CombatObservation.completeFrame") && observer>find(calls,"DialogueObservation.completeFrame"),"capture after completed frames");
        expect(observer>=0,"live observer tick installed");
        CtClass bridge=pool.get("communicationmod.observation.LocalObserver");
        for(CtBehavior method:bridge.getDeclaredBehaviors()) {
            List<String> bound=calls(method);
            for(String call:bound)expect(!call.endsWith("CommandExecutor.executeCommand") && !call.contains("CombatActions.") && !call.endsWith("CommunicationMod.initialize"),"observer must not bind legacy or mutation entry points");
        }
        expect(calls(bridge.getDeclaredMethod("tick")).toString().contains("ObserverSession.receive"),"v2 receive wired on frame thread");
        expect(!calls(bridge.getDeclaredMethod("tick")).toString().contains(".waitFor"),"no handshake/process wait on frame thread");
        calls=calls(pool.get("com.megacrit.cardcrawl.actions.common.EnableEndTurnButtonAction").getDeclaredMethod("update"));
        expect(find(calls,"GameStateListener.signalTurnStart")>=0,"real turn start bound");
        calls=calls(pool.get("com.megacrit.cardcrawl.map.MapRoomNode").getDeclaredMethod("update"));
        expect(find(calls,"MapRoomNodeHoverPatch.Insert")>=0 && find(calls,"java.lang.Boolean.getBoolean")>=0,"map selection hook explicitly gated to local play control");
        CtClass steam=pool.get("com.megacrit.cardcrawl.integrations.steam.SteamIntegration");
        for(CtBehavior method:steam.getDeclaredBehaviors())expect(!calls(method).toString().contains("steamworks"),"test integration cannot submit Steam records");
        for(String name:new String[]{"Metrics","BotDataUploader"}) {
            CtClass type=pool.get("com.megacrit.cardcrawl.metrics."+name);
            for(CtMethod method:type.getDeclaredMethods())if(method.getName().equals("sendPost"))expect(calls(method).isEmpty(),"metrics sender neutralized");
        }
        expect(Files.exists(out.resolve("runtime.properties")),"hash manifest written");
        try {builder.getMethod("main",String[].class).invoke(null,(Object)args);throw new AssertionError("Overwrote existing prepared runtime");}
        catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof java.io.IOException))throw e;}
        System.out.println("PASS: fresh copied runtime, original bootstrap, frame order, turn hook, no submissions, no overwrite");
    }
    private static List<String> calls(CtBehavior method)throws Exception{
        List<String> calls=new ArrayList<>();
        method.instrument(new ExprEditor(){public void edit(MethodCall call){calls.add(call.getClassName()+"."+call.getMethodName());}});
        return calls;
    }
    private static int count(List<String> calls,String name){int n=0;for(String call:calls)if(call.endsWith("."+name))n++;return n;}
    private static int find(List<String> calls,String text){for(int i=0;i<calls.size();i++)if(calls.get(i).endsWith(text))return i;return -1;}
    private static void expect(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
}
