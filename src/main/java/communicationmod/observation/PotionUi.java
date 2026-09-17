package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.*;
import com.megacrit.cardcrawl.ui.panels.PotionPopUp;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** One explicit potion decision, routed through the installed popup including all relic/mod hooks. */
public final class PotionUi {
    public List<ProtocolSession.Action> capture(JsonObject view,String decision) {
        List<ProtocolSession.Action> actions=new ArrayList<>();JsonArray controls=new JsonArray();view.add("potion_controls",controls);
        if(!availablePopup())return actions;
        boolean combat=CombatObservation.inCombat();
        for(int i=0;i<AbstractDungeon.player.potions.size();i++) {
            final int slot=i;AbstractPotion potion=AbstractDungeon.player.potions.get(i);
            if(potion instanceof PotionSlot)continue;
            JsonObject row=new JsonObject();row.addProperty("slot",slot);row.addProperty("id",potion.ID);
            row.addProperty("requires_target",potion.targetRequired);row.addProperty("can_use",potion.canUse());row.addProperty("can_discard",potion.canDiscard());controls.add(row);
            if(potion.canDiscard())actions.add(RoomUi.action("run.potion.discard."+slot,potion.name+" — "+PotionPopUp.TEXT[1],decision,"play",
                ()->same(slot,potion) && potion.canDiscard(),()->applyAndRearm(slot,potion,null,true,decision)));
            if(potion.targetRequired && combat && !AbstractDungeon.isScreenUp) {
                List<AbstractMonster> monsters=AbstractDungeon.getMonsters().monsters;
                for(int j=0;j<monsters.size();j++) {
                    final int targetIndex=j;AbstractMonster target=monsters.get(j);
                    if(usable(potion,target))actions.add(RoomUi.action("run.potion.use."+slot+"."+j,potion.name+" → "+target.name,decision,"play",
                        ()->same(slot,potion) && usable(potion,target) && targetIndex<AbstractDungeon.getMonsters().monsters.size() && AbstractDungeon.getMonsters().monsters.get(targetIndex)==target,
                        ()->applyAndRearm(slot,potion,target,false,decision)));
                }
            } else if(!potion.targetRequired && usable(potion,null))actions.add(RoomUi.action("run.potion.use."+slot,potion.name+" — "+PotionPopUp.TEXT[0],decision,"play",
                ()->same(slot,potion) && usable(potion,null),()->applyAndRearm(slot,potion,null,false,decision)));
        }
        return actions;
    }
    private static boolean availablePopup(){return AbstractDungeon.topPanel!=null && AbstractDungeon.topPanel.potionUi!=null
        && AbstractDungeon.topPanel.potionUi.getClass()==PotionPopUp.class && AbstractDungeon.topPanel.potionUi.isHidden && !AbstractDungeon.topPanel.potionUi.targetMode
        && !AbstractDungeon.topPanel.selectPotionMode && !AbstractDungeon.topPanel.potionCombine;}
    static boolean idle(){return AbstractDungeon.topPanel!=null && AbstractDungeon.topPanel.potionUi!=null
        && AbstractDungeon.topPanel.potionUi.isHidden && !AbstractDungeon.topPanel.potionUi.targetMode
        && !AbstractDungeon.topPanel.selectPotionMode && !AbstractDungeon.topPanel.potionCombine;}
    private static boolean same(int slot,AbstractPotion potion){return availablePopup() && slot<AbstractDungeon.player.potions.size() && AbstractDungeon.player.potions.get(slot)==potion;}
    private static boolean usable(AbstractPotion potion,AbstractMonster target){return RunUiPolicy.potionUse(!(potion instanceof PotionSlot),potion.canUse(),potion.targetRequired,CombatObservation.inCombat(),target!=null && !target.isDeadOrEscaped() && !target.isDying) && (!potion.targetRequired || !AbstractDungeon.isScreenUp);}
    private static void applyAndRearm(int slot,AbstractPotion potion,AbstractMonster target,boolean discard,String decision) {
        apply(slot,potion,target,discard);
        // claim() intentionally consumes the old combat decision before native input.
        // Potion inventory is not part of CombatDecision's key, so a successful
        // potion/discard whose eventual visible combat facts equal the old facts can
        // otherwise remain awaiting_resolution forever. Only rearm after the native
        // handler returned successfully; failures keep the consumed observation blocked.
        if(decision!=null)CombatObservation.invalidate();
    }
    private static void apply(int slot,AbstractPotion potion,AbstractMonster target,boolean discard) {
        PotionPopUp popup=AbstractDungeon.topPanel.potionUi;
        // Opening the popup is part of the single requested use/discard gesture, never a second gameplay choice.
        popup.open(slot,potion);
        try {
            Hitbox option=(Hitbox)NativeUiInput.field(popup,discard?"hbBot":"hbTop");
            NativeUiInput.click(option,()->NativeUiInput.invoke(popup,"updateInput"));
            if(target!=null) {
                if(!popup.targetMode || AbstractDungeon.player.potions.get(slot)!=potion)throw new IllegalStateException("Potion targeting did not open");
                int mouseY=InputHelper.mY;AbstractCard hovered=AbstractDungeon.player.hoveredCard;
                try {
                    // The native target handler reads public cursor/hover state but does not refresh monster hitboxes.
                    InputHelper.mY=(int)(Settings.HEIGHT*0.5f);AbstractDungeon.player.hoveredCard=null;
                    NativeUiInput.press(target.hb,()->{
                        for(AbstractMonster monster:AbstractDungeon.getMonsters().monsters)NativeUiInput.afterHitbox(monster.hb);
                        NativeUiInput.invoke(popup,"updateTargetMode");
                    });
                } finally {InputHelper.mY=mouseY;AbstractDungeon.player.hoveredCard=hovered;}
            }
        } finally {
            popup.close();popup.targetMode=false;GameCursor.hidden=false;
        }
    }
}
