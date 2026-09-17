import java.lang.reflect.Method;

/**
 * Runs an already prepackaged Downfall runtime with the additional patched
 * CommunicationMod classes placed first on the class path.
 */
public final class DevGameLauncher {
    private DevGameLauncher() {}

    public static void main(String[] args) throws Exception {
        DevelopmentLaunchSafety.requireVerifiedRuntime();
        Class<?> loader = Class.forName("com.evacipated.cardcrawl.modthespire.Loader");
        invoke("com.evacipated.cardcrawl.modthespire.Loader", "loadMTSVersion", "p");

        // The prepackaged launcher normally prepares these private Loader fields.
        // Recreate that small part because this launcher is intentionally outside
        // the original jar and must not patch the user's installation in place.
        Class<?> modInfo = Class.forName("com.evacipated.cardcrawl.modthespire.ModInfo");
        String[] packagedJarProperties = {"dev.basemod.jar", "dev.stslib.jar", "dev.downfall.jar"};
        Object packaged = java.lang.reflect.Array.newInstance(modInfo, packagedJarProperties.length);
        Method readModInfo = modInfo.getMethod("ReadModInfo", java.io.File.class);
        for (int i = 0; i < packagedJarProperties.length; i++) {
            String jar = System.getProperty(packagedJarProperties[i]);
            if (jar == null || jar.isEmpty()) {
                throw new IllegalArgumentException("Missing -D" + packagedJarProperties[i] + "=<path>");
            }
            java.lang.reflect.Array.set(packaged, i, readModInfo.invoke(null, new java.io.File(jar)));
        }
        loader.getField("MODINFOS").set(null, packaged);

        String communicationJar = System.getProperty("dev.communication.jar");
        if (communicationJar != null && !communicationJar.isEmpty()) {
            Object communicationInfo = readModInfo.invoke(null, new java.io.File(communicationJar));
            int length = java.lang.reflect.Array.getLength(packaged);
            Object combined = java.lang.reflect.Array.newInstance(modInfo, length + 1);
            for (int i = 0; i < length; i++) {
                java.lang.reflect.Array.set(combined, i, java.lang.reflect.Array.get(packaged, i));
            }
            java.lang.reflect.Array.set(combined, length, communicationInfo);
            loader.getField("MODINFOS").set(null, combined);
        }

        Class<?> classPool = Class.forName("javassist.ClassPool");
        Object defaultPool = classPool.getMethod("getDefault").invoke(null);
        java.lang.reflect.Field pool = loader.getDeclaredField("POOL");
        pool.setAccessible(true);
        pool.set(null, defaultPool);

        Method bustEnums = Class.forName("com.evacipated.cardcrawl.modthespire.Patcher")
            .getMethod("bustEnums", ClassLoader.class, java.lang.reflect.Array.newInstance(modInfo, 0).getClass());
        bustEnums.invoke(null, ClassLoader.getSystemClassLoader(), loader.getField("MODINFOS").get(null));

        // Keep the same initializer order as Downfall's prepackaged launcher.
        String[] initializers = {
            "basemod.BaseMod",
            "com.evacipated.cardcrawl.mod.stslib.StSLib",
            "slimebound.SlimeboundMod",
            "expansioncontent.expansionContentMod",
            "guardian.GuardianMod",
            "sneckomod.SneckoMod",
            "theHexaghost.HexaMod",
            "gremlin.GremlinMod",
            "collector.CollectorMod",
            "automaton.AutomatonMod",
            "awakenedOne.AwakenedOneMod",
            "downfall.downfallMod",
            "hermit.HermitMod",
            "champ.ChampMod",
            "reskinContent.reskinContent"
        };
        for (String initializer : initializers) {
            invoke(initializer, "initialize");
        }
        invoke("communicationmod.CommunicationMod", "initialize");

        if (Boolean.getBoolean("dev.no-game")) {
            System.out.println("Initialization smoke test completed.");
            return;
        }

        Class<?> launcher = Class.forName("com.megacrit.cardcrawl.desktop.DesktopLauncher");
        Method main = launcher.getDeclaredMethod("main", String[].class);
        main.invoke(null, (Object) args);
    }

    private static void invoke(String className, String methodName, Object... args) throws Exception {
        Class<?> type = Class.forName(className);
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = String.class;
        }
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(null, args);
    }
}
