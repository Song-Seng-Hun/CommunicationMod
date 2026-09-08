import java.io.DataInputStream;
import java.util.jar.JarFile;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

/** Reads bytecode only; never defines/initializes game or mod classes. */
public final class SafetyWiringTest {
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            verify(jar, "communicationmod/CommandExecutor.class", "executeCommand", "requireAutomationAllowed");
            verify(jar, "communicationmod/CommunicationMod.class", "queueCommand", "requireAutomationAllowed");
            verify(jar, "communicationmod/CommunicationMod.class", "receivePreUpdate", "isAutomationAllowed");
            verify(jar, "communicationmod/CommunicationMod.class", "startExternalProcess", "isAutomationAllowed");
        }
        System.out.println("PASS: built mod gates every subprocess/command entry before other instructions");
    }

    private static void verify(JarFile jar, String entry, String methodName, String gate) throws Exception {
        try (DataInputStream input = new DataInputStream(jar.getInputStream(jar.getJarEntry(entry)))) {
            ClassFile type = new ClassFile(input);
            for (Object item : type.getMethods()) {
                MethodInfo method = (MethodInfo) item;
                if (!method.getName().equals(methodName)) continue;
                byte[] code = method.getCodeAttribute().getCode();
                ConstPool pool = method.getConstPool();
                int ref = ((code[1] & 255) << 8) | (code[2] & 255);
                if ((code[0] & 255) != Opcode.INVOKESTATIC
                    || !pool.getMethodrefClassName(ref).equals("communicationmod.safety.AutomationSafety")
                    || !pool.getMethodrefName(ref).equals(gate)) {
                    throw new AssertionError(entry + ":" + methodName + " lacks the first-instruction safety gate");
                }
                if (gate.equals("isAutomationAllowed") && (code[3] & 255) != Opcode.IFNE) {
                    throw new AssertionError(entry + ":" + methodName + " discards or reverses the safety decision");
                }
                return;
            }
            throw new AssertionError("Missing method " + methodName);
        }
    }
}
