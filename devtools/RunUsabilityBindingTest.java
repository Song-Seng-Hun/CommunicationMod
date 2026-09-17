import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Installed signatures and actual production wiring; does not claim live UI acceptance. */
public final class RunUsabilityBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);for(int i=1;i<args.length;i++)p.insertClassPath(args[i]);
        String mode=args[0];
        if(mode.equals("reward")) {
            String calls=calls(p,"communicationmod.observation.RewardUi");
            for(String expected:new String[]{"RunUiPolicy.cardReward","RunUiPolicy.proceed","NativeUiInput.click","CombatObservation.claim","SingingBowlButton.update"})check(calls.contains(expected),expected);
            p.get("com.megacrit.cardcrawl.screens.CardRewardScreen").getDeclaredMethod("cardSelectUpdate");
            p.get("com.megacrit.cardcrawl.ui.buttons.SingingBowlButton").getDeclaredMethod("update");
        } else if(mode.equals("grid")) {
            String calls=calls(p,"communicationmod.observation.GridSelectionUi");
            for(String expected:new String[]{"GridCardSelectScreen.update","NativeUiInput.click","RoomUi.action"})check(calls.contains(expected),expected);
            check(!calls.contains("RoomUi.settled"),"grid choices must not wait for pixel-perfect animation convergence");
            check(calls(p,"communicationmod.observation.RoomUi").contains("CombatObservation.claim"),"shared action claims selection token");
            CtClass grid=p.get("com.megacrit.cardcrawl.screens.select.GridCardSelectScreen");
            for(String field:new String[]{"tipMsg","numCards","canCancel","confirmButton","targetGroup","selectedCards","hoveredCard"})grid.getDeclaredField(field);
            check(calls(p,"communicationmod.observation.RunUi").contains("GridSelectionUi.capture"),"grid route");
        } else if(mode.equals("room")) {
            String calls=calls(p,"communicationmod.observation.RoomUi");
            for(String expected:new String[]{"NativeUiInput.click","NativeUiInput.invoke","AbstractCampfireOption.update","AbstractChest.update","BossRelicSelectScreen.update"})check(calls.contains(expected),expected);
            check(!calls.contains("NativeUiInput.deferClick"),"shop card purchase must use native purchase routine, not deferred hitbox state");
            check(!calls.contains("Merchant.update"),"merchant entry must wait for the normal game update");
            check(fieldWrites(p,"communicationmod.observation.RoomUi").contains("communicationmod.patches.MerchantPatch.visitMerchant"),"merchant entry queues native merchant patch");
            CtClass shop=p.get("com.megacrit.cardcrawl.shop.ShopScreen");
            shop.getDeclaredMethod("purchaseCard",new CtClass[]{p.get("com.megacrit.cardcrawl.cards.AbstractCard")});
            check(!calls.contains("CommandExecutor.executeCommand")&&!calls.contains("loseGold")&&!calls.contains("gainGold"),"native UI only");
            check(calls(p,"communicationmod.observation.RunUi").contains("RoomUi.capture"),"room route");
            check(calls.contains("PotionUi.idle") && calls.contains("NativeUiInput.pending"),"pending native input blocks every room action");
        } else if(mode.equals("potion")) {
            String calls=calls(p,"communicationmod.observation.PotionUi");
            for(String expected:new String[]{"PotionPopUp.open","NativeUiInput.click","NativeUiInput.press","RunUiPolicy.potionUse","RoomUi.action"})check(calls.contains(expected),expected);
            check(!calls.contains("AbstractPotion.use")&&!calls.contains("destroyPotion"),"potion effects and hooks remain owned by native handlers");
            p.get("com.megacrit.cardcrawl.ui.panels.PotionPopUp").getDeclaredMethod("updateInput");
            p.get("com.megacrit.cardcrawl.ui.panels.PotionPopUp").getDeclaredMethod("updateTargetMode");
            check(calls(p,"communicationmod.observation.RunUi").contains("PotionUi.capture"),"potion route");
        } else if(mode.equals("information")) {
            CtClass converter=p.get("communicationmod.GameStateConverter");
            check(calls(p,converter.getName()).contains("CardPlayObservation.addTo"),"hand availability binding");
            final Set<String> orbFields=new HashSet<>(),potionFields=new HashSet<>();
            converter.getDeclaredMethod("convertOrbToJson").instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isReader())orbFields.add(f.getFieldName());}});
            converter.getDeclaredMethod("convertPotionToJson").instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isReader())potionFields.add(f.getFieldName());}});
            check(orbFields.contains("description"),"orb description binding");check(potionFields.contains("targetRequired"),"native potion targeting flag, not thrown animation");
            check(calls(p,"communicationmod.observation.CardObservation").contains("CardCostObservation.addTo"),"actual rendered cost binding");
        } else if(mode.equals("upgrade-choice")) {
            String calls=calls(p,"communicationmod.observation.UpgradeChoiceUi");
            check(calls.contains("NativeUiInput.press") && calls.matches("(?s).*AbstractCard\\.update(?:,|\\]).*"),"native branch/tree choice uses update, not hover-only method");
            String nativeCalls=calls(p,"com.megacrit.cardcrawl.cards.AbstractCard");
            check(nativeCalls.contains("SelectBranchedUpgrade.Postfix") && nativeCalls.contains("SelectMultiUpgrade.Postfix"),"installed selection handlers exist");
            check(!calls.contains("AbstractCard.upgrade")&&!calls.contains("setUpgradeIndex"),"never apply a guessed upgrade");
            check(calls(p,"communicationmod.observation.GridSelectionUi").contains("UpgradeChoiceUi.capture"),"upgrade choices routed");
        } else throw new IllegalArgumentException(mode);
        System.out.println("PASS: "+mode+" production/native bindings");
    }
    static String calls(ClassPool p,String name)throws Exception {
        CtClass c;try{c=p.get(name);}catch(NotFoundException missing){throw new AssertionError("Missing UI adapter: "+name,missing);}
        List<String> calls=new ArrayList<>();for(CtBehavior b:c.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall m){calls.add(m.getClassName()+"."+m.getMethodName());}});return calls.toString();
    }
    static String fieldWrites(ClassPool p,String name)throws Exception {
        CtClass c;try{c=p.get(name);}catch(NotFoundException missing){throw new AssertionError("Missing UI adapter: "+name,missing);}
        List<String> fields=new ArrayList<>();for(CtBehavior b:c.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isWriter())fields.add(f.getClassName()+"."+f.getFieldName());}});return fields.toString();
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
