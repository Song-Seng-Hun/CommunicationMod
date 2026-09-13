import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Real passive observer, field fixtures only; every cost evaluator in the fixture throws. */
public final class NativeCostObservationTest {
    static ClassPool p=new ClassPool(true);static Map<String,byte[]> bytes=new HashMap<>();static ClassLoader loader;static Class<?> observer;static Method rendered,add;
    public static void main(String[] args)throws Exception {
        fixture("com.megacrit.cardcrawl.cards.AbstractCard",null,
            "public boolean isSeen=true;","public boolean isLocked;","public boolean isFlipped;","public boolean freeToPlayOnce;","public boolean isInAutoplay;","public boolean ignoreEnergyOnUse;",
            "public int cost=2;","public int costForTurn=2;","public int timesUpgraded;","public int energyOnUse;","public String cardID=\"card\";","public java.util.UUID uuid=java.util.UUID.randomUUID();",
            "public java.util.ArrayList modifiers=new java.util.ArrayList();","public String getCost(){throw new IllegalStateException(\"must not evaluate costs\");}","public boolean freeToPlay(){throw new IllegalStateException(\"must not evaluate free play\");}");
        fixture("com.megacrit.cardcrawl.cards.CardGroup",null,"public java.util.ArrayList group=new java.util.ArrayList();");
        fixture("com.megacrit.cardcrawl.powers.AbstractPower",null,"public String ID=\"power\";","public int amount;");
        fixture("com.megacrit.cardcrawl.relics.AbstractRelic",null,"public String relicId=\"relic\";","public int counter;");
        fixture("com.megacrit.cardcrawl.stances.AbstractStance",null,"public String ID=\"stance\";");
        fixture("com.megacrit.cardcrawl.characters.AbstractPlayer",null,"public int currentHealth=20;","public com.megacrit.cardcrawl.stances.AbstractStance stance;",
            "public java.util.ArrayList powers=new java.util.ArrayList();","public java.util.ArrayList relics=new java.util.ArrayList();","public java.util.ArrayList orbs=new java.util.ArrayList();",
            "public com.megacrit.cardcrawl.cards.CardGroup hand=new com.megacrit.cardcrawl.cards.CardGroup();");
        fixture("com.megacrit.cardcrawl.dungeons.AbstractDungeon",null,"public static com.megacrit.cardcrawl.characters.AbstractPlayer player=new com.megacrit.cardcrawl.characters.AbstractPlayer();");
        fixture("com.megacrit.cardcrawl.ui.panels.EnergyPanel",null,"public static int totalCount=1;");
        fixture("collector.util.NewReserves",null,"public static int count=4;","public static int reserveCount(){return count;}");
        fixture("basemod.helpers.CardModifierManager",null,"public static java.util.ArrayList modifiers(com.megacrit.cardcrawl.cards.AbstractCard card){return card.modifiers;}");
        fixture("collector.cardmods.PyreMod",null);
        CtClass iface=p.makeInterface("basemod.interfaces.AlternateCardCostModifier");bytes.put(iface.getName(),iface.toBytecode());
        fixture("outside.CustomModifier",null);
        CtClass alternative=p.makeClass("collector.cardmods.UnauditedAlternate");alternative.addInterface(iface);alternative.addConstructor(CtNewConstructor.defaultConstructor(alternative));bytes.put(alternative.getName(),alternative.toBytecode());
        loader=new ClassLoader(NativeCostObservationTest.class.getClassLoader()){
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(name)){
                Class<?> c=findLoadedClass(name);byte[] b=bytes.get(name);
                if(c==null&&b==null&&name.startsWith("communicationmod.observation."))try{b=Files.readAllBytes(Paths.get(args[0],name.replace('.','/')+".class"));}catch(IOException missing){}
                if(c==null&&b!=null)c=defineClass(name,b,0,b.length);if(c==null)c=super.loadClass(name,resolve);if(resolve)resolveClass(c);return c;
            }}
        };
        observer=loader.loadClass("communicationmod.observation.CardCostObservation");Class<?> cardType=loader.loadClass("com.megacrit.cardcrawl.cards.AbstractCard");
        rendered=observer.getMethod("rendered",cardType,String.class);add=observer.getMethod("addTo",Map.class,cardType);
        Object card=cardType.newInstance(),player=get(loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon"),"player");list(get(player,"hand"),"group").add(card);
        check(Boolean.FALSE.equals(read(card).get("displayed_cost_complete")),"unrendered cost unavailable");
        frame(card,"2");Map<?,?> cost=read(card);check(Boolean.TRUE.equals(cost.get("cost_components_complete")),"native components complete after actual passive observation");
        check("energy_or_reserves".equals(cost.get("displayed_cost_kind")),"numeric reserve UI correctly classified");
        set(loader.loadClass("collector.util.NewReserves"),"count",5);check(Boolean.FALSE.equals(read(card).get("displayed_cost_complete")),"reserve changes invalidate cached final text");
        frame(card,"2");Object relic=make("com.megacrit.cardcrawl.relics.AbstractRelic");list(player,"relics").add(relic);check(Boolean.FALSE.equals(read(card).get("displayed_cost_complete")),"relic addition invalidates");
        frame(card,"2");set(relic,"counter",1);check(Boolean.FALSE.equals(read(card).get("displayed_cost_complete")),"relic counters invalidate");
        frame(card,"2");list(card,"modifiers").add(make("collector.cardmods.PyreMod"));check(Boolean.FALSE.equals(read(card).get("displayed_cost_complete")),"modifier changes invalidate");
        frame(card,"0");check(((List<?>)read(card).get("cost_components")).size()==2,"extra sacrifice survives zero energy text");
        set(card,"cardID","collector:FingerOfDeath");frame(card,"2");check("reserves".equals(read(card).get("displayed_cost_kind")),"reserve-only card");
        list(card,"modifiers").add(make("outside.CustomModifier"));frame(card,"2");check(Boolean.FALSE.equals(read(card).get("cost_components_complete")),"external modifier incomplete");
        list(card,"modifiers").clear();list(card,"modifiers").add(make("collector.cardmods.UnauditedAlternate"));frame(card,"2");check(Boolean.FALSE.equals(read(card).get("cost_components_complete")),"alternate provider even in a native package stays incomplete");
        list(card,"modifiers").clear();frame(card,"?");check(Boolean.FALSE.equals(read(card).get("cost_components_complete")),"unknown text incomplete");
        frame(card,"2");set(card,"isFlipped",true);check(!read(card).containsKey("cost_components"),"masked card costs not disclosed");
        check(get(card,"cost").equals(2)&&get(card,"costForTurn").equals(2)&&list(get(player,"hand"),"group").size()==1,"observations do not modify game state");
        System.out.println("PASS: actual passive cost observer; native resources/Pyre; stale reserves/relics/modifiers; custom providers; no cost/free-play evaluation");
    }
    static void fixture(String name,String parent,String... members)throws Exception{CtClass c=parent==null?p.makeClass(name):p.makeClass(name,p.get(parent));for(String m:members)if(m.contains("){"))c.addMethod(CtNewMethod.make(m,c));else c.addField(CtField.make(m,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));bytes.put(name,c.toBytecode());}
    static Object make(String type)throws Exception{return loader.loadClass(type).newInstance();}
    static Object get(Object o,String key)throws Exception{return (o instanceof Class?(Class<?>)o:o.getClass()).getField(key).get(o instanceof Class?null:o);}
    static void set(Object o,String key,Object value)throws Exception{(o instanceof Class?(Class<?>)o:o.getClass()).getField(key).set(o instanceof Class?null:o,value);}
    @SuppressWarnings("unchecked")static List<Object> list(Object o,String key)throws Exception{return (List<Object>)get(o,key);}
    static void frame(Object card,String text)throws Exception{observer.getMethod("beginFrame").invoke(null);rendered.invoke(null,card,text);observer.getMethod("completeFrame").invoke(null);}
    static Map<?,?> read(Object card)throws Exception{Map<String,Object> out=new LinkedHashMap<>();add.invoke(null,out,card);return out;}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
