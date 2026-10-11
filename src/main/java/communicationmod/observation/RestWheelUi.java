package communicationmod.observation;

import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.helpers.Hitbox;
import communicationmod.ChoiceScreenUtils;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Base/rest/evil wheels share scoped native input; compatibility class name retained. */
public final class RestWheelUi {
    private static final String BASE_TYPE="com.megacrit.cardcrawl.events.shrines.GremlinWheelGame";
    private static final String TYPE="downfall.events.GremlinWheelGame_Rest";
    private static final String EVIL_TYPE="downfall.events.GremlinWheelGame_Evil";
    private AbstractEvent authorized;
    private Object room;
    public static boolean isIntro(AbstractEvent event,int choice) {
        return choice==0 && supported(event) && "INTRO".equals(String.valueOf(NativeUiInput.field(event,"screen")));
    }
    /** Called only after ChoiceScreenUtils.makeEventChoice has successfully claimed the acknowledged page. */
    public void acceptedIntro(AbstractEvent event) {
        if(!supported(event) || AbstractDungeon.getCurrRoom().event!=event)throw new IllegalArgumentException("Wheel event changed");
        authorized=event;room=AbstractDungeon.getCurrRoom();
    }
    public List<ProtocolSession.Action> capture(JsonObject view,JsonObject status) {
        AbstractEvent event=AbstractDungeon.getCurrRoom().event;
        if(event!=authorized || room!=AbstractDungeon.getCurrRoom()){authorized=null;room=null;}
        if(!supported(event) || !(Boolean)NativeUiInput.field(event,"startSpin") || (Boolean)NativeUiInput.field(event,"doneSpinning"))return Collections.emptyList();
        boolean ready=ready(event);JsonObject controls=new JsonObject();view.add("wheel_controls",controls);
        controls.addProperty("spin_available",ready);
        if(!ready){status.addProperty("reason",authorized==null?"wheel_intro_not_acknowledged":"wheel_input_pending_or_animation");return Collections.emptyList();}
        Hitbox hb=(Hitbox)NativeUiInput.field(event,"buttonHb");
        return Collections.singletonList(new ProtocolSession.Action("run.wheel.spin","Spin",new JsonObject(),
            a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},a->{
                if(!a.entrySet().isEmpty() || !ready(event) || NativeUiInput.field(event,"buttonHb")!=hb)throw new IllegalArgumentException("Wheel changed");
                NativeUiInput.press(hb,event::update);
                authorized=null;room=null;
            }));
    }
    private boolean ready(AbstractEvent event) {
        if(!supported(event) || event!=authorized || AbstractDungeon.getCurrRoom()!=room || AbstractDungeon.getCurrRoom().event!=event
            || !RoomUi.ready() || AbstractDungeon.isScreenUp || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.NONE
            || ChoiceScreenUtils.getEventDialogType()!=ChoiceScreenUtils.EventDialogType.NONE)return false;
        Hitbox hb=(Hitbox)NativeUiInput.field(event,"buttonHb");
        return (Boolean)NativeUiInput.field(event,"startSpin") && !(Boolean)NativeUiInput.field(event,"buttonPressed")
            && !(Boolean)NativeUiInput.field(event,"doneSpinning") && (Float)NativeUiInput.field(event,"bounceTimer")==0f
            && hb!=null && !hb.clicked && !hb.clickStarted;
    }
    private static boolean supported(AbstractEvent event){return event!=null && (BASE_TYPE.equals(event.getClass().getName()) || TYPE.equals(event.getClass().getName()) || EVIL_TYPE.equals(event.getClass().getName()));}
}
