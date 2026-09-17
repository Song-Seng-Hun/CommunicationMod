import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Installed signatures and integration checks, not a claim of live gameplay. */
public final class RewardUiBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);for(String arg:args)pool.insertClassPath(arg);
        CtClass ui;
        try{ui=pool.get("communicationmod.observation.RewardUi");}
        catch(NotFoundException e){throw new AssertionError("Missing scoped reward UI adapter",e);}
        List<String> calls=new ArrayList<>();
        for(CtBehavior b:ui.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall m){calls.add(m.getClassName()+"."+m.getMethodName());}});
        check(calls.toString().contains("ChoiceScreenUtils.makeCombatRewardChoice"),"existing reward input path");
        check(calls.toString().contains("SkipCardButton.update"),"real skip handler");
        check(calls.toString().contains("ProceedButton.update"),"real proceed handler");
        check(!calls.toString().contains("gainGold")&&!calls.toString().contains("CommandExecutor.executeCommand"),"no direct currency or legacy command mutation");
        CtClass card=pool.get("com.megacrit.cardcrawl.screens.CardRewardScreen");
        card.getDeclaredMethod("cardSelectUpdate");
        for(String name:new String[]{"draft","discovery","chooseOne","codex","isVoting","skipButton"})card.getDeclaredField(name);
        pool.get("com.megacrit.cardcrawl.screens.CombatRewardScreen").getDeclaredField("rewardAnimTimer");
        System.out.println("PASS: scoped reward adapter, normal input handlers, installed animation/mode fields");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
