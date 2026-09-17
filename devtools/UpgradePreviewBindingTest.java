import javassist.*;
import java.util.*;

/** Checks installed API contracts and actual compiled wiring without loading the game. */
public final class UpgradePreviewBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);pool.insertClassPath(args[0]);pool.insertClassPath(args[1]);
        CtClass binding;
        try {binding=pool.get("communicationmod.observation.CardUpgradeObservation");}
        catch(NotFoundException absent){throw new AssertionError("Missing actual card upgrade observation binding",absent);}
        Set<String> invoked=new HashSet<>();
        for(CtMethod method:binding.getDeclaredMethods())method.instrument(new javassist.expr.ExprEditor(){
            public void edit(javassist.expr.MethodCall call){invoked.add(call.getClassName()+"."+call.getMethodName());}
        });
        require(invoked.contains("communicationmod.observation.UpgradePreview.describe"),"production preview service is wired");
        CtClass adapter=pool.get("communicationmod.observation.CardUpgradeObservation$GameAdapter");
        Set<String> adapterCalls=new HashSet<>();
        for(CtMethod method:adapter.getDeclaredMethods())method.instrument(new javassist.expr.ExprEditor(){
            public void edit(javassist.expr.MethodCall call){adapterCalls.add(call.getClassName()+"."+call.getMethodName());}
        });
        for(String method:Arrays.asList("makeStatEquivalentCopy","upgrade","initializeDescription","displayUpgrades","canUpgrade"))
            require(adapterCalls.contains("com.megacrit.cardcrawl.cards.AbstractCard."+method),"actual adapter uses native "+method);
        CtClass converter=pool.get("communicationmod.GameStateConverter");
        for(String method:Arrays.asList("getCardRewardState","getShopScreenState","getGridState")) {
            Set<String> calls=new HashSet<>();converter.getDeclaredMethod(method).instrument(new javassist.expr.ExprEditor(){
                public void edit(javassist.expr.MethodCall call){calls.add(call.getMethodName());}
            });
            require(calls.contains("convertDecisionCardToJson"),method+" must expose next upgrade");
        }
        CtClass grid=pool.get("com.megacrit.cardcrawl.screens.select.GridCardSelectScreen");
        require(grid.getDeclaredField("upgradePreviewCard")!=null,"native grid preview field");
        require(grid.getDeclaredField("hoveredCard")!=null,"native preview source field");
        require(pool.get("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton").getDeclaredField("cardToPreview")!=null,"native event card preview field");
        if(args.length>2) {
            pool.insertClassPath(args[2]);
            CtClass tree=pool.get("com.evacipated.cardcrawl.mod.stslib.ui.MultiUpgradeTree");
            for(String field:Arrays.asList("mainCard","cardList","takenList","lockedList","cardGraph"))require(tree.getDeclaredField(field)!=null,"installed tree "+field);
            CtClass vertex=pool.get("com.evacipated.cardcrawl.mod.stslib.util.CardVertex");
            for(String field:Arrays.asList("parents","exclusions","index","card","strict"))require(vertex.getDeclaredField(field)!=null,"installed vertex "+field);
        }
        System.out.println("PASS: installed card/grid/event APIs and actual production upgrade wiring");
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
