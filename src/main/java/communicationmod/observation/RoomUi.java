package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.shop.*;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import com.megacrit.cardcrawl.screens.select.BossRelicSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.*;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import communicationmod.ChoiceScreenUtils;
import communicationmod.GameStateConverter;
import communicationmod.patches.MerchantPatch;
import communicationmod.protocol.ProtocolSession;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Public shop/rest/treasure controls, using the installed handlers rather than reward synthesis. */
public final class RoomUi {
    public List<ProtocolSession.Action> capture(JsonObject view,JsonObject status,String type) {
        List<ProtocolSession.Action> actions=new ArrayList<>();
        if(!ready()){status.addProperty("reason","native_input_pending_or_transition");return actions;}
        if(type.equals("REST"))rest(view,actions);
        else if(type.equals("SHOP_ROOM")) {
            final ShopRoom room=(ShopRoom)AbstractDungeon.getCurrRoom();final Merchant merchant=room.merchant;
            // Do not call merchant.update() from the observer/render tail. The merchant's native click
            // path is frame-sensitive; queue the existing MerchantPatch injection and let the next
            // normal game update open SHOP_SCREEN. This is also the proven legacy `choose shop` path.
            if(merchant!=null && !MerchantPatch.visitMerchant)actions.add(action("run.shop.enter",Merchant.NAMES[0],null,"play",()->room.merchant==merchant && !AbstractDungeon.isScreenUp && !MerchantPatch.visitMerchant,
                ()->MerchantPatch.visitMerchant=true));
            proceed(actions,"run.room.proceed");
        } else if(type.equals("SHOP_SCREEN"))shop(view,actions);
        else if(type.equals("BOSS_REWARD"))boss(view,actions);
        else if(type.equals("CHEST")) {
            final AbstractChest chest=chest();
            if(chest!=null && !chest.isOpen)actions.add(action("run.chest.open",AbstractChest.TEXT[0],null,"play",()->chest()==chest && !chest.isOpen,
                ()->NativeUiInput.press((Hitbox)NativeUiInput.field(chest,"hb"),()->chest.update())));
            proceed(actions,"run.room.proceed");
        } else if(type.equals("COMPLETE"))proceed(actions,"run.room.proceed");
        if(actions.isEmpty())status.addProperty("reason","room_controls_pending_or_unsupported");
        return actions;
    }
    @SuppressWarnings("unchecked") private static List<AbstractCampfireOption> restOptions(RestRoom room) {
        return (List<AbstractCampfireOption>)NativeUiInput.field(room.campfireUI,"buttons");
    }
    private static void rest(JsonObject view,List<ProtocolSession.Action> actions) {
        RestRoom room=(RestRoom)AbstractDungeon.getCurrRoom();JsonArray options=new JsonArray();view.add("rest_controls",options);
        boolean choosing=room.phase!=AbstractRoom.RoomPhase.COMPLETE && !room.campfireUI.somethingSelected;
        List<AbstractCampfireOption> buttons=restOptions(room);
        for(int i=0;i<buttons.size();i++) {
            AbstractCampfireOption option=buttons.get(i);String id="run.rest."+i;
            String label=GameStateConverter.removeTextFormatting((String)NativeUiInput.field(option,"label"));
            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("label",label);
            row.addProperty("description",GameStateConverter.removeTextFormatting((String)NativeUiInput.field(option,"description")));
            row.addProperty("available",choosing && option.usable);options.add(row);
            if(choosing && option.usable)actions.add(action(id,label,null,"play",
                ()->!room.campfireUI.somethingSelected && room.phase!=AbstractRoom.RoomPhase.COMPLETE && restOptions(room).contains(option) && option.usable,
                ()->NativeUiInput.click(option.hb,()->option.update())));
        }
        if(room.phase==AbstractRoom.RoomPhase.COMPLETE)proceed(actions,"run.rest.proceed");
    }
    private static void shop(JsonObject view,List<ProtocolSession.Action> actions) {
        ShopScreen owner=AbstractDungeon.shopScreen;JsonArray controls=new JsonArray();view.add("shop_controls",controls);
        if(owner.getClass()!=ShopScreen.class || !owner.isActive || (Float)NativeUiInput.field(owner,"rugY")!=0f)return;
        for(AbstractCard card:ChoiceScreenUtils.getShopScreenCards()) {
            final int price=card.price;String id="run.shop.card."+card.uuid;
            boolean available=price>=0 && AbstractDungeon.player.gold>=price && settled(card);
            row(controls,id,card.name,price,available,available?null:"insufficient_gold_or_card_moving");
            if(available)actions.add(action(id,card.name,null,"play",()->shopSame(owner) && ChoiceScreenUtils.getShopScreenCards().contains(card) && card.price==price && AbstractDungeon.player.gold>=price && settled(card),
                ()->NativeUiInput.deferClick(card.hb)));
        }
        List<StoreRelic> relics=ChoiceScreenUtils.getShopScreenRelics();
        for(int i=0;i<relics.size();i++) {
            StoreRelic item=relics.get(i);final int price=item.price;final AbstractRelic relic=item.relic;String id="run.shop.relic."+i;
            boolean available=!item.isPurchased && price>=0 && AbstractDungeon.player.gold>=price;
            row(controls,id,relic.name,price,available,available?null:"purchased_or_insufficient_gold");
            if(available)actions.add(action(id,relic.name,null,"play",()->shopSame(owner) && ChoiceScreenUtils.getShopScreenRelics().contains(item) && item.relic==relic && !item.isPurchased && item.price==price && AbstractDungeon.player.gold>=price,
                ()->NativeUiInput.press(relic.hb,()->NativeUiInput.invoke(owner,"updateRelics"))));
        }
        List<StorePotion> potions=ChoiceScreenUtils.getShopScreenPotions();
        for(int i=0;i<potions.size();i++) {
            StorePotion item=potions.get(i);final int price=item.price;final com.megacrit.cardcrawl.potions.AbstractPotion potion=item.potion;String id="run.shop.potion."+i;
            boolean available=!item.isPurchased && price>=0 && AbstractDungeon.player.gold>=price && potionSlot();
            row(controls,id,potion.name,price,available,available?null:!potionSlot()?"potion_slots_full_or_sozu":"purchased_or_insufficient_gold");
            if(available)actions.add(action(id,potion.name,null,"play",()->shopSame(owner) && ChoiceScreenUtils.getShopScreenPotions().contains(item) && item.potion==potion && !item.isPurchased && item.price==price && AbstractDungeon.player.gold>=price && potionSlot(),
                ()->NativeUiInput.press(potion.hb,()->NativeUiInput.invoke(owner,"updatePotions"))));
        }
        final int purgePrice=ShopScreen.actualPurgeCost;
        boolean purge=owner.purgeAvailable && purgePrice>=0 && AbstractDungeon.player.gold>=purgePrice;
        row(controls,"run.shop.purge",ShopScreen.NAMES[13],purgePrice,purge,purge?null:"unavailable_or_insufficient_gold");
        if(purge)actions.add(action("run.shop.purge",ShopScreen.NAMES[13],null,"play",()->shopSame(owner) && owner.purgeAvailable && ShopScreen.actualPurgeCost==purgePrice && AbstractDungeon.player.gold>=purgePrice,
            ()->NativeUiInput.invoke(owner,"purchasePurge")));
        cancel(actions,"run.shop.leave",null,"play",()->shopSame(owner));
    }
    private static boolean shopSame(ShopScreen owner){return AbstractDungeon.shopScreen==owner && owner.isActive && (Float)NativeUiInput.field(owner,"rugY")==0f;}
    private static void row(JsonArray rows,String id,String label,int price,boolean available,String reason){JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("label",label);row.addProperty("price",price);row.addProperty("available",available);if(reason!=null)row.addProperty("unavailable_reason",reason);rows.add(row);}
    static boolean potionSlot(){if(AbstractDungeon.player.hasRelic("Sozu"))return false;for(com.megacrit.cardcrawl.potions.AbstractPotion potion:AbstractDungeon.player.potions)if(potion instanceof com.megacrit.cardcrawl.potions.PotionSlot)return true;return false;}
    private static void boss(JsonObject view,List<ProtocolSession.Action> actions) {
        BossRelicSelectScreen owner=AbstractDungeon.bossRelicScreen;
        if(owner.getClass()!=BossRelicSelectScreen.class || (Boolean)NativeUiInput.field(owner,"isDone") || (Boolean)NativeUiInput.field(owner,"isVoting") || !owner.blights.isEmpty())return;
        for(int i=0;i<owner.relics.size();i++) {
            AbstractRelic relic=owner.relics.get(i);
            actions.add(action("run.boss_relic."+i,relic.name,null,"play",()->AbstractDungeon.bossRelicScreen==owner && owner.relics.contains(relic) && !(Boolean)NativeUiInput.field(owner,"isDone") && !(Boolean)NativeUiInput.field(owner,"isVoting"),
                ()->NativeUiInput.press(relic.hb,()->owner.update())));
        }
        final com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton cancel=(com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton)NativeUiInput.field(owner,"cancelButton");
        if(NativeUiInput.visible(cancel))actions.add(action("run.boss_relic.skip",(String)NativeUiInput.field(cancel,"buttonText"),null,"play",()->AbstractDungeon.bossRelicScreen==owner && NativeUiInput.visible(cancel) && !(Boolean)NativeUiInput.field(owner,"isDone"),
                ()->NativeUiInput.click(cancel.hb,()->NativeUiInput.invoke(owner,"updateCancelButton"))));
    }
    private static AbstractChest chest(){AbstractRoom room=AbstractDungeon.getCurrRoom();return room instanceof TreasureRoomBoss?((TreasureRoomBoss)room).chest:room instanceof TreasureRoom?((TreasureRoom)room).chest:null;}
    static void proceed(List<ProtocolSession.Action> actions,String id) {
        ProceedButton button=AbstractDungeon.overlayMenu.proceedButton;
        if(NativeUiInput.visible(button))actions.add(action(id,(String)NativeUiInput.field(button,"label"),null,"play",()->AbstractDungeon.overlayMenu.proceedButton==button && NativeUiInput.visible(button),
            ()->NativeUiInput.click((Hitbox)NativeUiInput.field(button,"hb"),()->button.update())));
    }
    static void cancel(List<ProtocolSession.Action> actions,String id,String decision,String mode,BooleanSupplier valid) {
        CancelButton button=AbstractDungeon.overlayMenu.cancelButton;
        if(NativeUiInput.visible(button))actions.add(action(id,button.buttonText,decision,mode,()->valid.getAsBoolean() && AbstractDungeon.overlayMenu.cancelButton==button && NativeUiInput.visible(button),
            ()->NativeUiInput.click(button.hb,()->button.update())));
    }
    static boolean settled(AbstractCard card){return Math.abs(card.current_x-card.target_x)<0.5f && Math.abs(card.current_y-card.target_y)<0.5f && card.isSeen && !card.isLocked && !card.isFlipped;}
    static boolean ready(){return Boolean.getBoolean("communicationmod.play_control") && !Settings.isTouchScreen && !Settings.isControllerMode
        && !CardCrawlGame.isPopupOpen && PotionUi.idle() && !NativeUiInput.pending() && !AbstractDungeon.isFadingIn && !AbstractDungeon.isFadingOut && AbstractDungeon.fadeColor.a<=0.01f
        && AbstractDungeon.player!=null && !AbstractDungeon.player.isDead && !AbstractDungeon.player.isDraggingCard && !AbstractDungeon.player.viewingRelics
        && !InputHelper.justClickedLeft && !InputHelper.justClickedRight && !InputHelper.justReleasedClickLeft;}
    static ProtocolSession.Action action(String id,String label,String decision,String mode,BooleanSupplier valid,Runnable effect) {
        final AbstractDungeon.CurrentScreen screen=AbstractDungeon.screen;final Object player=AbstractDungeon.player,room=AbstractDungeon.getCurrRoom();
        return new ProtocolSession.Action(id,GameStateConverter.removeTextFormatting(label),new JsonObject(),a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException("No arguments expected");},a->{
            if(!ready() || AbstractDungeon.screen!=screen || AbstractDungeon.player!=player || AbstractDungeon.getCurrRoom()!=room || !valid.getAsBoolean())throw new IllegalArgumentException("Native UI decision changed");
            if(decision!=null)CombatObservation.claim(decision,mode);else if(CombatObservation.inCombat())throw new IllegalArgumentException("Combat token missing");
            effect.run();
        });
    }
}
