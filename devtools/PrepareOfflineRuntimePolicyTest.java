import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;

/** Small, deliberately invalid archive fixtures; no Steam or game class definitions. */
public final class PrepareOfflineRuntimePolicyTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Paths.get(args[0]), "offline-policy-");
        Path source = Files.createDirectory(root.resolve("source"));
        Path input = source.resolve("desktop-1.0-modded.jar");
        byte[] sentinel = "not-an-audited-game".getBytes("UTF-8");
        Files.write(input, sentinel);
        reject(source, root.resolve("bad-hash-output"), "Unreviewed runtime version");
        if (Files.exists(root.resolve("bad-hash-output"))) throw new AssertionError("Created output before validating hashes");
        Path existing = Files.createDirectory(root.resolve("existing"));
        Files.write(existing.resolve("keep.txt"), sentinel);
        reject(source, existing, "Refusing to overwrite");
        if (!Arrays.equals(sentinel, Files.readAllBytes(existing.resolve("keep.txt")))) throw new AssertionError("Output overwritten");
        reject(source, source.resolve("nested"), "inside the Steam installation");
        if (Files.exists(source.resolve("nested"))) throw new AssertionError("Wrote inside source");
        if (!Arrays.equals(sentinel, Files.readAllBytes(input))) throw new AssertionError("Source changed");
        System.out.println("PASS: unknown hashes, existing output and source-nested output rejected before writes");
    }
    private static void reject(Path source, Path output, String expected) throws Exception {
        try {
            PrepareOfflineRuntime.main(new String[]{source.toString(), output.toString()});
            throw new AssertionError("Expected rejection: " + expected);
        } catch (IOException failure) {
            if (!failure.getMessage().contains(expected)) throw failure;
        }
    }
}
