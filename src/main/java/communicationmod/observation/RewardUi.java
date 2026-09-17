package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.ui.buttons.*;
import communicationmod.ChoiceScreenUtils;
import communicationmod.GameStateConverter;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Standard reward UI only, within the copied opt-in play profile. */
public final class RewardUi {
    private final List<ProtocolSession.Action> offered=new ArrayList<>();
    private static AbstractCard selecting;
    private String decision;
    public List<ProtocolSession.Action> actions(){return new ArrayList<>(offered);}
    public void capture(JsonObject view,JsonObject status) {
        capture(view,status,null);
    }
    public void capture(JsonObject view,JsonObject status,String decision) {
        this.decision=decision;
        offered.clear();enabled();
        if(AbstractDungeon.screen==AbstractDungeon.CurrentScreen.COMBAT_REWARD)rewards(view,status);
        else if(AbstractDungeon.screen==AbstractDungeon.CurrentScreen.CARD_REWARD)cards(view,status);
    }
    private void rewards(JsonObject view,JsonObject status) {
        Object owner=AbstractDungeon.combatRewardScreen;
        if((Float)field(owner,"rewardAnimTimer")>0){status.addProperty("reason","reward_animation");return;}
        JsonArray rows=new JsonArray();view.add("reward_controls",rows);
        List<RewardItem> items=AbstractDungeon.combatRewardScreen.rewards;
        for(int i=0;i<items.size();i++) {
            final int index=i;RewardItem item=items.get(i);
            boolean supported=RewardPolicy.claimable(item.type.name(),item.getClass()==RewardItem.class,false,false,true);
            String id="run.reward."+i,label=GameStateConverter.removeTextFormatting(item.text);
            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("label",label);
            row.addProperty("supported",supported);row.addProperty("pending",item.isDone);row.addProperty("ignored",item.ignoreReward);rows.add(row);
            row.addProperty("claimable",claimable(item));
            if(item.type==RewardItem.RewardType.POTION && !potionSlot())row.addProperty("unavailable_reason","potion_slots_full_or_sozu");
            if(item.relicLink!=null){int linked=items.indexOf(item.relicLink);if(linked>=0)row.addProperty("mutually_exclusive_with","run.reward."+linked);}
            if(claimable(item))add(id,label,()->{
                check(AbstractDungeon.CurrentScreen.COMBAT_REWARD,owner,AbstractDungeon.combatRewardScreen);
                List<RewardItem> fresh=AbstractDungeon.combatRewardScreen.rewards;
                if(index>=fresh.size() || fresh.get(index)!=item || !claimable(item))throw new IllegalArgumentException("Reward changed");
                ChoiceScreenUtils.makeCombatRewardChoice(index);
            });
        }
        // The installed handler owns elite, boss, event and unclaimed-reward transitions.
        ProceedButton button=AbstractDungeon.overlayMenu.proceedButton;
        if(proceedAvailable(button)) {
            JsonObject navigation=new JsonObject();navigation.addProperty("unclaimed_rewards",items.size());
            navigation.addProperty("may_leave_unclaimed_rewards",!items.isEmpty());view.add("reward_navigation",navigation);
            add("run.reward.proceed",(String)field(button,"label"),()->{
                check(AbstractDungeon.CurrentScreen.COMBAT_REWARD,owner,AbstractDungeon.combatRewardScreen);
                if(!proceedAvailable(button))throw new IllegalArgumentException("Rewards changed");
                com.megacrit.cardcrawl.helpers.Hitbox hb=(com.megacrit.cardcrawl.helpers.Hitbox)field(button,"hb");
                NativeUiInput.click(hb,()->button.update());
            });
        }
        if(offered.isEmpty())status.addProperty("reason","unsupported_or_pending_reward");
    }
    private static boolean claimable(RewardItem item){return RewardPolicy.claimable(item.type.name(),item.getClass()==RewardItem.class,item.isDone,item.ignoreReward,potionSlot());}
    private static boolean potionSlot(){
        if(AbstractDungeon.player.hasRelic("Sozu"))return false;
        for(com.megacrit.cardcrawl.potions.AbstractPotion potion:AbstractDungeon.player.potions)
            if(potion instanceof com.megacrit.cardcrawl.potions.PotionSlot)return true;
        return false;
    }
    private void cards(JsonObject view,JsonObject status) {
        CardRewardScreen owner=AbstractDungeon.cardRewardScreen;
        if(!cardMode(owner)) {
            status.addProperty("reason","unsupported_card_reward_mode");return;
        }
        for(AbstractCard card:owner.rewardGroup)if(Math.abs(card.current_x-card.target_x)>0.5f || Math.abs(card.current_y-card.target_y)>0.5f) {
            status.addProperty("reason","card_reward_animation");return;
        }
        view.addProperty("card_reward_header",GameStateConverter.removeTextFormatting((String)field(owner,"header")));
        JsonObject controls=new JsonObject();controls.addProperty("candidate_count",owner.rewardGroup.size());
        controls.addProperty("kind",cardKind(owner));controls.addProperty("acquires_into_deck",cardKind(owner).equals("reward") || cardKind(owner).equals("draft"));
        view.add("card_selection_controls",controls);
        for(AbstractCard card:owner.rewardGroup)add("run.card_reward."+card.uuid,card.name,()->{
            check(AbstractDungeon.CurrentScreen.CARD_REWARD,owner,AbstractDungeon.cardRewardScreen);
            if(!cardMode(owner) || !owner.rewardGroup.contains(card) || selecting!=null)throw new IllegalArgumentException("Card reward changed");
            Map<AbstractCard,Boolean> hovered=new IdentityHashMap<>();for(AbstractCard c:owner.rewardGroup)hovered.put(c,c.hb.hovered);
            selecting=card;
            try{NativeUiInput.click(card.hb,()->NativeUiInput.invoke(owner,"cardSelectUpdate"));}
            finally{selecting=null;card.hb.clicked=false;for(Map.Entry<AbstractCard,Boolean> entry:hovered.entrySet())entry.getKey().hb.hovered=entry.getValue();}
        });
        SkipCardButton skip=(SkipCardButton)field(owner,"skipButton");
        if(!skip.screenDisabled && visible(skip))add("run.card_reward.skip",SkipCardButton.TEXT[0],()->{
            check(AbstractDungeon.CurrentScreen.CARD_REWARD,owner,AbstractDungeon.cardRewardScreen);
            if(skip.screenDisabled || !visible(skip))throw new IllegalArgumentException("Skip changed");
            NativeUiInput.click(skip.hb,()->skip.update());
        });
        SingingBowlButton bowl=(SingingBowlButton)field(owner,"bowlButton");
        if(visible(bowl))add("run.card_reward.bowl",SingingBowlButton.TEXT[0],()->{
            check(AbstractDungeon.CurrentScreen.CARD_REWARD,owner,AbstractDungeon.cardRewardScreen);
            if(!visible(bowl))throw new IllegalArgumentException("Bowl changed");
            NativeUiInput.click(bowl.hb,()->bowl.update());
        });
    }
    /** Called after the original hover update, only within synchronous card selection. */
    public static void hover(AbstractCard card){if(Boolean.getBoolean("communicationmod.play_control") && selecting!=null)card.hb.hovered=card==selecting;}
    private void add(String id,String label,Runnable effect) {
        final String expected=decision;
        offered.add(new ProtocolSession.Action(id,label,new JsonObject(),a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},a->{
            enabled();if(expected!=null)CombatObservation.claim(expected,"selection");
            else if(CombatObservation.inCombat())throw new IllegalArgumentException("Combat selection token missing");
            effect.run();
        }));
    }
    private static boolean cardMode(CardRewardScreen owner){return RunUiPolicy.cardReward(owner.getClass()==CardRewardScreen.class,Settings.isTouchScreen || Settings.isControllerMode,(Boolean)field(owner,"isVoting"),owner.rewardGroup.size());}
    private static String cardKind(CardRewardScreen owner){for(String name:new String[]{"draft","discovery","chooseOne","codex"})if((Boolean)field(owner,name))return name;return "reward";}
    private static boolean proceedAvailable(ProceedButton button){return RunUiPolicy.proceed(visible(button),((com.megacrit.cardcrawl.helpers.Hitbox)field(button,"hb")).clicked,(Float)field(AbstractDungeon.combatRewardScreen,"rewardAnimTimer")>0);}
    private static void check(AbstractDungeon.CurrentScreen screen,Object expected,Object actual){enabled();if(AbstractDungeon.screen!=screen || expected!=actual)throw new IllegalArgumentException("Reward screen changed");}
    private static boolean visible(Object button){return !(Boolean)field(button,"isHidden") && Math.abs((Float)field(button,"current_x")-(Float)field(button,"target_x"))<0.5f;}
    private static Object field(Object owner,String name){try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported reward UI field: "+name,e);}}
    private static void enabled(){if(!Boolean.getBoolean("communicationmod.play_control"))throw new IllegalStateException("Local play control not enabled");}
}
