package communicationmod.observation;

import com.google.gson.Gson;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import java.lang.reflect.Field;
import java.util.*;

/** Game-thread card previews. Does not choose cards, run events or mutate native preview objects. */
public final class CardUpgradeObservation {
    private static final int LIMIT=256;
    private static final GameAdapter ADAPTER=new GameAdapter();
    private static UpgradePreview<AbstractCard> previews=new UpgradePreview<>(LIMIT);
    private static Object owner;
    private static final List<Candidate> candidates=new ArrayList<>();
    private static final class Candidate {
        final AbstractCard card;final Map<String,Object> json;
        Candidate(AbstractCard card,Map<String,Object> json){this.card=card;this.json=json;}
    }
    private CardUpgradeObservation() { }

    public static void begin() { candidates.clear(); }
    public static void offer(Map<String,Object> json,AbstractCard card) {
        if(candidates.size()<LIMIT)candidates.add(new Candidate(card,json));
        else json.put("upgrade_preview",UpgradePreview.unavailable("decision_preview_budget_exceeded"));
    }
    public static void finish(Map<String,Object> state) {
        if(owner!=AbstractDungeon.player){owner=AbstractDungeon.player;previews=new UpgradePreview<>(LIMIT);}
        // Include all existing public decision input, including deck, relic counters and
        // event state. Animation/history and the unrelated drawn map are not card inputs.
        Map<String,Object> context=new LinkedHashMap<>(state);
        context.remove("narrative");context.remove("map");context.remove("map_plan");
        String key=new Gson().toJson(context);
        for(Candidate candidate:candidates)
            candidate.json.put("upgrade_preview",previews.describe(candidate.card,key,ADAPTER));
        candidates.clear();
    }

    public static AbstractCard eventCard(LargeDialogOptionButton button) {
        Object preview=field(button,"cardToPreview");return preview instanceof AbstractCard?(AbstractCard)preview:null;
    }

