package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.rewards.RewardItem;
import java.util.*;

/** Pinned Downfall rewards; compatibility name retained. Native screens own claim/removal and effects. */
public final class CollectorRewardUi {
    private static final String ESSENCE="collector.util.EssenceReward",CARD="collector.util.CollectibleCardReward",RARE="downfall.util.RareCardReward",SEAL="theHexaghost.util.SealSealReward";
    // Exact installed classes AND native enum names. Never allow an arbitrary CustomReward/subclass.
    // devtools/native-rewards.json is independently checked against all concrete native descendants.
    private static final Map<String,String> TYPES=types();
    private static Map<String,String> types() {
        Map<String,String> out=new HashMap<>();
        for(String[] row:new String[][]{
            {ESSENCE,"COLLECTOR_ESSENCE"},{CARD,"COLLECTOR_COLLECTIBLECARDREWARD"},{RARE,"CARD"},{SEAL,"SEALCARD"},
            {"guardian.rewards.GemReward","GEM"},{"guardian.rewards.GemRewardButRelicRng","GEM"},{"guardian.rewards.GemRewardAllRarities","GEMALLRARITIES"},
            {"automaton.util.DazingPulseReward","DAZINGPULSE"},{"automaton.util.DecaBeamReward","DECABEAM"},{"automaton.util.DonuBeamReward","DONUBEAM"},
            {"automaton.util.ExplodeReward","EXPLODE"},{"automaton.util.SpikeReward","SPIKE"},
            {"downfall.util.BossCardReward","BOSSCARD"},{"downfall.util.BossRelicReward","CARD"},{"downfall.util.JaxReward","JAXCARD"},
            {"downfall.util.ThirdSealReward","THIRDSEALCARDREWARD"},{"downfall.util.RemoveCardReward","REMOVECARD"},
            {"downfall.util.TransformCardReward","TRANSFORMCARD"},{"downfall.util.UpgradeCardReward","UPGRADECARD"},
            {"sneckomod.util.ColorfulCardReward","COLORFULCARD"},{"sneckomod.util.ColorfulPowersReward","COLORFULCARD"},
            {"sneckomod.util.ColorfulRareReward","COLORFULCARD"},{"sneckomod.util.ColorfulUncommonUpgradedReward","COLORFULCARD"},
            {"sneckomod.util.DiceRollPotionReward","COLORFULCARD"},{"sneckomod.util.UpgradedUnknownReward","UPGRADEDUNKNOWNCARD"},
            {"hermit.rewards.BountyGold","HERMIT_BOUNTY"}
        })out.put(row[0],row[1]);
        return Collections.unmodifiableMap(out);
    }
    // The installed CollectibleCardReward render and claimReward bodies both use this price.
    private static final int COLLECTIBLE_COST=3;
    private CollectorRewardUi() { }
    public static boolean supports(RewardItem item) {
        if(item==null || item.type==null)return false;
        return item.type.name().equals(TYPES.get(item.getClass().getName()));
    }
    public static boolean claimable(RewardItem item) {
        return supports(item) && !item.isDone && !item.ignoreReward && item.hb!=null && !item.hb.clicked && !item.hb.clickStarted
            && (!CARD.equals(item.getClass().getName()) || essence(item)>=COLLECTIBLE_COST);
    }
    public static JsonObject details(RewardItem item) {
        if(!supports(item))throw new IllegalArgumentException("Unsupported Collector reward");
        JsonObject out=new JsonObject();String name=item.getClass().getName();
        if(ESSENCE.equals(name))out.addProperty("amount",(Integer)NativeUiInput.field(item,"amount"));
        else if(CARD.equals(name)) {
            out.addProperty("currency","essence");out.addProperty("cost",COLLECTIBLE_COST);out.addProperty("essence",essence(item));
            java.util.Map<String,Object> card=CardUpgradeObservation.snapshotWithUpgrade((AbstractCard)NativeUiInput.field(item,"card"));
            out.add("card",new Gson().toJsonTree(card));
        } else if("hermit.rewards.BountyGold".equals(name)) {
            // Its declared amount field is unused; the installed claimReward pays inherited goldAmt.
            out.addProperty("amount",item.goldAmt);
        } else if(name.equals("downfall.util.RemoveCardReward") || name.equals("downfall.util.TransformCardReward")
            || name.equals("downfall.util.UpgradeCardReward") || name.equals("downfall.util.BossRelicReward")) {
            out.addProperty("selection_operation",name.endsWith("RemoveCardReward")?"remove":name.endsWith("TransformCardReward")?"transform":name.endsWith("UpgradeCardReward")?"upgrade":"boss_relic");
            out.addProperty("selection_count",1);
            // Native claim opens GRID; selecting/confirming is a separate, newly observed action.
            out.addProperty("choice_details_available_after_claim",true);
        } else {
            // Generated does not mean visible: native CARD_REWARD reveals choices only after claim.
            out.addProperty("choice_details_available_after_claim",true);
        }
        return out;
    }
    public static void click(RewardItem item) {
        if(!RoomUi.ready() || !claimable(item))throw new IllegalArgumentException("Collector reward changed");
        NativeUiInput.click(item.hb,item::update);
    }
    private static int essence(RewardItem item) {
        // Fixed optional binding, reached only after an exact supported reward-class check.
        try{return (Integer)Class.forName("collector.util.EssenceSystem",false,item.getClass().getClassLoader()).getMethod("essenceCount").invoke(null);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Collector essence binding unavailable",failure);}
    }
}
