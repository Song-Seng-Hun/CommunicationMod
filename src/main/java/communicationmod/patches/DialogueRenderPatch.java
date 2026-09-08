package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireRawPatch;
import javassist.*;

/** Only the new observer is injected; original render/control flow is retained. */
public final class DialogueRenderPatch {
    private static final String OBS = "communicationmod.observation.DialogueObservation.";
    private static String safe(String call) {
        return "{try{" + OBS + call + ";}catch(java.lang.RuntimeException e){" + OBS
            + "failed(e);}catch(java.lang.LinkageError e){" + OBS + "failed(e);}}";
    }
    private static void origin(CtBehavior method, String call) throws CannotCompileException {
        method.insertBefore(safe(call));
        method.insertAfter(safe("leaveOrigin()"), true);
    }
    private static void render(CtBehavior method, String channel) throws CannotCompileException {
        method.insertBefore(safe("enterRender($0,\"" + channel + "\")"));
        method.insertAfter(safe("finishRender()"));
        method.insertAfter(safe("leaveRender()"), true);
    }
    private static void word(CtBehavior method) throws CannotCompileException {
        method.insertAfter(safe("word($0,word,line,color.a,scale)"));
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.core.CardCrawlGame.class, method="render")
    public static class Frame {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException {
            m.insertBefore(safe("beginFrame(screenColor == null ? 1.0f : screenColor.a)"));
            m.insertAfter(safe("completeFrame(screenColor == null ? 1.0f : screenColor.a)"));
        }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.core.CardCrawlGame.class, method="startOver", paramtypez={})
    public static class Reset {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException {
            m.insertBefore(safe("reset()"));
        }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.actions.animations.TalkAction.class, method="update")
    public static class Talk {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { origin(m, "enterActor($0.source)"); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.neow.NeowEvent.class, method="talk")
    public static class Neow {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException {
            origin(m, "enterOrigin(\"neow\",com.megacrit.cardcrawl.neow.NeowEvent.NAME)");
        }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.shop.ShopScreen.class, method="createSpeech")
    public static class Merchant {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { origin(m, "enterOrigin(\"merchant\",null)"); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.vfx.SpeechTextEffect.class, method=SpirePatch.CONSTRUCTOR,
        paramtypez={float.class,float.class,float.class,String.class,com.megacrit.cardcrawl.ui.DialogWord.AppearEffect.class})
    public static class SpeechCreated {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { m.insertAfter(safe("created($0)")); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.vfx.SpeechTextEffect.class, method="render")
    public static class SpeechRender {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { render(m, "speech_text"); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.events.GenericEventDialog.class, method="render")
    public static class GenericRender {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { render(m, "event_dialog"); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.events.RoomEventDialog.class, method="render")
    public static class RoomRender {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { render(m, "event_dialog"); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.events.GenericEventDialog.class, method="updateBodyText",
        paramtypez={String.class,com.megacrit.cardcrawl.ui.DialogWord.AppearEffect.class})
    public static class GenericBody {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { m.insertAfter(safe("newBody($0)")); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.events.RoomEventDialog.class, method="updateBodyText",
        paramtypez={String.class,com.megacrit.cardcrawl.ui.DialogWord.AppearEffect.class})
    public static class RoomBody {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { m.insertAfter(safe("newBody($0)")); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.ui.SpeechWord.class, method="render", paramtypez={com.badlogic.gdx.graphics.g2d.SpriteBatch.class})
    public static class SpeechWord {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { word(m); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.ui.SpeechWord.class, method="render", paramtypez={com.badlogic.gdx.graphics.g2d.SpriteBatch.class,float.class})
    public static class SpeechWordOffset {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { word(m); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.ui.DialogWord.class, method="render", paramtypez={com.badlogic.gdx.graphics.g2d.SpriteBatch.class})
    public static class DialogWord {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { word(m); }
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.ui.DialogWord.class, method="render", paramtypez={com.badlogic.gdx.graphics.g2d.SpriteBatch.class,float.class})
    public static class DialogWordOffset {
        @SpireRawPatch public static void Raw(CtBehavior m) throws CannotCompileException { word(m); }
    }
}
