import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Starts the ModTheSpire Loader from a prepackaged Downfall runtime while
 * allowing the game JAR and mods directory to be supplied explicitly.
 */
public final class DevLoader {
    private DevLoader() {}

    public static void main(String[] args) throws Exception {
        DevelopmentLaunchSafety.requireVerifiedRuntime();
        Class<?> loader = Class.forName("com.evacipated.cardcrawl.modthespire.Loader");

        String gameJar = System.getProperty("dev.sts.jar");
        if (gameJar == null || gameJar.isEmpty()) {
            throw new IllegalArgumentException("Missing -Ddev.sts.jar=<path>");
        }

        String modsDir = System.getProperty("dev.mods.dir", "mods/");
        String outputJar = System.getProperty("dev.output.jar");
        Field stsJar = loader.getField("STS_JAR");
        Field modDir = loader.getField("MOD_DIR");
        stsJar.set(null, gameJar);
        modDir.set(null, modsDir);
        if (outputJar != null && !outputJar.isEmpty()) {
            Field patchedJar = loader.getField("STS_PATCHED_JAR");
            patchedJar.set(null, outputJar);
        }

        Method main = loader.getMethod("main", String[].class);
        main.invoke(null, (Object) args);
    }
}
