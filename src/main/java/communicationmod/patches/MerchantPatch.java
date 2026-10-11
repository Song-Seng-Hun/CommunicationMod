package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.shop.Merchant;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.ArrayList;

public class MerchantPatch {

    public static boolean visitMerchant = false;

    /**
     * Shared merchant-entry injection used by both the normal ModTheSpire patch and the
     * copied local runtime. The local runtime does not automatically apply every
     * @SpirePatch, so BuildLocalObserver wires this method into Merchant.update directly.
     */
    public static void consume(Merchant merchant) {
        if (visitMerchant) {
            merchant.hb.hovered = true;
            InputHelper.justClickedLeft = true;
            visitMerchant = false;
        }
    }

    /** Audited native entry point: restore only the active input scope after hitbox refresh. */
    public static void Raw(CtBehavior update,final boolean legacy) throws CannotCompileException {
        final int[] hitboxes={0};
        update.instrument(new ExprEditor() {
            public void edit(MethodCall call) throws CannotCompileException {
                if(call.getClassName().equals(Hitbox.class.getName()) && call.getMethodName().equals("update")) {
                    hitboxes[0]++;
                    call.replace("{$proceed($$);if(java.lang.Boolean.getBoolean(\"communicationmod.play_control\")){communicationmod.observation.NativeUiInput.afterHitbox($0);"
                        +(legacy?"communicationmod.patches.MerchantPatch.consume(this);":"")+"}}");
                }
            }
        });
        if(hitboxes[0]!=1)throw new CannotCompileException("Expected exactly one merchant hitbox update: "+update.getLongName()+"; found "+hitboxes[0]);
    }

    @SpirePatch(cls="downfall.util.HeartMerchant",method="update",optional=true)
    public static class HeartMerchantUpdatePatch {
        @SpireRawPatch public static void Raw(CtBehavior update) throws CannotCompileException {
            MerchantPatch.Raw(update,false);
        }
    }

    @SpirePatch(
            clz=Merchant.class,
            method="update"
    )
    public static class MerchantUpdatePatch {

        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(Merchant _instance) {
            consume(_instance);
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.MethodCallMatcher(Hitbox.class, "update");
                int[] results = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
                results[0] += 1;
                return results;
            }
        }

    }

}
