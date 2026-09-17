package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.*;
import java.util.function.Function;

/** Explicit optional bindings to public native panels. No reflected dump of character internals. */
public final class PlayerMechanicsObservation {
    private static final NativePanelObserver[] PANELS={new EncodePanelObserver(),new GhostflamePanelObserver()};
    private static final Set<String> CHARACTERS=new HashSet<>(Arrays.asList(
        "com.megacrit.cardcrawl.characters.Ironclad","com.megacrit.cardcrawl.characters.TheSilent","com.megacrit.cardcrawl.characters.Defect","com.megacrit.cardcrawl.characters.Watcher",
        "automaton.AutomatonChar","awakenedOne.AwakenedOneChar","champ.ChampChar","collector.CollectorChar","gremlin.characters.GremlinCharacter",
        "guardian.characters.GuardianCharacter","hermit.characters.hermit","slimebound.characters.SlimeboundCharacter","sneckomod.TheSnecko","theHexaghost.TheHexaghost"));
    private PlayerMechanicsObservation() { }
    public static Map<String,Object> capture(AbstractPlayer player,Function<AbstractCard,? extends Map<String,Object>> cardView) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("scope","pinned_native_public_character_panels");
        out.put("character_supported",player!=null && CHARACTERS.contains(player.getClass().getName()));
        for(NativePanelObserver observer:PANELS) {
            try {observer.capture(out,player,cardView);}
            catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable){out.put(observer.failureKey(),"native_character_panel_unavailable");}
        }
        CharacterResources.addTo(out,player,cardView);
        MechanicsCompleteness.addTo(out);
        return out;
    }
    /** Compatibility entry point for the pinned native formatter. */
    public static String infernoDescription(String[] d,boolean charged,boolean active,int spent,int effect) {
        return GhostflamePanelObserver.infernoDescription(d,charged,active,spent,effect);
    }
    public static Map<String,Object> orbDetails(Object orb,Function<AbstractCard,? extends Map<String,Object>> cardView) {
        Map<String,Object> out=new LinkedHashMap<>();
        if(!orbVisible(orb))return out;
        if(orb!=null && orb.getClass().getName().equals("guardian.orbs.StasisOrb"))try {
            Object card=orb.getClass().getField("stasisCard").get(orb);
            if(card instanceof AbstractCard && visible((AbstractCard)card))out.put("stasis_card",cardView.apply((AbstractCard)card));
            else out.put("stasis_card_unavailable_reason","card_not_visible");
        }catch(ReflectiveOperationException | RuntimeException unavailable){out.put("stasis_card_unavailable_reason","native_stasis_panel_unavailable");}
        NativeMechanicAccess.panel(out,"orb_mechanics",()->{
            if(NativeMechanicAccess.is(orb,"gremlin.orbs.GremlinStandby"))out.put("hp",NativeMechanicAccess.field(orb,"hp"));
            if(NativeMechanicAccess.is(orb,"slimebound.orbs.SpawnedSlime")) {
                out.put("upgraded",NativeMechanicAccess.field(orb,"upgraded"));
                if(NativeMechanicAccess.field(orb,"extraFontColor")!=null)
                    out.put("displayed_debuff",Math.max(0,NativeMechanicAccess.number(NativeMechanicAccess.field(orb,"debuffAmount")))+NativeMechanicAccess.number(NativeMechanicAccess.field(orb,"slimeBonus")));
            }
        });
        return out;
    }
    public static boolean orbVisible(Object orb) {
        if(!NativeMechanicAccess.is(orb,"slimebound.orbs.SpawnedSlime"))return true;
        try{return !Boolean.TRUE.equals(NativeMechanicAccess.field(orb,"noRender"));}
        catch(ReflectiveOperationException | RuntimeException unavailable){return false;}
    }
    private static boolean visible(AbstractCard card){return card!=null && card.isSeen && !card.isLocked && !card.isFlipped;}
}
