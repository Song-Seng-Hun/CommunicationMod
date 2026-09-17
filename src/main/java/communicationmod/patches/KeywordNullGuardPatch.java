package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireRawPatch;
import com.megacrit.cardcrawl.helpers.TipHelper;
import javassist.CannotCompileException;
import javassist.CtBehavior;

@SpirePatch(clz = TipHelper.class, method = "renderKeywords")
public class KeywordNullGuardPatch {
    @SpireRawPatch
    public static void Raw(CtBehavior method) throws CannotCompileException {
        // Run before BaseMod's already-injected FakeKeywords prefix. Replacing the
        // local argument protects both BaseMod and vanilla without editing card state.
        method.insertBefore("{ $4 = communicationmod.patches.KeywordNullGuard.sanitize($4, card); }");
    }
}
