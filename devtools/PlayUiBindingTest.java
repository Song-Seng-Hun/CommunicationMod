import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Installed bytecode check, deliberately not a gameplay claim. */
public final class PlayUiBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);for(String path:args)p.insertClassPath(path);
        CtClass run;
        try{run=p.get("communicationmod.observation.RunUi");}
        catch(NotFoundException missing){throw new AssertionError("Missing local play adapter",missing);}
        String calls=calls(run).toString();
        check(calls.contains("CombatObservation.claim"),"claim whole-hand token before combat mutation");
        check(calls.contains("ObserverSession.publicObservation"),"same privacy filter as passive observations");
        check(!calls.contains("CommandExecutor.executeCommand"),"legacy executor remains blocked");
        check(calls.contains("FtueTip.update"),"tutorial acknowledgement uses the original button handler");
        check(calls.contains("MultiPageFtue.update"),"first combat uses its distinct paginated tutorial handler");
        javassist.bytecode.ConstPool constants=run.getClassFile2().getConstPool();boolean hermit=false;
        for(int i=1;i<constants.getSize();i++)if(constants.getTag(i)==javassist.bytecode.ConstPool.CONST_String && "hermit.util.HermitTutorials".equals(constants.getStringInfo(i)))hermit=true;
        check(hermit,"Hermit tutorial must have an explicit optional allowlist entry");
        check(calls(p.get("communicationmod.observation.MenuUi")).toString().contains("CharacterSelectScreen.updateButtons"),"original Embark handler bound");
        CtClass button=p.get("com.megacrit.cardcrawl.ui.buttons.ConfirmButton");for(String f:new String[]{"isHidden","current_x","target_x","buttonText"})button.getDeclaredField(f);
        p.get("com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen").getDeclaredMethod("updateButtons");
        if(args.length>2){CtClass h=p.get("hermit.util.HermitTutorials");for(String f:new String[]{"txt1","txt2","LABEL","currentSlot","scrollTimer","screen"})h.getDeclaredField(f);h.getDeclaredMethod("update");}
        System.out.println("PASS: installed Embark signatures, shared public filter, whole-hand claim and no legacy dispatch");
    }
    private static List<String> calls(CtClass c)throws Exception{List<String> out=new ArrayList<>();for(CtBehavior b:c.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall m){out.add(m.getClassName()+"."+m.getMethodName());}});return out;}
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
}
