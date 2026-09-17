import javassist.*;

/** Build-time transformations only. Does not define, initialize, or run game classes.
 * This closes the audited Steam Java entrypoints, NOT the storage/OS isolation gate.
 */
public final class OfflineBytecode {
    private OfflineBytecode() {}

    public static void neutralizeUploads(CtClass type) throws Exception {
        String name = type.getName();
        if (!name.equals("com.megacrit.cardcrawl.metrics.Metrics")
                && !name.equals("com.megacrit.cardcrawl.metrics.BotDataUploader"))
            throw new IllegalArgumentException("Wrong upload target: " + name);
        for (CtMethod method : type.getDeclaredMethods()) {
            if (method.getName().equals("sendPost") || method.getName().equals("run")
                    || method.getName().equals("uploadDataAsync")) method.setBody("{ return; }");
        }
    }

    public static void neutralizeNetwork(CtClass type) throws Exception {
        requireName(type, "com.badlogic.gdx.backends.lwjgl.LwjglNet");
        for (CtMethod method : type.getDeclaredMethods()) {
            String name = method.getName();
            if (name.equals("sendHttpRequest")) {
                // Complete asynchronously expected callers through their failure path;
                // never pretend a metrics submission or time lookup succeeded.
                method.setBody("{ if ($2 != null) $2.failed(new java.lang.SecurityException(\"COMM-OFFLINE: HTTP disabled\")); }");
            } else if (name.equals("cancelHttpRequest")) method.setBody("{ return; }");
            else if (name.equals("openURI")) method.setBody("{ return false; }");
            else if (name.equals("newClientSocket") || name.equals("newServerSocket"))
                method.setBody("{ throw new java.lang.SecurityException(\"COMM-OFFLINE: sockets disabled\"); }");
            else throw new IllegalArgumentException("Unreviewed LwjglNet method: " + method.getLongName());
        }
    }

    public static void neutralizeIntegration(CtClass type) throws Exception {
        requireName(type, "com.megacrit.cardcrawl.integrations.steam.SteamIntegration");
        for (CtConstructor constructor : type.getDeclaredConstructors()) constructor.setBody("{ super(); }");
        if (type.getClassInitializer() != null) type.getClassInitializer().setBody("{}");
        for (CtMethod method : type.getDeclaredMethods()) {
            String body = defaultBody(method.getReturnType());
            // Keep the platform identity but report isInitialized=false; the factory
            // independently disables leaderboard UI. Never impersonate another store.
            if (method.getName().equals("getType")) {
                body = "{ return com.megacrit.cardcrawl.integrations.DistributorFactory.Distributor.STEAM; }";
            } else if (method.getName().equals("getAllCloudFiles")) {
                body = "{ return new java.util.ArrayList(); }";
            }
            method.setBody(body);
        }
    }

    public static void neutralizeApi(CtClass type) throws Exception {
        requireName(type, "com.codedisaster.steamworks.SteamAPI");
        if (type.getClassInitializer() != null) type.getClassInitializer().setBody("{}");
        for (CtMethod method : type.getDeclaredMethods()) {
            method.setModifiers(method.getModifiers() & ~Modifier.NATIVE);
            method.setBody(defaultBody(method.getReturnType()));
            method.setExceptionTypes(new CtClass[0]);
        }
    }

    public static void blockNativeMethods(CtClass type) throws Exception {
        for (CtMethod method : type.getDeclaredMethods()) {
            if (!Modifier.isNative(method.getModifiers())) continue;
            method.setModifiers(method.getModifiers() & ~Modifier.NATIVE);
            method.setBody("{ throw new java.lang.SecurityException(\"COMM-OFFLINE: Steam native calls are disabled\"); }");
        }
    }

    private static String defaultBody(CtClass returns) {
        if (returns == CtClass.voidType) return "{ return; }";
        if (returns == CtClass.booleanType) return "{ return false; }";
        // The bundled Javassist does not widen an integer literal for setBody.
        if (returns == CtClass.longType) return "{ return 0L; }";
        if (returns == CtClass.floatType) return "{ return 0.0f; }";
        if (returns == CtClass.doubleType) return "{ return 0.0d; }";
        if (returns.isPrimitive()) return "{ return 0; }";
        return "{ return null; }";
    }

    private static void requireName(CtClass type, String expected) {
        if (!type.getName().equals(expected)) throw new IllegalArgumentException("Wrong transformation target: " + type.getName());
    }
}
