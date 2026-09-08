import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import javassist.bytecode.*;

/** Acceptance of the PREPARED bytecode artifact, not permission to launch it. */
public final class PrepareOfflineRuntimeTest {
    public static void main(String[] args) throws Exception {
        Path original = Paths.get(args[0]);
        Path candidate = Paths.get(args[1]);
        int blockedNatives = 0;
        try (JarFile before = new JarFile(original.toFile()); JarFile after = new JarFile(candidate.toFile())) {
            if (after.getManifest().getMainAttributes().getValue("Main-Class") != null
                    || after.getManifest().getMainAttributes().getValue("Class-Path") != null)
                throw new AssertionError("Prepared archive is launchable or refers to external classpath");
            // Preserve actual packaged metadata and enum/initializer sequence byte for byte.
            String bootstrap = "com/evacipated/cardcrawl/modthespire/PackageJar$PrepackagedLauncher.class";
            if (!Arrays.equals(bytes(before, bootstrap), bytes(after, bootstrap))) throw new AssertionError("Bootstrap changed");
            Enumeration<JarEntry> all = before.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                String name = entry.getName();
                if (after.getJarEntry(name) == null) throw new AssertionError("Entry lost: " + name);
                if (!name.startsWith("com/codedisaster/steamworks/") || !name.endsWith(".class")) continue;
                ClassFile oldType = new ClassFile(new DataInputStream(new ByteArrayInputStream(bytes(before, name))));
                ClassFile newType = new ClassFile(new DataInputStream(new ByteArrayInputStream(bytes(after, name))));
                for (Object item : oldType.getMethods()) {
                    MethodInfo method = (MethodInfo) item;
                    if ((method.getAccessFlags() & AccessFlag.NATIVE) == 0) continue;
                    blockedNatives++;
                    MethodInfo replacement = find(newType, method.getName(), method.getDescriptor());
                    if ((replacement.getAccessFlags() & AccessFlag.NATIVE) != 0 || replacement.getCodeAttribute() == null)
                        throw new AssertionError("Native entry survives: " + name + ":" + method.getName());
                    if (!name.equals("com/codedisaster/steamworks/SteamAPI.class"))
                        OfflineBytecodeTest.assertThrowsSecurityException(replacement);
                }
            }
            int libraryMethods = 0;
            ClassFile shared = read(after, "com/codedisaster/steamworks/SteamSharedLibraryLoader.class");
            for (Object item : shared.getMethods()) {
                MethodInfo method = (MethodInfo)item;
                if (method.getName().equals("loadLibrary") || method.getName().equals("loadLibraries")) {
                    OfflineBytecodeTest.assertThrowsSecurityException(method);
                    libraryMethods++;
                }
            }
            if (libraryMethods == 0) throw new AssertionError("No shared library loader checks");
            if (blockedNatives < 300) throw new AssertionError("Unexpected Steam native surface");
            ClassFile factory = read(after, "com/megacrit/cardcrawl/integrations/DistributorFactory.class");
            MethodInfo enabled = find(factory, "isLeaderboardEnabled", "()Z");
            if (!Arrays.equals(enabled.getCodeAttribute().getCode(), new byte[]{3, (byte)172}))
                throw new AssertionError("Leaderboard is not unconditionally disabled");
            ClassFile integration = read(after, "com/megacrit/cardcrawl/integrations/steam/SteamIntegration.class");
            for (Object item : integration.getMethods()) OfflineBytecodeTest.checkNoOnlineCalls((MethodInfo)item);
            ClassFile api = read(after, "com/codedisaster/steamworks/SteamAPI.class");
            for (Object item : api.getMethods()) OfflineBytecodeTest.checkNoOnlineCalls((MethodInfo)item);
            ClassFile network = read(after, "com/badlogic/gdx/backends/lwjgl/LwjglNet.class");
            for (Object item : network.getMethods()) {
                MethodInfo method = (MethodInfo)item;
                if (!method.getName().equals("<init>")) OfflineBytecodeTest.checkNoOnlineCalls(method);
            }
            for (String name : new String[]{"Metrics", "BotDataUploader"}) {
                ClassFile uploader = read(after, "com/megacrit/cardcrawl/metrics/" + name + ".class");
                for (Object item : uploader.getMethods()) {
                    MethodInfo method = (MethodInfo)item;
                    if (method.getName().equals("sendPost") || method.getName().equals("run") || method.getName().equals("uploadDataAsync"))
                        if (!Arrays.equals(method.getCodeAttribute().getCode(), new byte[]{(byte)Opcode.RETURN}))
                            throw new AssertionError("Upload survives in artifact: " + name + ":" + method.getName());
                }
            }
        }
        for (String name : new String[]{"BaseMod-modded.jar", "StSLib-modded.jar", "EvilWithin-modded.jar"}) {
            try (JarFile before = new JarFile(original.getParent().resolve("package").resolve(name).toFile());
                 JarFile after = new JarFile(candidate.getParent().resolve("package").resolve(name).toFile())) {
                if (before.size() != after.size()) throw new AssertionError("Companion inventory changed: " + name);
                if (after.getManifest() != null && (after.getManifest().getMainAttributes().getValue("Main-Class") != null
                        || after.getManifest().getMainAttributes().getValue("Class-Path") != null))
                    throw new AssertionError("Companion launch manifest survived: " + name);
                Enumeration<JarEntry> entries = before.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    if (after.getJarEntry(entry) == null) throw new AssertionError("Companion entry lost: " + entry);
                    if (!entry.equalsIgnoreCase("META-INF/MANIFEST.MF") && !Arrays.equals(bytes(before, entry), bytes(after, entry)))
                        throw new AssertionError("Companion content changed: " + name + ":" + entry);
                }
            }
        }
        System.out.println("PASS: prepared artifact preserves packaged bootstrap and removes " + blockedNatives + " Steam native methods; NOT a launch/isolation verification");
    }

    static ClassFile read(JarFile jar, String name) throws Exception {
        return new ClassFile(new DataInputStream(new ByteArrayInputStream(bytes(jar, name))));
    }
    static MethodInfo find(ClassFile type, String name, String descriptor) {
        for (Object item : type.getMethods()) {
            MethodInfo method = (MethodInfo)item;
            if (method.getName().equals(name) && method.getDescriptor().equals(descriptor)) return method;
        }
        throw new AssertionError("Missing " + name + descriptor);
    }
    static byte[] bytes(JarFile jar, String name) throws Exception {
        try (InputStream in = jar.getInputStream(jar.getJarEntry(name)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
