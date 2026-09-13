package communicationmod.observation;

import com.google.gson.*;
import communicationmod.*;
import communicationmod.protocol.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.map.MapRoomNode;
import java.util.*;

/** Explicit copied-test-profile actions only. Does not enable the legacy command executor. */
public final class RunUi {
    private final MenuUi menu=new MenuUi(true);
    private final RewardUi rewards=new RewardUi();
    private final HandSelectionUi handSelection=new HandSelectionUi();
    private final GridSelectionUi gridSelection=new GridSelectionUi();
    private final RoomUi rooms=new RoomUi();
    private final PotionUi potions=new PotionUi();
    private List<ProtocolSession.Action> offered=Collections.emptyList();
    private boolean inMenu,reported;

    public JsonObject capture() {
        inMenu=CardCrawlGame.mode==CardCrawlGame.GameMode.CHAR_SELECT;
        if(inMenu)return menu.capture();
        JsonObject view=new JsonObject(),status=new JsonObject();view.add("menu",status);
        status.addProperty("screen","RUN_TRANSITION");status.addProperty("stable",false);
        offered=new ArrayList<>();
        try {
            requireEnabled();
            if(!CommandExecutor.isInDungeon() || AbstractDungeon.player==null || AbstractDungeon.getCurrMapNode()==null)return view;
            view=ObserverSession.publicObservation(new JsonParser().parse(GameStateConverter.getCommunicationState()).getAsJsonObject());
            view.add("menu",status);
            String type=ChoiceScreenUtils.getCurrentChoiceType().name();status.addProperty("screen",type);
            status.addProperty("game_screen",AbstractDungeon.screen.name());
            status.addProperty("scope","partial_local_run_control");
            if(CardCrawlGame.isPopupOpen || AbstractDungeon.isFadingOut || AbstractDungeon.fadeColor.a>0.01f || AbstractDungeon.player.isDead) return view;
            if(!RoomUi.ready()){status.addProperty("reason","native_input_pending_or_transition");return view;}
            if(type.equals("MAP")&&communicationmod.map.MapDrawing.blocksNavigation()) {
                status.addProperty("reason","human_editing_map");return view;
            }
            if(AbstractDungeon.screen==AbstractDungeon.CurrentScreen.FTUE) {
                tutorial(view,status);
            } else if(type.equals("HAND_SELECT")) {
                Map<String,Object> decision=CombatObservation.observation();
                if(!CombatObservation.inCombat())offered.addAll(handSelection.capture(view,status,null));
                else if(Boolean.TRUE.equals(decision.get("ready")) && "selection".equals(decision.get("mode")))
                    offered.addAll(handSelection.capture(view,status,(String)decision.get("decision_id")));
                else status.addProperty("reason","selection_not_ready");
            } else if(type.equals("GRID") || type.equals("CARD_REWARD")) {
                String decision=null;
                if(CombatObservation.inCombat()) {
                    Map<String,Object> current=CombatObservation.observation();
                    if(!Boolean.TRUE.equals(current.get("ready")) || !"selection".equals(current.get("mode"))) {
                        status.addProperty("reason","selection_not_ready");return view;
                    }
                    decision=(String)current.get("decision_id");
                }
                if(type.equals("GRID"))offered.addAll(gridSelection.capture(view,status,decision));
                else {rewards.capture(view,status,decision);offered.addAll(rewards.actions());}
            } else if(CombatObservation.inCombat()) {
                Map<String,Object> decision=CombatObservation.observation();
                if(Boolean.TRUE.equals(decision.get("ready")) && "play".equals(decision.get("mode")))combat((String)decision.get("decision_id"));
                else status.addProperty("reason","combat_not_ready_or_unsupported_selection");
            } else if(type.equals("COMBAT_REWARD") || type.equals("CARD_REWARD")) {
                rewards.capture(view,status);offered.addAll(rewards.actions());
            } else if(type.equals("EVENT")) {
                offered.addAll(DialogueObservation.eventReadingActions());
                if(DialogueObservation.allowsEventCommand("choose")) {
                    List<com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton> buttons=ChoiceScreenUtils.getActiveEventButtons();
                    for(int i=0;i<buttons.size();i++) {
                        final int index=i;Object button=buttons.get(i);
                        offered.add(simple("run.event."+i,GameStateConverter.removeTextFormatting(buttons.get(i).msg),()->{
                            if(!DialogueObservation.allowsEventCommand("choose") || index>=ChoiceScreenUtils.getActiveEventButtons().size()
                                || ChoiceScreenUtils.getActiveEventButtons().get(index)!=button)throw new IllegalArgumentException("Event changed");
                            ChoiceScreenUtils.makeEventChoice(index);
                        }));
                    }
                }
                if(offered.isEmpty())status.addProperty("reason","event_text_not_ready_or_unsupported_renderer");
            } else if(type.equals("MAP")) {
                offered.addAll(communicationmod.map.MapDrawing.actions());
                final MapRoomNode origin=AbstractDungeon.getCurrMapNode();
                final Object map=AbstractDungeon.map;
                if(!communicationmod.compat.DownfallMapCoordinates.isSupported()) {
                    status.addProperty("reason",communicationmod.compat.DownfallMapCoordinates.getUnsupportedReason());
                } else if(!mapInputPending()) {
                    if(ChoiceScreenUtils.bossNodeAvailable()) {
                        offered.add(simple("run.map.boss",AbstractDungeon.bossKey==null?"boss":AbstractDungeon.bossKey,()->{
                            requireMapUnchanged(origin,map);
                            if(!ChoiceScreenUtils.bossNodeAvailable())throw new IllegalArgumentException("Boss entry changed");
                            ChoiceScreenUtils.makeMapChoice(0);
                        }));
                    } else {
                        List<MapRoomNode> nodes=ChoiceScreenUtils.getMapScreenNodeChoices();
                        for(int i=0;i<nodes.size();i++) {
                            final int index=i;MapRoomNode node=nodes.get(i);
                            offered.add(simple("run.map."+node.x+"."+node.y,"x="+node.x+", y="+node.y+" ("+node.getRoom().getMapSymbol()+")",()->{
                                requireMapUnchanged(origin,map);
                                List<MapRoomNode> fresh=ChoiceScreenUtils.getMapScreenNodeChoices();
                                if(ChoiceScreenUtils.bossNodeAvailable() || index>=fresh.size() || fresh.get(index)!=node)throw new IllegalArgumentException("Map changed");
                                ChoiceScreenUtils.makeMapChoice(index);
                            }));
                        }
                    }
                    if(!communicationmod.compat.DownfallMapCoordinates.isSupported()) {
                        offered.clear();status.addProperty("reason",communicationmod.compat.DownfallMapCoordinates.getUnsupportedReason());
                    }
                } else status.addProperty("reason","map_input_pending_or_not_ready");
            } else if(Arrays.asList("REST","SHOP_ROOM","SHOP_SCREEN","BOSS_REWARD","CHEST","COMPLETE").contains(type)) {
                offered.addAll(rooms.capture(view,status,type));
            } else status.addProperty("reason","unsupported_run_screen");
            if(!CombatObservation.inCombat() && Arrays.asList("MAP","REST","SHOP_ROOM","SHOP_SCREEN","COMBAT_REWARD","COMPLETE").contains(type))offered.addAll(potions.capture(view,null));
            else if(CombatObservation.inCombat() && type.equals("NONE")) {
                Map<String,Object> current=CombatObservation.observation();
                if(Boolean.TRUE.equals(current.get("ready")) && "play".equals(current.get("mode")))offered.addAll(potions.capture(view,(String)current.get("decision_id")));
            }
            status.addProperty("stable",!offered.isEmpty());return view;
        } catch(RuntimeException | LinkageError failure) {
            offered.clear();status.addProperty("stable",false);status.addProperty("reason","run_capture_failed");
            if(!reported){reported=true;System.err.println("[COMM-PLAY] Capture failed (repeats suppressed)");failure.printStackTrace(System.err);}
            return view;
        }
    }
    public List<ProtocolSession.Action> actions(){return inMenu?menu.actions():new ArrayList<>(offered);}

