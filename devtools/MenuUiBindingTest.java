import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Actual installed signatures and dispatch bytecode, without initializing the game. */
public final class MenuUiBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);for(String path:args)pool.insertClassPath(path);
        CtClass ui;
        try{ui=pool.get("communicationmod.observation.MenuUi");}
        catch(NotFoundException missing){throw new AssertionError("Missing allowlisted live menu binding",missing);}
        String pkg="com.megacrit.cardcrawl.screens.";
        for(String[] pair:new String[][]{{"mainMenu.MenuButton","label"},{"mainMenu.MenuButton","hidden"},{"mainMenu.MenuButton","x"},{"mainMenu.MenuButton","targetX"},
            {"mainMenu.MainMenuPanelButton","header"},{"mainMenu.MainMenuPanelButton","description"},{"mainMenu.MainMenuPanelButton","result"},{"mainMenu.MainMenuPanelButton","animTimer"}})
            pool.get(pkg+pair[0]).getDeclaredField(pair[1]);
        pool.get(pkg+"mainMenu.MainMenuPanelButton").getDeclaredMethod("buttonEffect");
        pool.get(pkg+"charSelect.CharacterOption").getDeclaredMethod("updateHitbox");
        String sourceCalls=calls(ui).toString();
        check(sourceCalls.contains("MenuButton.buttonEffect"),"use real Play handler");
        check(sourceCalls.contains("MainMenuScreen.hideMenuButtons"),"preserve post-click main button hiding");
        check(sourceCalls.contains("MenuPanelScreen.hide"),"panel hide before its effect");
        check(!sourceCalls.contains("CommandExecutor") && !sourceCalls.contains("getDeclaredFields"),"no legacy executor or broad field dump");
        check(calls(pool.get(pkg+"mainMenu.MainMenuPanelButton")).toString().contains("RedirectPlayNormal.Prefix"),"installed Downfall mode redirect preserved");
        check(calls(pool.get(pkg+"charSelect.CharacterOption")).toString().contains("CharacterSelectScreen.justSelected"),"installed character UI selection route exists");
        check(calls(pool.get("communicationmod.observation.LocalObserver")).toString().contains("MenuControlSession.receive"),"opt-in real menu receive wired on game thread");
        System.out.println("PASS: installed menu labels/animation signatures, native Play/Downfall redirect/character-selection handlers; no game execution");
    }
    private static List<String> calls(CtClass type)throws Exception {
        List<String> out=new ArrayList<>();for(CtBehavior b:type.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall c){out.add(c.getClassName()+"."+c.getMethodName());}});return out;
    }
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
}
