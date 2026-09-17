import javassist.*;
import javassist.expr.*;
import java.util.*;
public final class HandSelectionBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);for(String p:args)pool.insertClassPath(p);
        CtClass adapter;
        try{adapter=pool.get("communicationmod.observation.HandSelectionUi");}
        catch(NotFoundException e){throw new AssertionError("Missing native hand selection adapter",e);}
        Set<String> calls=new HashSet<>();
        for(CtBehavior b:adapter.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall c){calls.add(c.getClassName()+"."+c.getMethodName());}});
        for(String method:new String[]{"claim","validate"})if(!calls.contains("communicationmod.observation.CombatObservation."+method))throw new AssertionError("Missing decision "+method);
        if(!calls.contains("communicationmod.ChoiceScreenUtils.makeHandSelectScreenChoice"))throw new AssertionError("Missing native select");
        for(String call:calls)if(call.endsWith(".removeCard")||call.endsWith(".addToTop")||call.endsWith(".clear"))throw new AssertionError("Direct selection mutation: "+call);
        CtClass screen=pool.get("com.megacrit.cardcrawl.screens.select.HandCardSelectScreen");
        screen.getDeclaredMethod("updateSelectedCards");screen.getDeclaredMethod("selectHoveredCard");
        for(String f:new String[]{"waitThenClose","message","anyNumber","upTo","canPickZero"})screen.getDeclaredField(f);
        System.out.println("PASS: native hand selection methods and selection decision guards");
    }
}
