package communicationmod.observation;

import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.FtueTip;
import com.megacrit.cardcrawl.ui.buttons.ProceedButton;
import communicationmod.GameStateConverter;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Exact installed two-page tutorial bindings. No generic subclass reflection fallback. */
public final class DownfallTutorialUi {
    private static final Set<String> TYPES=new HashSet<>(Arrays.asList(
        "downfall.tutorials.GuardianTutorials","downfall.tutorials.HexaghostTutorials",
        "downfall.tutorials.CharbossTutorials","downfall.tutorials.CollectorTutorials",
        "downfall.tutorials.SlimeBossTutorials","downfall.tutorials.ChampTutorials",
        "downfall.tutorials.AutomatonTutorials","downfall.tutorials.GremlinsTutorials",
        "downfall.tutorials.SneckoTutorials","downfall.tutorials.AwakenedOneTutorials"));
    private DownfallTutorialUi() { }
    public static boolean supports(FtueTip tip){return tip!=null && TYPES.contains(tip.getClass().getName());}
    public static List<ProtocolSession.Action> capture(JsonObject view,JsonObject status) {
        FtueTip tip=AbstractDungeon.ftue;
        if(!supports(tip))return Collections.emptyList();
        status.addProperty("screen","TUTORIAL");
        if(!ready(tip)){status.addProperty("reason","tutorial_transition");return Collections.emptyList();}
        ProceedButton button=AbstractDungeon.overlayMenu.proceedButton;
        JsonObject page=page(tip,button);view.add("tutorial",page);
        return Collections.singletonList(new ProtocolSession.Action("run.tutorial.confirm",page.get("confirm").getAsString(),new JsonObject(),
            a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},a->{
                if(!a.entrySet().isEmpty() || AbstractDungeon.overlayMenu.proceedButton!=button || !ready(tip)
                    || !page.equals(page(tip,button)))throw new IllegalArgumentException("Tutorial changed");
                NativeUiInput.press((com.megacrit.cardcrawl.helpers.Hitbox)NativeUiInput.field(button,"hb"),()->{
                    boolean hover=button.isHovered;
                    try{button.isHovered=true;tip.update();}finally{button.isHovered=hover;}
                });
            }));
    }
    private static boolean ready(FtueTip tip) {
        if(!supports(tip) || !RoomUi.ready() || AbstractDungeon.ftue!=tip || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.FTUE)return false;
        int slot=(Integer)NativeUiInput.field(tip,"currentSlot");
        ProceedButton button=AbstractDungeon.overlayMenu.proceedButton;
        com.megacrit.cardcrawl.helpers.Hitbox hb=(com.megacrit.cardcrawl.helpers.Hitbox)NativeUiInput.field(button,"hb");
        return (Integer)NativeUiInput.field(tip,"closeScreen")==-1 && slot<=0 && slot>=-1
            && (Float)NativeUiInput.field(tip,"scrollTimer")==0f
            && ((com.badlogic.gdx.graphics.Color)NativeUiInput.field(tip,"screen")).a>=0.8f
            && NativeUiInput.visible(button) && !hb.clicked && !hb.clickStarted;
    }
    private static JsonObject page(FtueTip tip,ProceedButton button) {
        int page=1-(Integer)NativeUiInput.field(tip,"currentSlot");
        JsonObject out=new JsonObject();out.addProperty("page",page);out.addProperty("pages",2);
        out.addProperty("body",GameStateConverter.removeTextFormatting((String)NativeUiInput.field(tip,"txt"+page)));
        out.addProperty("footer",GameStateConverter.removeTextFormatting(((String[])NativeUiInput.field(tip,"LABEL"))[2]));
        out.addProperty("confirm",(String)NativeUiInput.field(button,"label"));out.addProperty("illustrations_available",false);
        return out;
    }
}
