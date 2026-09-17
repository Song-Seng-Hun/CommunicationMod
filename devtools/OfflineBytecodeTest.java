import java.io.*;
import java.util.*;
import java.util.jar.*;
import javassist.*;
import javassist.bytecode.*;

/** No game class definitions: checks executable instructions, not unused constants. */
public final class OfflineBytecodeTest {
    public static void main(String[] args) throws Exception {
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(args[0]);
        CtClass integration = pool.get("com.megacrit.cardcrawl.integrations.steam.SteamIntegration");
        if (!Boolean.getBoolean("test.original")) {
            Class<?> transformer = Class.forName("OfflineBytecode");
            transformer.getMethod("neutralizeIntegration", CtClass.class).invoke(null, integration);
        }
        for (CtBehavior behavior : integration.getDeclaredBehaviors()) {
            checkNoOnlineCalls(behavior.getMethodInfo2());
        }
        for (CtMethod method : integration.getDeclaredMethods()) {
            CtClass returns = method.getReturnType();
            int expected = returns == CtClass.longType ? Opcode.LRETURN
                : returns == CtClass.floatType ? Opcode.FRETURN
                : returns == CtClass.doubleType ? Opcode.DRETURN : -1;
            if (expected >= 0) {
                byte[] code = method.getMethodInfo2().getCodeAttribute().getCode();
                if ((code[code.length - 1] & 255) != expected)
                    throw new AssertionError("Wrong typed return in " + method.getLongName());
            }
        }
        System.out.println("PASS: SteamIntegration executable code has no Steam/library/network calls");
        if (Boolean.getBoolean("test.original")) return;
        Class<?> transformer = Class.forName("OfflineBytecode");
        CtClass api = pool.get("com.codedisaster.steamworks.SteamAPI");
        transformer.getMethod("neutralizeApi", CtClass.class).invoke(null, api);
        for (CtBehavior behavior : api.getDeclaredBehaviors()) checkNoOnlineCalls(behavior.getMethodInfo2());
        // Execute ONLY transformed SteamAPI bytes, in a loader that refuses every game/Steam dependency.
        Class<?> safeApi = new OnlyClass(api.getName(), api.toBytecode()).loadClass(api.getName());
        safeApi.getMethod("loadLibraries").invoke(null);
        safeApi.getMethod("loadLibraries", String.class).invoke(null, "DO_NOT_LOAD");
        safeApi.getMethod("skipLoadLibraries").invoke(null);
        if ((Boolean) safeApi.getMethod("init").invoke(null)) throw new AssertionError("Fake initialization");
        if ((Boolean) safeApi.getMethod("isSteamRunning", boolean.class).invoke(null, true)) throw new AssertionError("Steam active");
        if ((Boolean) safeApi.getMethod("restartAppIfNecessary", int.class).invoke(null, 1865780)) throw new AssertionError("Restart requested");
        safeApi.getMethod("runCallbacks").invoke(null);
        safeApi.getMethod("shutdown").invoke(null);

        int natives = 0;
        try (JarFile jar = new JarFile(args[0])) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("com/codedisaster/steamworks/") || !entry.getName().endsWith(".class")) continue;
                CtClass type = pool.get(entry.getName().replace('/', '.').replace(".class", ""));
                for (CtMethod method : type.getDeclaredMethods()) if (Modifier.isNative(method.getModifiers())) natives++;
                transformer.getMethod("blockNativeMethods", CtClass.class).invoke(null, type);
                for (CtMethod method : type.getDeclaredMethods()) {
                    if (Modifier.isNative(method.getModifiers())) throw new AssertionError("Native entry survived");
                }
            }
        }
        if (natives < 100) throw new AssertionError("Unexpected installed Steam native surface: " + natives);
        // Independent fixture verifies native methods THROW, not falsely report successful writes.
        CtClass fixture = pool.makeClass("NativeFixture");
        CtMethod upload = new CtMethod(CtClass.booleanType, "upload", new CtClass[]{CtClass.longType}, fixture);
        upload.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.NATIVE);
        fixture.addMethod(upload);
        transformer.getMethod("blockNativeMethods", CtClass.class).invoke(null, fixture);
        assertThrowsSecurityException(upload.getMethodInfo2());
        CtMethod falseSuccess = CtNewMethod.make("public static boolean falseSuccess() { return true; }", fixture);
        try {
            assertThrowsSecurityException(falseSuccess.getMethodInfo2());
            throw new AssertionError("Verifier accepted a false-success replacement");
        } catch (IllegalStateException expected) { /* Intentionally bad replacement caught. */ }
        CtClass loader = pool.get("com.codedisaster.steamworks.SteamSharedLibraryLoader");
        int caughtLoaders = 0;
        for (CtMethod method : loader.getDeclaredMethods()) {
            if (!method.getName().equals("loadLibrary") && !method.getName().equals("loadLibraries")) continue;
            try {
                assertThrowsSecurityException(method.getMethodInfo2());
                throw new AssertionError("Verifier accepted original native-library loader");
            } catch (IllegalStateException expected) { caughtLoaders++; }
        }
        if (caughtLoaders == 0) throw new AssertionError("Missing shared-library negative fixtures");
        Class<?> fixtureClass = new OnlyClass(fixture.getName(), fixture.toBytecode()).loadClass(fixture.getName());
        try {
            fixtureClass.getMethod("upload", long.class).invoke(null, 12L);
            throw new AssertionError("Native upload allowed");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof SecurityException)) throw expected;
        }
        System.out.println("PASS: offline SteamAPI executes without any game/Steam dependency; " + natives + " native entrypoints replaced");
        CtClass metrics = pool.get("com.megacrit.cardcrawl.metrics.Metrics");
        byte[] localHistory = metrics.getDeclaredMethod("gatherAllDataAndSave").getMethodInfo2().getCodeAttribute().getCode().clone();
        transformer.getMethod("neutralizeUploads", CtClass.class).invoke(null, metrics);
        for (CtMethod method : metrics.getDeclaredMethods()) {
            if (method.getName().equals("sendPost") || method.getName().equals("run")) {
                if (!Arrays.equals(method.getMethodInfo2().getCodeAttribute().getCode(), new byte[]{(byte)Opcode.RETURN}))
                    throw new AssertionError("Upload path survived: " + method.getLongName());
            }
        }
        if (!Arrays.equals(localHistory, metrics.getDeclaredMethod("gatherAllDataAndSave").getMethodInfo2().getCodeAttribute().getCode()))
            throw new AssertionError("Local history was removed with upload transport");
        CtClass network = pool.get("com.badlogic.gdx.backends.lwjgl.LwjglNet");
        transformer.getMethod("neutralizeNetwork", CtClass.class).invoke(null, network);
        for (CtMethod method : network.getDeclaredMethods()) checkNoOnlineCalls(method.getMethodInfo2());
        System.out.println("PASS: upload senders disabled, local history unchanged, LibGDX network/browser boundary disabled");
    }

    static void checkNoOnlineCalls(MethodInfo method) throws Exception {
        if ((method.getAccessFlags() & AccessFlag.NATIVE) != 0) throw new AssertionError("Native: " + method.getName());
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) return;
        CodeIterator it = code.iterator();
        ConstPool cp = method.getConstPool();
        while (it.hasNext()) {
            int pos = it.next();
            int op = it.byteAt(pos);
            if (op == Opcode.INVOKEDYNAMIC) throw new AssertionError("Dynamic call in offline boundary: " + method.getName());
            if (op != Opcode.INVOKESTATIC && op != Opcode.INVOKEVIRTUAL && op != Opcode.INVOKESPECIAL && op != Opcode.INVOKEINTERFACE) continue;
            int ref = it.u16bitAt(pos + 1);
            String owner = op == Opcode.INVOKEINTERFACE ? cp.getInterfaceMethodrefClassName(ref) : cp.getMethodrefClassName(ref);
            String name = op == Opcode.INVOKEINTERFACE ? cp.getInterfaceMethodrefName(ref) : cp.getMethodrefName(ref);
            boolean constructor = name.equals("<init>") && (owner.equals("java.lang.Object")
                    || owner.equals("java.util.ArrayList") || owner.equals("java.lang.SecurityException"));
            boolean failureCallback = owner.equals("com.badlogic.gdx.Net$HttpResponseListener") && name.equals("failed");
            if (!constructor && !failureCallback) {
                throw new AssertionError(method.getName() + " still calls " + owner + "." + name);
            }
        }
    }

    static void assertThrowsSecurityException(MethodInfo method) throws Exception {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null || (method.getAccessFlags() & AccessFlag.NATIVE) != 0 || code.getExceptionTable().size() != 0)
            throw new IllegalStateException("Not an unconditional block: " + method.getName());
        CodeIterator it = code.iterator();
        int[] expected = {Opcode.NEW, Opcode.DUP, Opcode.LDC, Opcode.INVOKESPECIAL, Opcode.ATHROW};
        ConstPool cp = method.getConstPool();
        int step = 0;
        while (it.hasNext()) {
            int pos = it.next(); int op = it.byteAt(pos);
            if (step >= expected.length || (op != expected[step] && !(step == 2 && op == Opcode.LDC_W)))
                throw new IllegalStateException("Block has extra/incorrect instructions: " + method.getName());
            if (step == 0 && !cp.getClassInfo(it.u16bitAt(pos + 1)).equals("java.lang.SecurityException"))
                throw new IllegalStateException("Wrong exception type");
            if (step == 2) {
                int ref = op == Opcode.LDC ? it.byteAt(pos + 1) : it.u16bitAt(pos + 1);
                if (!cp.getStringInfo(ref).startsWith("COMM-OFFLINE:")) throw new IllegalStateException("Missing diagnostic");
            }
            if (step == 3) {
                int ref = it.u16bitAt(pos + 1);
                if (!cp.getMethodrefClassName(ref).equals("java.lang.SecurityException")
                        || !cp.getMethodrefName(ref).equals("<init>")
                        || !cp.getMethodrefType(ref).equals("(Ljava/lang/String;)V"))
                    throw new IllegalStateException("Wrong block constructor");
            }
            step++;
        }
        if (step != expected.length) throw new IllegalStateException("Incomplete block");
    }

    private static final class OnlyClass extends ClassLoader {
        private final String name;
        private final byte[] bytes;
        OnlyClass(String name, byte[] bytes) { super(null); this.name = name; this.bytes = bytes; }
        protected Class<?> findClass(String requested) throws ClassNotFoundException {
            if (!name.equals(requested)) throw new ClassNotFoundException("Dependency forbidden: " + requested);
            return defineClass(requested, bytes, 0, bytes.length);
        }
    }
}
