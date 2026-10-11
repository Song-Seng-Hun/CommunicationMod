package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import communicationmod.ChoiceScreenUtils;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Base/evil public boards and scoped native clicks; compatibility name retained. No concealed identities. */
public final class EvilMatchUi {
    private static final String BASE_TYPE="com.megacrit.cardcrawl.events.shrines.GremlinMatchGame";
    private static final String TYPE="downfall.events.GremlinMatchGame_Evil";
    private AbstractEvent authorized;
    private Object room;
    private final Map<AbstractCard,Integer> positions=new IdentityHashMap<>();
    public static boolean isIntro(AbstractEvent event,int choice) {
        return choice==0 && supported(event) && "RULE_EXPLANATION".equals(String.valueOf(NativeUiInput.field(event,"screen")));
    }
    /** The current intro choice was claimed; native buttonEffect will place these existing cards. */
    public void acceptedIntro(AbstractEvent event) {
        if(!isIntro(event,0) || AbstractDungeon.getCurrRoom().event!=event)throw new IllegalArgumentException("Match intro changed");
        List<AbstractCard> cards=board(event);
        if(cards.size()!=12 || cards.contains(null) || new HashSet<>(cards).size()!=cards.size())throw new IllegalStateException("Unsupported match layout");
        authorized=event;room=AbstractDungeon.getCurrRoom();positions.clear();
        for(int i=0;i<cards.size();i++)positions.put(cards.get(i),i);
    }
    public List<ProtocolSession.Action> capture(JsonObject view,JsonObject status) {
        AbstractEvent event=AbstractDungeon.getCurrRoom().event;
        if(event!=authorized || room!=AbstractDungeon.getCurrRoom()){authorized=null;room=null;positions.clear();}
        if(!supported(event) || !"PLAY".equals(String.valueOf(NativeUiInput.field(event,"screen"))))return Collections.emptyList();
        if(event!=authorized){status.addProperty("reason","match_intro_not_acknowledged");return Collections.emptyList();}
        List<AbstractCard> current=board(event);
        if(current.isEmpty() || current.size()>12 || current.contains(null) || !positions.keySet().containsAll(current)) {
            status.addProperty("reason","match_board_changed_or_unobserved");return Collections.emptyList();
        }
        List<AbstractCard> snapshot=new ArrayList<>(current);boolean[] flipped=new boolean[snapshot.size()];
        int attempts=(Integer)NativeUiInput.field(event,"attemptCount");
        Object chosen=NativeUiInput.field(event,"chosenCard"),pair=NativeUiInput.field(event,"cardFlipped");
        boolean ready=ready(event);boolean[] consumed={false};
        JsonObject controls=new JsonObject();controls.addProperty("flip_available",ready);controls.addProperty("attempts_remaining",attempts);
        JsonArray rows=new JsonArray();controls.add("cards",rows);view.add("match_controls",controls);
        List<ProtocolSession.Action> actions=new ArrayList<>();
        for(int i=0;i<snapshot.size();i++) {
            AbstractCard card=snapshot.get(i);flipped[i]=card.isFlipped;int position=positions.get(card);
            JsonObject row=new JsonObject();row.addProperty("position",position);
            // Pinned placeCards layout uses i%3 for rows and i%4 for columns, not list indices after removal.
            row.addProperty("row",position%3);row.addProperty("column",position%4);row.addProperty("face_up",!card.isFlipped);
            boolean available=ready && card.isFlipped && card.hb!=null && !card.hb.clicked && !card.hb.clickStarted;
            row.addProperty("available",available);rows.add(row);
            if(!card.isFlipped)row.add("card",new Gson().toJsonTree(CardUpgradeObservation.snapshot(card)));
            if(!available)continue;
            String id="run.match.flip."+position;row.addProperty("action_id",id);
            final com.megacrit.cardcrawl.helpers.Hitbox hitbox=card.hb;
            actions.add(new ProtocolSession.Action(id,"Flip position "+position,new JsonObject(),EvilMatchUi::empty,args->{
                empty(args);
                if(consumed[0] || !ready(event) || !sameBoard(event,snapshot,flipped)
                    || !Integer.valueOf(attempts).equals(NativeUiInput.field(event,"attemptCount"))
                    || chosen!=NativeUiInput.field(event,"chosenCard") || !pair.equals(NativeUiInput.field(event,"cardFlipped"))
                    || card.hb!=hitbox || !card.isFlipped || hitbox.clicked || hitbox.clickStarted)throw new IllegalArgumentException("Match board changed");
                consumed[0]=true;
                NativeUiInput.press(hitbox,()->NativeUiInput.invoke(event,"updateMatchGameLogic"));
            }));
        }
        if(!ready)status.addProperty("reason","match_input_pending_or_animation");
        return actions;
    }
    private boolean ready(AbstractEvent event) {
        return event==authorized && supported(event) && room==AbstractDungeon.getCurrRoom() && AbstractDungeon.getCurrRoom().event==event
            && RoomUi.ready() && !NativeUiInput.pending() && !AbstractDungeon.isScreenUp && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.NONE
            && ChoiceScreenUtils.getEventDialogType()==ChoiceScreenUtils.EventDialogType.NONE
            && "PLAY".equals(String.valueOf(NativeUiInput.field(event,"screen"))) && !(Boolean)NativeUiInput.field(event,"gameDone")
            && (Float)NativeUiInput.field(event,"waitTimer")==0f && (Integer)NativeUiInput.field(event,"attemptCount")>0;
    }
    private static boolean sameBoard(AbstractEvent event,List<AbstractCard> before,boolean[] flipped) {
        List<AbstractCard> current=board(event);if(current.size()!=before.size())return false;
        for(int i=0;i<before.size();i++)if(current.get(i)!=before.get(i) || before.get(i).isFlipped!=flipped[i])return false;
        return true;
    }
    private static List<AbstractCard> board(AbstractEvent event){return ((CardGroup)NativeUiInput.field(event,"cards")).group;}
    private static boolean supported(AbstractEvent event){return event!=null && (BASE_TYPE.equals(event.getClass().getName()) || TYPE.equals(event.getClass().getName()));}
    private static void empty(JsonObject args){if(args==null || !args.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");}
}
