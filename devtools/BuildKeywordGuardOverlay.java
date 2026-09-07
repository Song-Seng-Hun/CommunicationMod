import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import communicationmod.patches.KeywordNullGuardPatch;

/** Compiles the same raw patch used by MTS against the installed prepackaged class. */
public final class BuildKeywordGuardOverlay {
    public static void main(String[] args) throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass tip = pool.get("com.megacrit.cardcrawl.helpers.TipHelper");
        CtMethod method = tip.getDeclaredMethod("renderKeywords");
        KeywordNullGuardPatch.Raw(method);
        ConstPool constants = method.getMethodInfo().getConstPool();
        CodeIterator instructions = method.getMethodInfo().getCodeAttribute().iterator();
        boolean guardFound = false;
        boolean baseModFound = false;
        while (instructions.hasNext()) {
            int index = instructions.next();
            if (instructions.byteAt(index) != Opcode.INVOKESTATIC) continue;
            int ref = instructions.u16bitAt(index + 1);
            String owner = constants.getMethodrefClassName(ref);
            if (owner.equals("communicationmod.patches.KeywordNullGuard")) guardFound = true;
            if (owner.endsWith("TipHelper.FakeKeywords")) {
                if (!guardFound) throw new AssertionError("Guard must execute before BaseMod");
                baseModFound = true;
            }
        }
        if (!guardFound || !baseModFound) throw new AssertionError("Expected patch call sites missing");
        tip.writeFile(args[0]);
        System.out.println("PASS: installed TipHelper patched; guard precedes BaseMod FakeKeywords");
    }
}
