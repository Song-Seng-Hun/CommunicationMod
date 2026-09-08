import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import javassist.*;
import communicationmod.patches.*;

/** Copies a pinned standalone build, adding observation hooks only; never runs game code. */
public final class BuildLocalObserver {
    private static final String[] JARS={"desktop-1.0-modded.jar","package/BaseMod-modded.jar","package/StSLib-modded.jar","package/EvilWithin-modded.jar"};
    private static final String[] HASHES={
        "abb42ebdc5d3ea66b30d6a1993d80880ab45b21a1de01f8ce43945bf31b9fee8",
        "ba44fdfdccd6b0949a862e4555daf7f91cd5ebb71f24559bff420d99166a2056",
        "6dfb351694567d925282bf3a1895905e35fce4cc4ec619b0362d29220bd366bd",
        "148c422dd26e869fbdc9a63f9ab072373c3d791d3e3fcbe8d87b55ae75a29226"};
    private static final String BOOT="com.evacipated.cardcrawl.modthespire.PackageJar$PrepackagedLauncher";

    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Installed directory, NEW output directory, built CommunicationMod.jar required");
        Path source=Paths.get(args[0]).toRealPath(), output=Paths.get(args[1]).toAbsolutePath().normalize(), mod=Paths.get(args[2]).toRealPath();
        if(Files.exists(output))throw new IOException("Refusing existing output: "+output);
        if(output.getParent().toRealPath().startsWith(source))throw new IOException("Output must be outside Steam install");
        for(int i=0;i<JARS.length;i++)if(!hash(source.resolve(JARS[i])).equals(HASHES[i]))throw new IOException("Installed version changed: "+JARS[i]);
        Files.createDirectory(output);Files.createDirectory(output.resolve("package"));
        ClassPool pool=new ClassPool(true);
        for(String jar:JARS)pool.insertClassPath(source.resolve(jar).toString());
        pool.insertClassPath(mod.toString());
        Map<String,byte[]> replacements=patch(pool);
        Properties report=new Properties();report.setProperty("profile","local_observer");
        report.setProperty("source",source.toString());
        for(int i=0;i<JARS.length;i++) {
            Path destination=output.resolve(JARS[i]);
            if(i==0)copyJar(source.resolve(JARS[i]),destination,replacements);
            else Files.copy(source.resolve(JARS[i]),destination);
            if(!hash(source.resolve(JARS[i])).equals(HASHES[i]))throw new IOException("Source changed during copy");
            report.setProperty("input."+JARS[i],HASHES[i]);report.setProperty("output."+JARS[i],hash(destination));
        }
        Files.copy(mod,output.resolve("CommunicationMod.jar"));
        report.setProperty("output.CommunicationMod.jar",hash(output.resolve("CommunicationMod.jar")));
        report.setProperty("patched.classes",String.join(",",replacements.keySet()));
        try(OutputStream out=Files.newOutputStream(output.resolve("runtime.properties"),StandardOpenOption.CREATE_NEW)){report.store(out,"Local observer only; gameplay not yet validated");}
        System.out.println("PREPARED: "+output+" ("+replacements.size()+" classes; original Steam files unchanged)");
    }

    private static Map<String,byte[]> patch(ClassPool pool)throws Exception {
        Set<CtClass> changed=new LinkedHashSet<>();
        CtClass game=take(pool,changed,"com.megacrit.cardcrawl.core.CardCrawlGame");
        // SteamInputHelper already skips itself when SteamAPI.isSteamRunning() is
        // false, but create() allocates SteamUtils unconditionally just before it.
        // Its disposal path is null-checked. Do not initialize that native callback
        // in this mouse/keyboard-only test profile.
        game.getDeclaredMethod("create").instrument(new javassist.expr.ExprEditor(){
            public void edit(javassist.expr.NewExpr allocation)throws CannotCompileException {
                if(allocation.getClassName().equals("com.codedisaster.steamworks.SteamUtils"))
                    allocation.replace("{ $_ = null; }");
            }
        });
        CtMethod render=game.getDeclaredMethod("render");
        take(pool,changed,"com.megacrit.cardcrawl.map.MapRoomNode").getDeclaredMethod("update").instrument(new javassist.expr.ExprEditor(){
            public void edit(javassist.expr.MethodCall call)throws CannotCompileException {
                if(call.getClassName().equals("com.megacrit.cardcrawl.helpers.Hitbox") && call.getMethodName().equals("update"))
                    call.replace("{$proceed($$);if(java.lang.Boolean.getBoolean(\"communicationmod.play_control\")){communicationmod.patches.MapRoomNodeHoverPatch.Insert(this);}}");
            }
        });
        DialogueRenderPatch.Frame.Raw(render);CombatReadinessPatch.Frame.Raw(render);
        render.insertAfter("{ communicationmod.observation.LocalObserver.tick(); }");
        game.getDeclaredMethod("dispose").insertBefore("{ communicationmod.observation.LocalObserver.close(); }");
        CtMethod reset=game.getDeclaredMethod("startOver",new CtClass[0]);
        DialogueRenderPatch.Reset.Raw(reset);reset.insertBefore("{communicationmod.GameStateListener.resetStateVariables();}");
        raw(pool,changed,"Talk","com.megacrit.cardcrawl.actions.animations.TalkAction","update");
        raw(pool,changed,"Neow","com.megacrit.cardcrawl.neow.NeowEvent","talk");
        raw(pool,changed,"Merchant","com.megacrit.cardcrawl.shop.ShopScreen","createSpeech");
        raw(pool,changed,"SpeechRender","com.megacrit.cardcrawl.vfx.SpeechTextEffect","render");
        CtClass speech=take(pool,changed,"com.megacrit.cardcrawl.vfx.SpeechTextEffect");
        DialogueRenderPatch.SpeechCreated.Raw(speech.getDeclaredConstructor(new CtClass[]{CtClass.floatType,CtClass.floatType,CtClass.floatType,pool.get("java.lang.String"),pool.get("com.megacrit.cardcrawl.ui.DialogWord$AppearEffect")}));
        for(String kind:new String[]{"Generic","Room"}) {
            String owner="com.megacrit.cardcrawl.events."+kind+"EventDialog";
            raw(pool,changed,kind+"Render",owner,"render");
            CtMethod body=take(pool,changed,owner).getDeclaredMethod("updateBodyText",new CtClass[]{pool.get("java.lang.String"),pool.get("com.megacrit.cardcrawl.ui.DialogWord$AppearEffect")});
            hook(kind+"Body",body);
        }
        for(String kind:new String[]{"Speech","Dialog"})for(CtMethod method:take(pool,changed,"com.megacrit.cardcrawl.ui."+kind+"Word").getDeclaredMethods("render"))hook(kind+"Word",method);
        KeywordNullGuardPatch.Raw(take(pool,changed,"com.megacrit.cardcrawl.helpers.TipHelper").getDeclaredMethod("renderKeywords"));
        take(pool,changed,"com.megacrit.cardcrawl.actions.common.EnableEndTurnButtonAction").getDeclaredMethod("update").insertAfter("{communicationmod.GameStateListener.signalTurnStart();}");
        take(pool,changed,"com.megacrit.cardcrawl.rooms.AbstractRoom").getDeclaredMethod("endTurn").insertBefore("{communicationmod.GameStateListener.signalTurnEnd();}");
        CtClass manager=take(pool,changed,"com.megacrit.cardcrawl.actions.GameActionManager");
        for(String name:new String[]{"addToTop","addToBottom"})manager.getDeclaredMethod(name).insertAfter("{communicationmod.GameStateListener.registerStateChange();}");
        // Only this copied test profile changes integration/metrics; normal Steam launch remains untouched.
        OfflineBytecode.neutralizeIntegration(take(pool,changed,"com.megacrit.cardcrawl.integrations.steam.SteamIntegration"));
        OfflineBytecode.neutralizeApi(take(pool,changed,"com.codedisaster.steamworks.SteamAPI"));
        for(String name:new String[]{"Metrics","BotDataUploader"})OfflineBytecode.neutralizeUploads(take(pool,changed,"com.megacrit.cardcrawl.metrics."+name));
        take(pool,changed,"com.megacrit.cardcrawl.integrations.DistributorFactory").getDeclaredMethod("isLeaderboardEnabled").setBody("{return false;}");
        // A smoke probe uses the SAME packaged bootstrap and skips only opening LWJGL.
        CtClass launcher=take(pool,changed,BOOT);
        launcher.getDeclaredMethod("main").instrument(new javassist.expr.ExprEditor(){
            public void edit(javassist.expr.MethodCall call)throws CannotCompileException {
                if(call.getClassName().equals("com.megacrit.cardcrawl.desktop.DesktopLauncher") && call.getMethodName().equals("main"))
                    call.replace("{if(java.lang.Boolean.getBoolean(\"communicationmod.smoke\")){java.lang.System.out.println(\"[COMM-OBSERVER] Packaged initialization smoke passed; no game window opened.\");}else{$proceed($$);}}");
            }
        });
        Map<String,byte[]> bytes=new LinkedHashMap<>();
        for(CtClass type:changed)bytes.put(type.getName().replace('.','/')+".class",type.toBytecode());
        return bytes;
    }
    private static CtClass take(ClassPool pool,Set<CtClass> changed,String name)throws Exception{CtClass type=pool.get(name);changed.add(type);return type;}
    private static void raw(ClassPool pool,Set<CtClass> changed,String hook,String owner,String method)throws Exception{hook(hook,take(pool,changed,owner).getDeclaredMethod(method));}
    private static void hook(String name,CtBehavior method)throws Exception{Class.forName("communicationmod.patches.DialogueRenderPatch$"+name).getMethod("Raw",CtBehavior.class).invoke(null,method);}
    private static void copyJar(Path source,Path destination,Map<String,byte[]> changes)throws Exception {
        try(JarFile jar=new JarFile(source.toFile());JarOutputStream out=new JarOutputStream(Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW))) {
            Enumeration<JarEntry> entries=jar.entries();byte[] buffer=new byte[65536];
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();out.putNextEntry(new JarEntry(entry.getName()));
                byte[] replacement=changes.get(entry.getName());
                if(replacement!=null)out.write(replacement);
                else try(InputStream in=jar.getInputStream(entry)){int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
                out.closeEntry();
            }
        }
    }
    public static String hash(Path file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
        StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format("%02x",b&255));return result.toString();
    }
}
