import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import javassist.*;

/** Produces NON-LAUNCHABLE copies, never invokes MTS Loader or game initialization.
 * A pinned input set is intentional: a game update requires a new audit.
 */
public final class PrepareOfflineRuntime {
    private static final String[] JARS = {"desktop-1.0-modded.jar", "package/BaseMod-modded.jar",
        "package/StSLib-modded.jar", "package/EvilWithin-modded.jar"};
    private static final String[] HASHES = {
        "abb42ebdc5d3ea66b30d6a1993d80880ab45b21a1de01f8ce43945bf31b9fee8",
        "ba44fdfdccd6b0949a862e4555daf7f91cd5ebb71f24559bff420d99166a2056",
        "6dfb351694567d925282bf3a1895905e35fce4cc4ec619b0362d29220bd366bd",
        "148c422dd26e869fbdc9a63f9ab072373c3d791d3e3fcbe8d87b55ae75a29226"};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Installed Downfall directory and NEW output directory required");
        Path source = Paths.get(args[0]).toRealPath();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IOException("Refusing to overwrite an existing output: " + output);
        Path parent = output.getParent().toRealPath();
        if (parent.startsWith(source)) throw new IOException("Output must not be inside the Steam installation");
        for (int i = 0; i < JARS.length; i++) requireHash(source.resolve(JARS[i]), HASHES[i]);
        Files.createDirectory(output);
        ClassPool pool = new ClassPool(true);
        for (String jar : JARS) pool.insertClassPath(source.resolve(jar).toString());
        StringBuilder report = new StringBuilder("status=prepared_not_launchable\nlaunch_allowed=false\n");
        for (int i = 0; i < JARS.length; i++) {
            Path input = source.resolve(JARS[i]);
            Path destination = output.resolve(JARS[i]);
            Files.createDirectories(destination.getParent());
            Path partial = destination.resolveSibling(destination.getFileName() + ".partial");
            int changed = transformJar(input, partial, pool);
            requireHash(input, HASHES[i]);
            Files.move(partial, destination);
            report.append(JARS[i]).append(" input_sha256=").append(HASHES[i])
                .append(" output_sha256=").append(sha256(destination))
                .append(" transformed_classes=").append(changed).append('\n');
        }
        Files.write(output.resolve("PREPARED-NOT-LAUNCHABLE.txt"), report.toString().getBytes("UTF-8"), StandardOpenOption.CREATE_NEW);
        System.out.println(report);
        System.out.println("Steam Java boundaries prepared. Storage/native-library isolation and actual gameplay NOT verified. Existing launch holds remain.");
    }

    private static int transformJar(Path input, Path output, ClassPool pool) throws Exception {
        int count = 0;
        try (JarFile jar = new JarFile(input.toFile());
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            Enumeration<JarEntry> entries = jar.entries();
            byte[] buffer = new byte[32768];
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                byte[] replacement = null;
                if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    Manifest manifest;
                    try (InputStream in = jar.getInputStream(entry)) { manifest = new Manifest(in); }
                    // No double-click/java -jar execution of an unverified prepared copy.
                    manifest.getMainAttributes().remove(Attributes.Name.MAIN_CLASS);
                    manifest.getMainAttributes().remove(Attributes.Name.CLASS_PATH);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    manifest.write(bytes);
                    replacement = bytes.toByteArray();
                } else if (name.endsWith(".class") && isTarget(name)) {
                    CtClass type;
                    try (InputStream in = jar.getInputStream(entry)) { type = pool.makeClass(in); }
                    String className = type.getName();
                    if (className.equals("com.megacrit.cardcrawl.integrations.steam.SteamIntegration")) {
                        OfflineBytecode.neutralizeIntegration(type);
                    } else if (className.equals("com.codedisaster.steamworks.SteamAPI")) {
                        OfflineBytecode.neutralizeApi(type);
                    } else if (className.equals("com.badlogic.gdx.backends.lwjgl.LwjglNet")) {
                        OfflineBytecode.neutralizeNetwork(type);
                    } else if (className.startsWith("com.megacrit.cardcrawl.metrics.")) {
                        OfflineBytecode.neutralizeUploads(type);
                    } else if (className.equals("com.megacrit.cardcrawl.integrations.DistributorFactory")) {
                        type.getDeclaredMethod("isLeaderboardEnabled").setBody("{ return false; }");
                        // Do not select any other store/native provider from build settings.
                        type.getDeclaredMethod("getEnabledDistributor").setBody("{ return new com.megacrit.cardcrawl.integrations.steam.SteamIntegration(); }");
                    } else {
                        OfflineBytecode.blockNativeMethods(type);
                        if (className.equals("com.codedisaster.steamworks.SteamSharedLibraryLoader")) {
                            for (CtMethod method : type.getDeclaredMethods()) {
                                if (method.getName().equals("loadLibrary") || method.getName().equals("loadLibraries"))
                                    method.setBody("{ throw new java.lang.SecurityException(\"COMM-OFFLINE: Steam library loading disabled\"); }");
                            }
                        }
                    }
                    if (type.isModified()) { replacement = type.toBytecode(); count++; }
                    type.detach();
                }
                JarEntry copy = new JarEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                if (replacement != null) out.write(replacement);
                else try (InputStream in = jar.getInputStream(entry)) {
                    int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                out.closeEntry();
            }
        }
        return count;
    }

    private static boolean isTarget(String name) {
        return name.startsWith("com/codedisaster/steamworks/")
            || name.equals("com/badlogic/gdx/backends/lwjgl/LwjglNet.class")
            || name.equals("com/megacrit/cardcrawl/metrics/Metrics.class")
            || name.equals("com/megacrit/cardcrawl/metrics/BotDataUploader.class")
            || name.equals("com/megacrit/cardcrawl/integrations/steam/SteamIntegration.class")
            || name.equals("com/megacrit/cardcrawl/integrations/DistributorFactory.class");
    }

    private static void requireHash(Path path, String expected) throws Exception {
        if (!sha256(path).equals(expected)) throw new IOException("Unreviewed runtime version; refusing preparation: " + path);
    }
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder text = new StringBuilder();
        for (byte b : digest.digest()) text.append(String.format("%02x", b & 255));
        return text.toString();
    }
}
