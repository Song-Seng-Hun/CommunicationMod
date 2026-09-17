package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Choose only a preview the native branch/tree UI already created; confirmation remains separate. */
public final class UpgradeChoiceUi {
    private UpgradeChoiceUi() { }
    public static List<ProtocolSession.Action> capture(GridCardSelectScreen screen,JsonObject controls,String decision) {
        List<ProtocolSession.Action> actions=new ArrayList<>();
        if(!screen.forUpgrade || !screen.confirmScreenUp)return actions;
        Object source=NativeUiInput.field(screen,"hoveredCard");if(!(source instanceof AbstractCard))return actions;
        AbstractCard original=(AbstractCard)source;
        if(!screen.targetGroup.group.contains(original))return actions;
        JsonObject choice=new JsonObject();JsonArray candidates=new JsonArray();choice.add("candidates",candidates);
        if(implementsNamed(original.getClass(),"MultiUpgradeCard")) {
            Map<String,Object> tree=NativeUpgradeTree.capture(original);
            if(!"displayed".equals(tree.get("status")) || !Boolean.TRUE.equals(tree.get("complete")))return actions;
            choice.addProperty("kind","multi_upgrade");choice.addProperty("selected_upgrade_index",(Integer)GridSelectionUi.injected(screen,"chosenIndex"));
            choice.addProperty("choice_required",Boolean.TRUE.equals(GridSelectionUi.injected(screen,"waitingForUpgradeSelection")));
            for(Map.Entry<Integer,AbstractCard> entry:treeCandidates().entrySet()) {
                final int index=entry.getKey();AbstractCard card=entry.getValue();if(!RoomUi.settled(card))continue;
                String id="run.grid.upgrade."+index;candidate(candidates,id,"upgrade_"+index,card);
                actions.add(RoomUi.action(id,card.name+" ["+index+"]",decision,"selection",()->same(screen,original) && treeCandidates().get(index)==card && RoomUi.settled(card),
                    ()->NativeUiInput.press(card.hb,()->card.update())));
                if(Objects.equals(GridSelectionUi.injected(screen,"chosenIndex"),index))choice.add("selected_after",new Gson().toJsonTree(CardUpgradeObservation.snapshot(card)));
            }
        } else if(implementsNamed(original.getClass(),"BranchingUpgradesCard")) {
            Object branch=GridSelectionUi.injected(screen,"branchUpgradePreviewCard");
            if(!(branch instanceof AbstractCard) || screen.upgradePreviewCard==null)return actions;
            choice.addProperty("kind","branching_upgrade");choice.addProperty("choice_required",Boolean.TRUE.equals(GridSelectionUi.injected(screen,"waitingForBranchUpgradeSelection")));
            boolean selectedBranch=Boolean.TRUE.equals(GridSelectionUi.injected(screen,"isBranchUpgrading"));choice.addProperty("selected_kind",selectedBranch?"branch":"normal");
            for(boolean branching:new boolean[]{false,true}) {
                AbstractCard card=branching?(AbstractCard)branch:screen.upgradePreviewCard;if(!RoomUi.settled(card))continue;
                String kind=branching?"branch":"normal",id="run.grid.upgrade."+kind;candidate(candidates,id,kind,card);
                actions.add(RoomUi.action(id,kind+": "+card.name,decision,"selection",()->same(screen,original) && (branching?GridSelectionUi.injected(screen,"branchUpgradePreviewCard"):screen.upgradePreviewCard)==card && RoomUi.settled(card),
                    ()->NativeUiInput.press(card.hb,()->card.update())));
            }
            if(!Boolean.TRUE.equals(GridSelectionUi.injected(screen,"waitingForBranchUpgradeSelection")))choice.add("selected_after",new Gson().toJsonTree(CardUpgradeObservation.snapshot(selectedBranch?(AbstractCard)branch:screen.upgradePreviewCard)));
        } else return actions;
        controls.add("upgrade_choice",choice);return actions;
    }
    private static void candidate(JsonArray candidates,String id,String kind,AbstractCard card){JsonObject row=new JsonObject();row.addProperty("action_id",id);row.addProperty("kind",kind);row.add("after",new Gson().toJsonTree(CardUpgradeObservation.snapshot(card)));candidates.add(row);}
    private static boolean same(GridCardSelectScreen screen,AbstractCard original){return AbstractDungeon.gridSelectScreen==screen && screen.forUpgrade && screen.confirmScreenUp && NativeUiInput.field(screen,"hoveredCard")==original && screen.targetGroup.group.contains(original);}
    private static boolean implementsNamed(Class<?> type,String name){if(type==null)return false;for(Class<?> i:type.getInterfaces())if(i.getSimpleName().equals(name) || implementsNamed(i,name))return true;return implementsNamed(type.getSuperclass(),name);}
    private static Map<Integer,AbstractCard> treeCandidates() {
        Map<Integer,AbstractCard> out=new LinkedHashMap<>();
        try {
            Class<?> tree=Class.forName("com.evacipated.cardcrawl.mod.stslib.ui.MultiUpgradeTree",false,UpgradeChoiceUi.class.getClassLoader());
            Object main=tree.getField("mainCard").get(null),graph=tree.getField("cardGraph").get(null);
            List<?> cards=(List<?>)tree.getField("cardList").get(null),taken=(List<?>)tree.getField("takenList").get(null),locked=(List<?>)tree.getField("lockedList").get(null);
            List<?> vertices=(List<?>)graph.getClass().getField("vertices").get(graph);if(vertices.size()>64)return out;
            for(Object vertex:vertices) {
                Object value=vertex.getClass().getField("card").get(vertex);
                if(value instanceof AbstractCard && value!=main && cards.contains(value) && !taken.contains(value) && !locked.contains(value))out.put((Integer)vertex.getClass().getField("index").get(vertex),(AbstractCard)value);
            }
        }catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported){out.clear();}
        return out;
    }
}
