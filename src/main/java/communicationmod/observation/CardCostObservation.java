package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import java.util.Map;
import java.util.*;
import basemod.helpers.CardModifierManager;
import basemod.interfaces.AlternateCardCostModifier;
import static communicationmod.observation.NativeMechanicAccess.*;

/** Passive renderer hook. Never calls getCost/freeToPlay or any resource-spending hook. */
public final class CardCostObservation {
    private static final DisplayedCost costs=new DisplayedCost();
    private CardCostObservation() { }
    public static void beginFrame(){costs.beginFrame();}
    public static void completeFrame(){costs.completeFrame();}
    public static void rendered(AbstractCard card,String text){if(visible(card))costs.record(card,fingerprint(card),text);}
    public static void addTo(Map<String,Object> target,AbstractCard card){
        Map<String,Object> rendered=costs.read(visible(card)?card:null,fingerprint(card));target.putAll(rendered);
        if(!visible(card))return;
        try {
            int reserves=reserves(),pyre=0;boolean audited=nativeType(card);
            for(Object modifier:CardModifierManager.modifiers(card)) {
                if(modifier.getClass().getName().equals("collector.cardmods.PyreMod"))pyre++;
                if(modifier instanceof AlternateCardCostModifier || !nativeType(modifier))audited=false;
            }
            if(AbstractDungeon.player!=null) {
                for(Object value:AbstractDungeon.player.powers)if(value instanceof AlternateCardCostModifier || !nativeType(value))audited=false;
                for(Object value:AbstractDungeon.player.relics)if(value instanceof AlternateCardCostModifier || !nativeType(value))audited=false;
                for(Object value:AbstractDungeon.player.orbs)if(value instanceof AlternateCardCostModifier || !nativeType(value))audited=false;
            }
            Map<String,Object> components=SpecialCost.describe((String)rendered.get("displayed_cost_text"),card.cost,card.costForTurn,card.freeToPlayOnce,reserves,EnergyPanel.totalCount,pyre,"collector:FingerOfDeath".equals(card.cardID),audited);
            if(!Boolean.TRUE.equals(rendered.get("displayed_cost_complete")))components.put("cost_components_complete",false);
            target.putAll(components);
        }catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable){target.put("cost_components_complete",false);target.put("cost_components_unavailable_reason","native_resource_binding_unavailable");}
    }
    private static int reserves() throws ReflectiveOperationException{return number(call(type("collector.util.NewReserves"),"reserveCount"));}
    private static boolean nativeType(Object value) {
        String name=value.getClass().getName();
        for(String prefix:new String[]{"com.megacrit.cardcrawl.","basemod.","com.evacipated.cardcrawl.mod.stslib.","automaton.","awakenedOne.","champ.","collector.","downfall.","gremlin.","guardian.","hermit.","slimebound.","sneckomod.","theHexaghost."})if(name.startsWith(prefix))return true;
        return false;
    }
    private static boolean visible(AbstractCard card){return card!=null && card.isSeen && !card.isLocked && !card.isFlipped;}
    private static String fingerprint(AbstractCard card){
        if(card==null)return "none";
        StringBuilder key=new StringBuilder().append(card.cardID).append('/').append(card.cost).append('/').append(card.costForTurn).append('/').append(card.freeToPlayOnce).append('/').append(card.timesUpgraded).append('/').append(EnergyPanel.totalCount);
        key.append('/').append(card.isInAutoplay).append('/').append(card.ignoreEnergyOnUse).append('/').append(card.energyOnUse);
        try{key.append("/r:").append(reserves());for(Object modifier:CardModifierManager.modifiers(card))key.append("/m:").append(modifier.getClass().getName()).append('@').append(System.identityHashCode(modifier));}
        catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable){key.append("/resource_binding_unavailable");}
        if(AbstractDungeon.player!=null){key.append('/').append(AbstractDungeon.player.currentHealth).append('/').append(AbstractDungeon.player.stance==null?"none":AbstractDungeon.player.stance.ID);
            for(AbstractPower power:AbstractDungeon.player.powers)key.append('/').append(power.ID).append(':').append(power.amount);
            for(com.megacrit.cardcrawl.relics.AbstractRelic relic:AbstractDungeon.player.relics)key.append("/relic:").append(relic.relicId).append(':').append(relic.counter);
            for(AbstractCard held:AbstractDungeon.player.hand.group)key.append("/hand:").append(held.uuid);}
        return key.toString();
    }
}
