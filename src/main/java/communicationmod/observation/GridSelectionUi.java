package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.PeekButton;
import communicationmod.GameStateConverter;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Native grid selection and confirmation. The source card is never upgraded by this adapter. */
public final class GridSelectionUi {
    private static AbstractCard selecting;
    public List<ProtocolSession.Action> capture(JsonObject view,JsonObject status,String decision) {
        List<ProtocolSession.Action> actions=new ArrayList<>();GridCardSelectScreen owner=AbstractDungeon.gridSelectScreen;
        if(!valid(owner)){status.addProperty("reason","grid_selection_transition_or_unsupported");return actions;}
        JsonObject controls=new JsonObject();view.add("selection_controls",controls);
        controls.addProperty("kind","grid");controls.addProperty("prompt",GameStateConverter.removeTextFormatting((String)NativeUiInput.field(owner,"tipMsg")));
        controls.addProperty("maximum",(Integer)NativeUiInput.field(owner,"numCards"));controls.addProperty("selected_count",owner.selectedCards.size());
        controls.addProperty("any_number",owner.anyNumber);controls.addProperty("confirm_up",owner.confirmScreenUp || owner.isJustForConfirming);
        final String key=key(owner);
        if(!owner.confirmScreenUp && !owner.isJustForConfirming)for(AbstractCard card:owner.targetGroup.group) {
            if(!RoomUi.settled(card))continue;
            String op=owner.selectedCards.contains(card)?"deselect":"select";
            actions.add(RoomUi.action("run.grid."+op+"."+card.uuid,card.name,decision,"selection",()->valid(owner) && key(owner).equals(key) && owner.targetGroup.group.contains(card) && RoomUi.settled(card),()->{
                if(selecting!=null)throw new IllegalStateException("Grid input already pending");
                selecting=card;try{NativeUiInput.click(card.hb,()->owner.update());}finally{selecting=null;}
            }));
        }
        boolean extensionWaiting=Boolean.TRUE.equals(injected(owner,"waitingForUpgradeSelection")) || Boolean.TRUE.equals(injected(owner,"waitingForBranchUpgradeSelection"));
        actions.addAll(UpgradeChoiceUi.capture(owner,controls,decision));
        if(extensionWaiting)controls.addProperty("unavailable_reason","native_upgrade_choice_required");
        controls.addProperty("confirm_available",confirmable(owner) && !extensionWaiting);
        if(confirmable(owner) && !extensionWaiting)actions.add(RoomUi.action("run.grid.confirm",(String)NativeUiInput.field(owner.confirmButton,"buttonText"),decision,"selection",()->valid(owner) && key(owner).equals(key) && confirmable(owner) && !Boolean.TRUE.equals(injected(owner,"waitingForUpgradeSelection")) && !Boolean.TRUE.equals(injected(owner,"waitingForBranchUpgradeSelection")),
            ()->NativeUiInput.click(owner.confirmButton.hb,()->owner.update())));
        boolean cancel=owner.confirmScreenUp || (Boolean)NativeUiInput.field(owner,"canCancel");
        controls.addProperty("cancel_available",cancel && NativeUiInput.visible(AbstractDungeon.overlayMenu.cancelButton));
        if(cancel)RoomUi.cancel(actions,"run.grid.cancel",decision,"selection",()->valid(owner) && key(owner).equals(key));
        if(actions.isEmpty())status.addProperty("reason","grid_controls_pending_or_extension_choice");
        return actions;
    }
    /** Applied after the existing (already modded) native hover calculation, only for a requested grid card. */
    public static void hover(GridCardSelectScreen owner) {
        if(selecting==null || AbstractDungeon.gridSelectScreen!=owner || !owner.targetGroup.group.contains(selecting))return;
        try{java.lang.reflect.Field field=GridCardSelectScreen.class.getDeclaredField("hoveredCard");field.setAccessible(true);field.set(owner,selecting);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Native grid hover unavailable",failure);}
        NativeUiInput.afterHitbox(selecting.hb);
    }
    private static boolean valid(GridCardSelectScreen owner){return owner!=null && owner==AbstractDungeon.gridSelectScreen && owner.getClass()==GridCardSelectScreen.class
        && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID && !PeekButton.isPeeking && owner.targetGroup!=null && owner.targetGroup.size()<=1000
        && !owner.confirmButton.hb.clicked && !(Boolean)NativeUiInput.field(owner,"grabbedScreen");}
    private static boolean confirmable(GridCardSelectScreen owner){return !owner.confirmButton.isDisabled && NativeUiInput.visible(owner.confirmButton) && !owner.confirmButton.hb.clicked;}
    static Object injected(Object owner,String prefix){for(java.lang.reflect.Field f:owner.getClass().getFields())if(f.getName().startsWith(prefix+"_"))try{return f.get(owner);}catch(IllegalAccessException e){throw new IllegalStateException(e);}return null;}
    private static String key(GridCardSelectScreen owner){StringBuilder out=new StringBuilder();out.append(owner.confirmScreenUp).append('/').append(owner.isJustForConfirming).append('/').append(owner.anyNumber).append('/').append(NativeUiInput.field(owner,"numCards"));for(AbstractCard c:owner.targetGroup.group)out.append('/').append(c.uuid);out.append(":");for(AbstractCard c:owner.selectedCards)out.append('/').append(c.uuid);out.append('/').append(injected(owner,"waitingForUpgradeSelection")).append('/').append(injected(owner,"waitingForBranchUpgradeSelection"));return out.toString();}
}
