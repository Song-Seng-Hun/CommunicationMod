import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual installed StSLib selection bodies with field-only graphics fixtures. */
public final class NativeUpgradeChoiceTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true),nativePool=new ClassPool(true);nativePool.insertClassPath(args[1]);
        Map<String,byte[]> bytes=new HashMap<>();
        String cards="com.megacrit.cardcrawl.cards.AbstractCard",grid="com.megacrit.cardcrawl.screens.select.GridCardSelectScreen",dungeon="com.megacrit.cardcrawl.dungeons.AbstractDungeon";
        String branch="com.evacipated.cardcrawl.mod.stslib.patches.cardInterfaces.BranchingUpgradesPatch",multi="com.evacipated.cardcrawl.mod.stslib.patches.cardInterfaces.MultiUpgradePatches";
        String spire="com.evacipated.cardcrawl.modthespire.lib.SpireField",tree="com.evacipated.cardcrawl.mod.stslib.ui.MultiUpgradeTree";
        fixture(p,bytes,"com.megacrit.cardcrawl.helpers.Hitbox",new String[]{"public boolean hovered;","public boolean clicked;","public boolean clickStarted;","public boolean justHovered;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.helpers.input.InputHelper",new String[]{"public static boolean justClickedLeft;","public static boolean justClickedRight;","public static boolean justReleasedClickLeft;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.core.Settings",new String[]{"public static boolean isTouchScreen;","public static boolean isControllerMode;"});
        fixture(p,bytes,cards,new String[]{"public com.megacrit.cardcrawl.helpers.Hitbox hb=new com.megacrit.cardcrawl.helpers.Hitbox();","public int timesUpgraded;"},"public void beginGlowing(){}","public void stopGlowing(){}");
        fixture(p,bytes,grid,new String[]{"public boolean forUpgrade=true;"});
        fixture(p,bytes,dungeon+"$CurrentScreen",new String[]{"public static "+dungeon+"$CurrentScreen GRID=new "+dungeon+"$CurrentScreen();"});
        fixture(p,bytes,dungeon,new String[]{"public static "+dungeon+"$CurrentScreen screen="+dungeon+"$CurrentScreen.GRID;","public static "+grid+" gridSelectScreen=new "+grid+"();"});
        fixture(p,bytes,spire,new String[]{"public Object value;"},"public void set(Object owner,Object v){value=v;}");
        fixture(p,bytes,branch+"$BranchSelectFields",new String[]{"public static "+spire+" isBranchUpgrading=new "+spire+"();","public static "+spire+" waitingForBranchUpgradeSelection=new "+spire+"();"});
        fixture(p,bytes,branch,new String[]{"public static java.util.ArrayList cardList=new java.util.ArrayList();","public static "+cards+" hovered;"},"public static "+cards+" getHoveredCard(){return hovered;}");
        fixture(p,bytes,tree,new String[]{"public static "+cards+" selected;"},"public static void selectCard("+cards+" c){selected=c;}");
        for(String name:new String[]{"BranchingUpgradesCard","MultiUpgradeCard"}) {
            CtClass iface=p.makeInterface("com.evacipated.cardcrawl.mod.stslib.cards.interfaces."+name);bytes.put(iface.getName(),iface.toBytecode());
            CtClass card=p.makeClass("fixture."+name,p.get(cards));card.addInterface(iface);card.addConstructor(CtNewConstructor.defaultConstructor(card));bytes.put(card.getName(),card.toBytecode());
        }
        for(String name:new String[]{branch+"$SelectBranchedUpgrade",multi+"$SelectMultiUpgrade"})bytes.put(name,nativePool.get(name).toBytecode());
        String inputName="communicationmod.observation.NativeUiInput";bytes.put(inputName,Files.readAllBytes(Paths.get(args[0],inputName.replace('.','/')+".class")));
        ClassLoader loader=new ClassLoader(NativeUpgradeChoiceTest.class.getClassLoader()) {
            protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);if(c==null&&b!=null)c=defineClass(n,b,0,b.length);if(c==null)c=super.loadClass(n,resolve);if(resolve)resolveClass(c);return c;}}
        };
        Class<?> card=loader.loadClass(cards),hitbox=loader.loadClass("com.megacrit.cardcrawl.helpers.Hitbox"),input=loader.loadClass(inputName);
        Method press=input.getMethod("press",hitbox,Runnable.class),branchHandler=loader.loadClass(branch+"$SelectBranchedUpgrade").getMethod("Postfix",card),multiHandler=loader.loadClass(multi+"$SelectMultiUpgrade").getMethod("Postfix",card);
        Object branchCard=loader.loadClass("fixture.BranchingUpgradesCard").newInstance();card.getField("timesUpgraded").setInt(branchCard,-1);
        Object hb=card.getField("hb").get(branchCard);System.setProperty("communicationmod.play_control","true");
        press.invoke(null,hb,(Runnable)()->invoke(branchHandler,branchCard));
        Object branchField=loader.loadClass(branch+"$BranchSelectFields").getField("isBranchUpgrading").get(null),waiting=loader.loadClass(branch+"$BranchSelectFields").getField("waitingForBranchUpgradeSelection").get(null);
        check(Boolean.TRUE.equals(branchField.getClass().getField("value").get(branchField)),"native branch flag selected");
        check(Boolean.FALSE.equals(waiting.getClass().getField("value").get(waiting)),"native branch wait cleared");
        card.getField("timesUpgraded").setInt(branchCard,1);branchHandler.invoke(null,branchCard);
        check(Boolean.TRUE.equals(branchField.getClass().getField("value").get(branchField)),"idle handler does not select");
        press.invoke(null,hb,(Runnable)()->invoke(branchHandler,branchCard));
        check(Boolean.FALSE.equals(branchField.getClass().getField("value").get(branchField)),"native normal branch selected");
        Object source=loader.loadClass("fixture.MultiUpgradeCard").newInstance(),candidate=loader.loadClass("fixture.MultiUpgradeCard").newInstance();loader.loadClass(branch).getField("hovered").set(null,source);
        press.invoke(null,card.getField("hb").get(candidate),(Runnable)()->invoke(multiHandler,candidate));
        check(loader.loadClass(tree).getField("selected").get(null)==candidate,"actual native tree handler dispatches selected preview");
        check(!hitbox.getField("clicked").getBoolean(hb),"selection input restored");
        System.clearProperty("communicationmod.play_control");
        System.out.println("PASS: installed native branch/normal/tree selection handlers executed with scoped production input; no live UI claim");
    }
    static void invoke(Method m,Object card){try{m.invoke(null,card);}catch(Exception e){throw new RuntimeException(e);}}
    static void fixture(ClassPool p,Map<String,byte[]> bytes,String name,String[] fields,String... methods)throws Exception{CtClass c=p.makeClass(name);for(String f:fields)c.addField(CtField.make(f,c));for(String m:methods)c.addMethod(CtNewMethod.make(m,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));bytes.put(name,c.toBytecode());}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
