import java.io.DataInputStream;
import java.util.jar.JarFile;
import javassist.bytecode.*;

/** Static binding checks only; never defines game or Downfall classes. */
public final class MapBindingTest {
    public static void main(String[] args) throws Exception {
        try (JarFile mod = new JarFile(args[0]); JarFile downfall = new JarFile(args[1])) {
            ClassFile bridge = read(mod, "communicationmod.compat.DownfallMapCoordinates");
            for (Object entry : bridge.getConstPool().getClassNames()) {
                String name = (String)entry;
                if (name.startsWith("downfall.") || name.startsWith("downfall/"))
                    throw new AssertionError("Hard Downfall dependency: " + name);
            }
            ClassFile choices = read(mod, "communicationmod.ChoiceScreenUtils");
            requireCall(choices, "bossNodeAvailable", "communicationmod.compat.DownfallMapCoordinates", "bossUiRow");
            requireCall(choices, "getMapScreenNodeChoices", "communicationmod.compat.MapChoicePolicy", "choices");
            requireCall(choices, "getMapScreenNodeChoices", "communicationmod.compat.DownfallMapCoordinates", "isSupported");
            requireCall(choices, "makeMapChoice", "communicationmod.ChoiceScreenUtils", "bossNodeAvailable");
            requireSignature(read(downfall, "downfall.patches.ui.map.FlipMap$FirstRoom"), "isValidFirstNode");
            requireSignature(read(downfall, "downfall.patches.ui.map.FlipMap$BossStuff"), "compatibleGetARealY");
        }
        System.out.println("PASS: optional Downfall binding and shared boss-choice dispatch; installed helper signatures present (not gameplay)");
    }

    private static ClassFile read(JarFile jar, String type) throws Exception {
        try (DataInputStream in = new DataInputStream(jar.getInputStream(jar.getJarEntry(type.replace('.', '/') + ".class")))) {
            return new ClassFile(in);
        }
    }
    private static void requireSignature(ClassFile type, String name) {
        for (Object item : type.getMethods()) {
            MethodInfo method = (MethodInfo)item;
            if (method.getName().equals(name) && method.getDescriptor().equals("(Lcom/megacrit/cardcrawl/map/MapRoomNode;)I")) return;
        }
        throw new AssertionError("Installed Downfall helper changed: " + name);
    }
    private static void requireCall(ClassFile type, String name, String owner, String called) throws Exception {
        for (Object item : type.getMethods()) {
            MethodInfo method = (MethodInfo)item;
            if (!method.getName().equals(name)) continue;
            CodeIterator it = method.getCodeAttribute().iterator();
            ConstPool cp = method.getConstPool();
            while (it.hasNext()) {
                int pos = it.next();
                if (it.byteAt(pos) != Opcode.INVOKESTATIC) continue;
                int ref = it.u16bitAt(pos + 1);
                if (cp.getMethodrefClassName(ref).equals(owner) && cp.getMethodrefName(ref).equals(called)) return;
            }
        }
        throw new AssertionError(name + " does not call " + owner + "." + called);
    }
}
