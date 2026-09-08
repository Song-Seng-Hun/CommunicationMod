package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireRawPatch;
import javassist.*;

public final class CombatReadinessPatch {
    private static String safe(String call) {
        String binding = "communicationmod.observation.CombatObservation.";
        return "{try{"+binding+call+";}catch(java.lang.RuntimeException e){"+binding+"failed(e);}catch(java.lang.LinkageError e){"+binding+"failed(e);}}";
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.core.CardCrawlGame.class, method="render")
    public static class Frame {
        @SpireRawPatch public static void Raw(CtBehavior method) throws CannotCompileException {
            method.insertBefore(safe("beginFrame(screenColor == null ? 1.0f : screenColor.a)"));
            method.insertAfter(safe("completeFrame(screenColor == null ? 1.0f : screenColor.a)"));
            method.insertAfter(safe("endFrame()"), true);
        }
    }
}
