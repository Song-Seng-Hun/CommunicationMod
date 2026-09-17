import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;

/** Headless guard tests omit graphics initialization and unrelated card gameplay methods. */
public final class KeywordGuardHarness {
    public static void main(String[] args) throws Throwable {
        ClassPool pool = new ClassPool(true);
        for (String name : new String[] {
            "com.megacrit.cardcrawl.dungeons.AbstractDungeon",
            "com.megacrit.cardcrawl.cards.AbstractCard",
            "com.megacrit.cardcrawl.cards.red.Strike_Red"
        }) {
            CtClass engine = pool.get(name);
            engine.getClassInitializer().setBody("{}");
            if (name.endsWith(".AbstractCard")) {
                // Only card fields are exercised. Prepackaged gameplay bytecode is not
                // part of this unit test and may depend on the full mod classloader.
                for (javassist.CtMethod method : engine.getDeclaredMethods()) {
                    if (javassist.Modifier.isAbstract(method.getModifiers())) continue;
                    CtClass result = method.getReturnType();
                    method.setBody(result == CtClass.voidType ? "{}" : result.isPrimitive()
                        ? "{ return ($r)0; }" : "{ return null; }");
                }
            }
        }
        Loader loader = new Loader(pool);
        loader.run("KeywordGuardTest", args);
    }
}