    private static boolean mapInputPending() {
        return AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.MAP
            || AbstractDungeon.getCurrRoom()==null
            || AbstractDungeon.getCurrRoom().phase!=com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMPLETE
            || AbstractDungeon.dungeonMapScreen.clicked
            || communicationmod.patches.MapRoomNodeHoverPatch.doHover
            || communicationmod.patches.DungeonMapPatch.doBossHover;
    }

    private static void requireMapUnchanged(MapRoomNode origin,Object map) {
        if(mapInputPending() || AbstractDungeon.getCurrMapNode()!=origin || AbstractDungeon.map!=map
            || !communicationmod.compat.DownfallMapCoordinates.isSupported())throw new IllegalArgumentException("Map changed");
    }

    private void tutorial(JsonObject view,JsonObject status) {
        com.megacrit.cardcrawl.ui.FtueTip tip=AbstractDungeon.ftue;
        if(tip!=null && tip.getClass()==com.megacrit.cardcrawl.ui.MultiPageFtue.class) {
            pagedTutorial(tip,view,status,false);return;
        }
        if(tip!=null && tip.getClass().getName().equals("hermit.util.HermitTutorials")) {
            pagedTutorial(tip,view,status,true);return;
        }
        if(tip==null || tip.getClass()!=com.megacrit.cardcrawl.ui.FtueTip.class) {
            status.addProperty("reason","unsupported_tutorial_renderer");return;
        }
        JsonObject text=new JsonObject();
        text.addProperty("title",GameStateConverter.removeTextFormatting(com.megacrit.cardcrawl.ui.FtueTip.LABEL[0]+field(tip,"header")));
        text.addProperty("body",GameStateConverter.removeTextFormatting((String)field(tip,"body")));
        String label=com.megacrit.cardcrawl.ui.FtueTip.LABEL[1];text.addProperty("confirm",label);
        view.add("tutorial",text);status.addProperty("screen","TUTORIAL");
        offered.add(simple("run.tutorial.confirm",label,()->{
            if(AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.FTUE || AbstractDungeon.ftue!=tip)throw new IllegalArgumentException("Tutorial changed");
            com.megacrit.cardcrawl.ui.buttons.GotItButton button=(com.megacrit.cardcrawl.ui.buttons.GotItButton)field(tip,"button");
            button.hb.clicked=true;
            try{tip.update();}finally{button.hb.clicked=false;}
        }));
    }
    private void pagedTutorial(com.megacrit.cardcrawl.ui.FtueTip tip,JsonObject view,JsonObject status,boolean hermit) {
        status.addProperty("screen","TUTORIAL");
        int slot=(Integer)field(tip,"currentSlot");
        com.megacrit.cardcrawl.ui.buttons.ProceedButton button=AbstractDungeon.overlayMenu.proceedButton;
        int pages=hermit?2:3;
        if(slot>0 || slot < 1-pages || (Float)field(tip,"scrollTimer")!=0f
            || ((com.badlogic.gdx.graphics.Color)field(tip,"screen")).a<0.8f
            || (Boolean)field(button,"isHidden")
            || Math.abs((Float)field(button,"current_x")-(Float)field(button,"target_x"))>0.5f) {
            status.addProperty("reason","tutorial_transition");return;
        }
        JsonObject text=new JsonObject();
        text.addProperty("page",1-slot);text.addProperty("pages",pages);
        text.addProperty("body",GameStateConverter.removeTextFormatting((String)field(tip,(hermit?"txt":"msg")+(1-slot))));
        text.addProperty("footer",GameStateConverter.removeTextFormatting(((String[])field(tip,"LABEL"))[2]));
        String label=(String)field(button,"label");text.addProperty("confirm",label);
        text.addProperty("illustrations_available",false);view.add("tutorial",text);
        offered.add(simple("run.tutorial.confirm",label,()->{
            if(AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.FTUE || AbstractDungeon.ftue!=tip || (Integer)field(tip,"currentSlot")!=slot
                || AbstractDungeon.overlayMenu.proceedButton!=button)throw new IllegalArgumentException("Tutorial changed");
            boolean click=com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft,hover=button.isHovered;
            try{button.isHovered=true;com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft=true;
                if(hermit)tip.update();else ((com.megacrit.cardcrawl.ui.MultiPageFtue)tip).update();}
            finally{button.isHovered=hover;com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft=click;}
        }));
    }
    private static Object field(Object owner,String name) {
        try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported tutorial field: "+name,failure);}
    }