    /** The game has already produced these copies. Read them; never generate a branch here. */
    public static Map<String,Object> nativeGridPreview(GridCardSelectScreen screen) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("scope","native_upgrade_confirmation");
        result.put("source","game_ui_preview");
        Object source=field(screen,"hoveredCard");
        if(!screen.confirmScreenUp || !(source instanceof AbstractCard)
            || screen.targetGroup==null || !screen.targetGroup.group.contains(source)
            || !ADAPTER.visible((AbstractCard)source)) {
            result.put("status","not_displayed");return result;
        }
        AbstractCard card=(AbstractCard)source;
        if(hasInterface(card.getClass(),"MultiUpgradeCard")) {
            Map<String,Object> tree=NativeUpgradeTree.capture(card);
            boolean waiting=Boolean.TRUE.equals(optionalInjectedField(screen,"waitingForUpgradeSelection"));
            tree.put("choice_required",waiting);
            if(!waiting)tree.put("selected_result_location","selection_controls.upgrade_choice.selected_after");
            return tree;
        }
        if(screen.upgradePreviewCard==null){result.put("status","not_displayed");return result;}
        result.put("status","displayed");result.put("source_uuid",card.uuid==null?null:card.uuid.toString());
        result.put("before",snapshot(card));result.put("after",snapshot(screen.upgradePreviewCard));
        List<Object> alternatives=new ArrayList<>();
        boolean branching=hasInterface(card.getClass(),"BranchingUpgradesCard")
            && Boolean.TRUE.equals(optionalInjectedField(screen,"waitingForBranchUpgradeSelection"));
        Object branch=optionalInjectedField(screen,"branchUpgradePreviewCard");
        if(hasInterface(card.getClass(),"BranchingUpgradesCard") && !branching
            && Boolean.TRUE.equals(optionalInjectedField(screen,"isBranchUpgrading"))) {
            if(branch instanceof AbstractCard)result.put("after",snapshot((AbstractCard)branch));
            else {result.remove("after");result.put("status","unavailable");result.put("reason","selected_branch_preview_missing");}
        }
        if(branching && branch instanceof AbstractCard) {
            Map<String,Object> item=new LinkedHashMap<>();item.put("kind","branch");item.put("card",snapshot((AbstractCard)branch));alternatives.add(item);
        }
        result.put("choice_required",branching);
        result.put("alternatives",alternatives);result.put("alternatives_complete",!branching || !alternatives.isEmpty());
        result.put("event_effects_predicted",false);
        return result;
    }

    private static Object field(Object object,String name) {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
            catch(NoSuchFieldException inherited){continue;}
            catch(IllegalAccessException | RuntimeException unavailable){return null;}
        }
        return null;
    }
    private static Object optionalInjectedField(Object object,String name) {
        // The prepackaged build suffixes injected field names. Ordinary MTS uses the
        // same prefix. Do not hard-code a suffix or initialize an optional mod class.
        for(Field f:object.getClass().getFields())if(f.getName().equals(name) || f.getName().startsWith(name+"_")) {
            try{return f.get(object);}catch(IllegalAccessException unavailable){return null;}
        }
        return null;
    }
    private static boolean hasInterface(Class<?> type,String simpleName) {
        if(type==null)return false;
        if(type.getName().equals("com.evacipated.cardcrawl.mod.stslib.cards.interfaces."+simpleName))return true;
        for(Class<?> parent:type.getInterfaces())if(hasInterface(parent,simpleName))return true;
        return hasInterface(type.getSuperclass(),simpleName);
    }

    static Map<String,Object> snapshot(AbstractCard card) {
        Map<String,Object> result=new LinkedHashMap<>();
        if(!ADAPTER.visible(card)){result.put("status","card_not_visible");return result;}
        result.put("id",card.cardID);result.put("name",card.name);result.put("upgrades",card.timesUpgraded);
        result.put("misc",card.misc);result.put("base_cost",card.cost);result.put("base_damage",card.baseDamage);
        result.put("base_block",card.baseBlock);result.put("base_magic_number",card.baseMagicNumber);
        result.put("exhausts",card.exhaust);result.put("ethereal",card.isEthereal);result.put("innate",card.isInnate);
        result.put("self_retain",card.selfRetain);result.put("retain",card.retain);
        result.put("target",card.target==null?null:card.target.name());
        // No UUID: a newly generated preview identity must not churn protocol state IDs.
        CardObservation.addTo(result,card);return result;
    }

    static final class GameAdapter implements UpgradePreview.Adapter<AbstractCard> {
        public boolean visible(AbstractCard card){return card!=null && card.isSeen && !card.isLocked && !card.isFlipped;}
        public String unavailableReason(AbstractCard card) {
            if(hasInterface(card.getClass(),"BranchingUpgradesCard") || hasInterface(card.getClass(),"MultiUpgradeCard"))return "upgrade_choice_required_use_native_preview";
            // The game's stat-equivalent copy replays prior upgrades. Bound that work.
            if(card.timesUpgraded<0 || card.timesUpgraded>256)return "upgrade_copy_replay_budget_exceeded";
            return null;
        }
        public Map<String,Object> snapshot(AbstractCard card){return CardUpgradeObservation.snapshot(card);}
        public boolean canUpgrade(AbstractCard card){return card.canUpgrade();}
        public AbstractCard copy(AbstractCard card) {
            AbstractCard copy=card.makeStatEquivalentCopy();
            if(copy!=null && copy!=card && copy.getClass()!=card.getClass())return null;
            return copy;
        }
        public void upgrade(AbstractCard copy) {
            copy.upgrade();
            // Native confirmation uses displayUpgrades. Rebuild only the detached copy's
            // cached description, so changed text/keywords are not the old UI snapshot.
            copy.displayUpgrades();copy.initializeDescription();
        }
    }
}
