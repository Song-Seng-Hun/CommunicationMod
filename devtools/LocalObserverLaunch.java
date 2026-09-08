import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit local-only test entry. Does not alter the installed Steam game or global Java. */
public final class LocalObserverLaunch {
    private static final String[] FILES={"desktop-1.0-modded.jar","package/BaseMod-modded.jar","package/StSLib-modded.jar","package/EvilWithin-modded.jar","CommunicationMod.jar"};
    public static void main(String[] args)throws Exception {
        if(args.length>1 || (args.length==1 && !args[0].equals("--check") && !args[0].equals("--smoke") && !args[0].equals("--menu-control") && !args[0].equals("--play-control")))throw new IllegalArgumentException("Only --check, --smoke, --menu-control or --play-control supported");
        Path root=Paths.get("").toRealPath();
        Properties report=verify(root);
        if(args.length==1 && args[0].equals("--check")){System.out.println("PASS: prepared and installed JAR hashes match the test manifest");return;}
        if(!System.getProperty("java.specification.version").equals("1.8"))throw new IOException("Use the bundled Java 8 for this profile");
        boolean smoke=args.length==1 && args[0].equals("--smoke");
        Path records=root.resolve("recordings").resolve(UUID.randomUUID().toString());
        if(!smoke)Files.createDirectories(records);
        System.setProperty("communicationmod.smoke",Boolean.toString(smoke));
        System.setProperty("communicationmod.observer",Boolean.toString(!smoke));
        boolean play=args.length==1 && args[0].equals("--play-control");
        System.setProperty("communicationmod.play_control",Boolean.toString(play));
        System.setProperty("communicationmod.menu_control",Boolean.toString(play || args.length==1 && args[0].equals("--menu-control")));
        System.setProperty("communicationmod.client.jar",root.resolve("CommunicationMod.jar").toString());
        System.setProperty("communicationmod.recording",records.toString());
        System.setProperty("communicationmod.build",report.getProperty("output.CommunicationMod.jar"));
        Class.forName("com.evacipated.cardcrawl.modthespire.PackageJar$PrepackagedLauncher")
            .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
    }

    public static Properties verify(Path root)throws IOException {
        Properties report=new Properties();
        try(InputStream in=Files.newInputStream(root.resolve("runtime.properties"))){report.load(in);}
        if(!"local_observer".equals(report.getProperty("profile")))throw new IOException("Not a local observer profile");
        String source=report.getProperty("source");
        if(source==null)throw new IOException("Source directory missing in manifest");
        Path installed=Paths.get(source).toRealPath();
        if(root.toRealPath().startsWith(installed))throw new IOException("Refusing test launch inside the Steam installation");
        for(String file:FILES) {
            requireHash(root.resolve(file),report.getProperty("output."+file));
            if(!file.equals("CommunicationMod.jar"))requireHash(installed.resolve(file),report.getProperty("input."+file));
        }
        return report;
    }
    private static void requireHash(Path file,String expected)throws IOException {
        if(expected==null || !expected.matches("[0-9a-f]{64}"))throw new IOException("Missing/invalid hash for "+file);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
            StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format("%02x",b&255));
            if(!expected.equals(actual.toString()))throw new IOException("Game/build changed; prepare again: "+file);
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
}