    private void combat(String decision) {
        for(AbstractCard card:AbstractDungeon.player.hand.group) {
            if(card.target==AbstractCard.CardTarget.ENEMY || card.target==AbstractCard.CardTarget.SELF_AND_ENEMY) {
                List<AbstractMonster> targets=AbstractDungeon.getMonsters().monsters;
                for(int i=0;i<targets.size();i++)if(!targets.get(i).isDeadOrEscaped() && !targets.get(i).isDying && CardPlayObservation.canUse(card,AbstractDungeon.player,targets.get(i)))play(decision,card,targets.get(i),i);
            } else if(CardPlayObservation.canUse(card,AbstractDungeon.player,null))play(decision,card,null,-1);
        }
        offered.add(simple("run.end_turn",(String)field(AbstractDungeon.overlayMenu.endTurnButton,"label"),()->{
            CombatObservation.claim(decision,"play");AbstractDungeon.overlayMenu.endTurnButton.disable(true);
        }));
        CombatObservation.validate(decision,"play");
    }
    private void play(String decision,AbstractCard card,AbstractMonster target,int targetIndex) {
        offered.add(simple("run.play."+card.uuid+"."+targetIndex,card.name+(target==null?"":" → "+target.name),()->{
            CombatObservation.validate(decision,"play");
            if(!AbstractDungeon.player.hand.group.contains(card) || target!=null && (target.isDeadOrEscaped()
                || targetIndex>=AbstractDungeon.getMonsters().monsters.size() || AbstractDungeon.getMonsters().monsters.get(targetIndex)!=target)
                || !CardPlayObservation.canUse(card,AbstractDungeon.player,target))throw new IllegalArgumentException("Card/target changed");
            CombatObservation.claim(decision,"play");AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card,target));
        }));
    }
    private static ProtocolSession.Action simple(String id,String label,Runnable effect) {
        return new ProtocolSession.Action(id,label,new JsonObject(),a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},a->{requireEnabled();effect.run();});
    }
    private static void requireEnabled(){if(!Boolean.getBoolean("communicationmod.play_control"))throw new IllegalStateException("Local play control not enabled");}
}
