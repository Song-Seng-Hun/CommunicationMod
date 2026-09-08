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
    public List<ProtocolSession.Action> actions(){return new ArrayList<>(offered);}
    public void capture(JsonObject view,JsonObject status) {
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
            boolean supported=item.getClass()==RewardItem.class && (item.type==RewardItem.RewardType.GOLD || item.type==RewardItem.RewardType.CARD);
            String id="run.reward."+i,label=GameStateConverter.removeTextFormatting(item.text);
            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("label",label);
            row.addProperty("supported",supported);row.addProperty("pending",item.isDone);row.addProperty("ignored",item.ignoreReward);rows.add(row);
            if(supported && !item.isDone && !item.ignoreReward)add(id,label,()->{
                check(AbstractDungeon.CurrentScreen.COMBAT_REWARD,owner,AbstractDungeon.combatRewardScreen);
                List<RewardItem> fresh=AbstractDungeon.combatRewardScreen.rewards;
                if(index>=fresh.size() || fresh.get(index)!=item || item.isDone || item.ignoreReward)throw new IllegalArgumentException("Reward changed");
                ChoiceScreenUtils.makeCombatRewardChoice(index);
            });
        }
        // Boss/event transitions have additional semantics and remain unsupported.
        ProceedButton button=AbstractDungeon.overlayMenu.proceedButton;
        if(items.isEmpty() && AbstractDungeon.getCurrRoom().getClass()==com.megacrit.cardcrawl.rooms.MonsterRoom.class && visible(button)) {
            add("run.reward.proceed",(String)field(button,"label"),()->{
                check(AbstractDungeon.CurrentScreen.COMBAT_REWARD,owner,AbstractDungeon.combatRewardScreen);
                if(!AbstractDungeon.combatRewardScreen.rewards.isEmpty() || !visible(button))throw new IllegalArgumentException("Rewards changed");
                com.megacrit.cardcrawl.helpers.Hitbox hb=(com.megacrit.cardcrawl.helpers.Hitbox)field(button,"hb");
                hb.clicked=true;try{button.update();}finally{hb.clicked=false;}
            });
        }
        if(offered.isEmpty())status.addProperty("reason","unsupported_or_pending_reward");
    }
    private void cards(JsonObject view,JsonObject status) {
        CardRewardScreen owner=AbstractDungeon.cardRewardScreen;
        if(Settings.isTouchScreen || owner.cardOnly || owner.rItem==null || owner.rewardGroup.size()>3) {
            status.addProperty("reason","unsupported_card_reward_mode");return;
        }
        for(String mode:new String[]{"draft","discovery","chooseOne","codex","isVoting"})if((Boolean)field(owner,mode)) {
            status.addProperty("reason","unsupported_card_reward_mode");return;
        }
        for(AbstractCard card:owner.rewardGroup)if(Math.abs(card.current_x-card.target_x)>0.5f || Math.abs(card.current_y-card.target_y)>0.5f) {
            status.addProperty("reason","card_reward_animation");return;
        }
        view.addProperty("card_reward_header",GameStateConverter.removeTextFormatting((String)field(owner,"header")));
        for(AbstractCard card:owner.rewardGroup)add("run.card_reward."+card.uuid,card.name,()->{
            check(AbstractDungeon.CurrentScreen.CARD_REWARD,owner,AbstractDungeon.cardRewardScreen);
            if(!owner.rewardGroup.contains(card) || selecting!=null)throw new IllegalArgumentException("Card reward changed");
            Map<AbstractCard,Boolean> hovered=new IdentityHashMap<>();for(AbstractCard c:owner.rewardGroup)hovered.put(c,c.hb.hovered);
            selecting=card;card.hb.clicked=true;
            try{java.lang.reflect.Method m=CardRewardScreen.class.getDeclaredMethod("cardSelectUpdate");m.setAccessible(true);m.invoke(owner);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Card selection handler failed",failure);}
            finally{selecting=null;card.hb.clicked=false;for(Map.Entry<AbstractCard,Boolean> entry:hovered.entrySet())entry.getKey().hb.hovered=entry.getValue();}
        });
        SkipCardButton skip=(SkipCardButton)field(owner,"skipButton");
        if(!skip.screenDisabled && visible(skip))add("run.card_reward.skip",SkipCardButton.TEXT[0],()->{
            check(AbstractDungeon.CurrentScreen.CARD_REWARD,owner,AbstractDungeon.cardRewardScreen);
            if(skip.screenDisabled || !visible(skip))throw new IllegalArgumentException("Skip changed");
            skip.hb.clicked=true;try{skip.update();}finally{skip.hb.clicked=false;}
        });
    }
    /** Called after the original hover update, only within synchronous card selection. */
    public static void hover(AbstractCard card){if(Boolean.getBoolean("communicationmod.play_control") && selecting!=null)card.hb.hovered=card==selecting;}
    private void add(String id,String label,Runnable effect) {
        offered.add(new ProtocolSession.Action(id,label,new JsonObject(),a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},a->{enabled();effect.run();}));
    }
    private static void check(AbstractDungeon.CurrentScreen screen,Object expected,Object actual){enabled();if(AbstractDungeon.screen!=screen || expected!=actual)throw new IllegalArgumentException("Reward screen changed");}
    private static boolean visible(Object button){return !(Boolean)field(button,"isHidden") && Math.abs((Float)field(button,"current_x")-(Float)field(button,"target_x"))<0.5f;}
    private static Object field(Object owner,String name){try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported reward UI field: "+name,e);}}
    private static void enabled(){if(!Boolean.getBoolean("communicationmod.play_control"))throw new IllegalStateException("Local play control not enabled");}
}
