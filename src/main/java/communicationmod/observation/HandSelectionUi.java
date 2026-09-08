package communicationmod.observation;

import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.PeekButton;
import communicationmod.ChoiceScreenUtils;
import communicationmod.GameStateConverter;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Opt-in hand selector. Keeps the installed Hermit selection patches in the input path. */
public final class HandSelectionUi {
    private static Hitbox deselecting;
    public List<ProtocolSession.Action> capture(JsonObject view,JsonObject status,String decision) {
        List<ProtocolSession.Action> result=new ArrayList<>();
        HandCardSelectScreen owner=AbstractDungeon.handCardSelectScreen;
        if(!valid(owner)){status.addProperty("reason","hand_selection_transition_or_unsupported");return result;}
        JsonObject info=new JsonObject();view.add("selection_controls",info);
        info.addProperty("kind","hand");
        info.addProperty("prompt",GameStateConverter.removeTextFormatting((String)field(owner,"message")));
        info.addProperty("reason",GameStateConverter.removeTextFormatting(owner.selectionReason));
        info.addProperty("maximum",owner.numCardsToSelect);
        info.addProperty("selected_count",owner.selectedCards.size());
        info.addProperty("up_to",owner.upTo || (Boolean)field(owner,"anyNumber"));
        info.addProperty("can_pick_zero",owner.canPickZero);
        info.addProperty("cancel_available",false);
        if(HandSelectionPolicy.canSelect(owner.numCardsToSelect,owner.selectedCards.size())) {
            for(AbstractCard card:AbstractDungeon.player.hand.group) {
                result.add(action("run.hand.select."+card.uuid,card.name,owner,decision,()->{
                    int index=AbstractDungeon.player.hand.group.indexOf(card);
                    if(index<0 || !HandSelectionPolicy.canSelect(owner.numCardsToSelect,owner.selectedCards.size()))throw new IllegalArgumentException("Hand selection changed");
                    claim(decision);boolean click=InputHelper.justClickedLeft;
                    try{ChoiceScreenUtils.makeHandSelectScreenChoice(index);}finally{InputHelper.justClickedLeft=click;}
                }));
            }
        }
        for(AbstractCard card:owner.selectedCards.group) {
            result.add(action("run.hand.deselect."+card.uuid,card.name,owner,decision,()->{
                if(!owner.selectedCards.group.contains(card) || deselecting!=null)throw new IllegalArgumentException("Selected card changed");
                claim(decision);
                Map<Hitbox,Boolean> hovered=new IdentityHashMap<>();
                for(AbstractCard c:owner.selectedCards.group)hovered.put(c.hb,c.hb.hovered);
                boolean click=InputHelper.justClickedLeft;deselecting=card.hb;
                try{InputHelper.justClickedLeft=true;invoke(owner,"updateSelectedCards");}
                finally{deselecting=null;InputHelper.justClickedLeft=click;for(Map.Entry<Hitbox,Boolean> e:hovered.entrySet())e.getKey().hovered=e.getValue();}
            }));
        }
        if(confirmable(owner))result.add(action("run.hand.confirm",(String)field(owner.button,"buttonText"),owner,decision,()->{
            if(!confirmable(owner))throw new IllegalArgumentException("Confirmation changed");
            claim(decision);owner.button.hb.clicked=true;
            // The normal next frame consumes the click and preserves all installed update patches.
        }));
        return result;
    }
    private static ProtocolSession.Action action(String id,String label,HandCardSelectScreen owner,String decision,Runnable effect) {
        return new ProtocolSession.Action(id,label,new JsonObject(),args->{if(!args.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},args->{
            if(!Boolean.getBoolean("communicationmod.play_control") || owner!=AbstractDungeon.handCardSelectScreen || !valid(owner))throw new IllegalArgumentException("Hand screen changed");
            if(decision!=null)CombatObservation.validate(decision,"selection");effect.run();
        });
    }
    private static void claim(String decision){if(decision!=null)CombatObservation.claim(decision,"selection");}
    private static boolean confirmable(HandCardSelectScreen s) {
        return !s.button.isDisabled && !(Boolean)field(s.button,"isHidden") && !s.button.hb.clicked
            && HandSelectionPolicy.canConfirm(s.numCardsToSelect,s.selectedCards.size(),s.upTo || (Boolean)field(s,"anyNumber"),s.canPickZero);
    }
    private static boolean valid(HandCardSelectScreen s) {
        if(s==null || s.getClass()!=HandCardSelectScreen.class || Settings.isTouchScreen || Settings.isControllerMode || PeekButton.isPeeking
            || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.HAND_SELECT || (Boolean)field(s,"waitThenClose") || s.wereCardsRetrieved || s.button.hb.clicked)return false;
        for(AbstractCard c:AbstractDungeon.player.hand.group)if(!settled(c))return false;
        for(AbstractCard c:s.selectedCards.group)if(!settled(c))return false;
        return true;
    }
    private static boolean settled(AbstractCard c){return Math.abs(c.current_x-c.target_x)<0.5f && Math.abs(c.current_y-c.target_y)<0.5f;}
    public static void hover(Hitbox box){if(Boolean.getBoolean("communicationmod.play_control") && deselecting!=null)box.hovered=box==deselecting;}
    private static Object field(Object owner,String name){try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported hand field "+name,e);}}
    private static void invoke(Object owner,String name){try{java.lang.reflect.Method m=HandCardSelectScreen.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException("Hand input failed",e);}}
}
