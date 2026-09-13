import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

public final class PlayerMechanicsTest {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.AbstractCard",null,new String[]{"public boolean isSeen=true;","public boolean isLocked;","public boolean isFlipped;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.CardGroup",null,new String[]{"public java.util.ArrayList group=new java.util.ArrayList();"});
        fixture(p,bytes,"com.megacrit.cardcrawl.characters.AbstractPlayer",null,new String[0]);
        fixture(p,bytes,"automaton.AutomatonChar","com.megacrit.cardcrawl.characters.AbstractPlayer",new String[0]);
        fixture(p,bytes,"automaton.FunctionHelper",null,new String[]{"public static boolean doStuff;","public static com.megacrit.cardcrawl.cards.CardGroup held=new com.megacrit.cardcrawl.cards.CardGroup();","public static com.megacrit.cardcrawl.cards.AbstractCard secretStorage;"});
        CtClass helperFixture=p.get("automaton.FunctionHelper");helperFixture.defrost();helperFixture.addMethod(CtNewMethod.make("public static int max(){return 3;}",helperFixture));bytes.put(helperFixture.getName(),helperFixture.toBytecode());
        fixture(p,bytes,"guardian.orbs.StasisOrb",null,new String[]{"public com.megacrit.cardcrawl.cards.AbstractCard stasisCard;"});
        fixture(p,bytes,"theHexaghost.TheHexaghost","com.megacrit.cardcrawl.characters.AbstractPlayer",new String[0]);
        fixture(p,bytes,"theHexaghost.HexaMod",null,new String[]{"public static boolean renderFlames=true;"});
        fixture(p,bytes,"theHexaghost.GhostflameHelper",null,new String[]{"public static Object activeGhostFlame;","public static java.util.ArrayList hexaGhostFlames=new java.util.ArrayList();"});
        CtClass flame=p.makeClass("theHexaghost.ghostflames.InfernoGhostflame");
        flame.addField(CtField.make("public boolean charged;",flame));flame.addField(CtField.make("public int triggersRequired=6;",flame));
        flame.addConstructor(CtNewConstructor.defaultConstructor(flame));
        flame.addMethod(CtNewMethod.make("public String getName(){return \"Inferno\";}",flame));
        flame.addMethod(CtNewMethod.make("public String getDescription(){throw new IllegalStateException(\"This native getter prints to stdout\");}",flame));
        flame.addMethod(CtNewMethod.make("public int getActiveFlamesTriggerCount(){return 2;}",flame));
        flame.addField(CtField.make("public int energySpentThisTurn=2;",flame));
        flame.addField(CtField.make("private String[] DESCRIPTIONS=new String[]{\"charged\",\"three\",\"two\",\"one\",\"remaining\",\"inactive\",\"damage\",\"!\",\"active\"};",flame));
        flame.addMethod(CtNewMethod.make("public int getEffectCount(){return 6;}",flame));bytes.put(flame.getName(),flame.toBytecode());
        String name="communicationmod.observation.PlayerMechanicsObservation";Path path=Paths.get(args[0],name.replace('.','/')+".class");if(!Files.exists(path))throw new AssertionError("Missing public mechanic observer");bytes.put(name,Files.readAllBytes(path));
        for(String dependency:new String[]{"CharacterResources","MechanicsCompleteness","NativeMechanicAccess","NativeMechanicAccess$Read","PublicDescription","NativePanelObserver","EncodePanelObserver","GhostflamePanelObserver"}) {
            String n="communicationmod.observation."+dependency;bytes.put(n,Files.readAllBytes(Paths.get(args[0],n.replace('.','/')+".class")));
        }
        ClassLoader loader=new ClassLoader(PlayerMechanicsTest.class.getClassLoader()){protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);if(c==null&&b!=null)c=defineClass(n,b,0,b.length);if(c==null)c=super.loadClass(n,resolve);if(resolve)resolveClass(c);return c;}}};
        Class<?> player=loader.loadClass("com.megacrit.cardcrawl.characters.AbstractPlayer"),helper=loader.loadClass("automaton.FunctionHelper"),card=loader.loadClass("com.megacrit.cardcrawl.cards.AbstractCard");Object actor=loader.loadClass("automaton.AutomatonChar").newInstance();
        Method capture=loader.loadClass(name).getMethod("capture",player,Function.class);Function<Object,Map<String,Object>> projector=c->{Map<String,Object> row=new HashMap<>();row.put("fixture","public card");return row;};
        Object sequence=helper.getField("held").get(null);List<Object> cards=(List<Object>)sequence.getClass().getField("group").get(sequence);cards.add(card.newInstance());helper.getField("secretStorage").set(null,card.newInstance());
        Map<?,?> hidden=(Map<?,?>)capture.invoke(null,actor,projector);check(!hidden.containsKey("encode_sequence"),"hidden encode UI withheld");
        helper.getField("doStuff").setBoolean(null,true);Map<?,?> visible=(Map<?,?>)capture.invoke(null,actor,projector);
        check(((List<?>)visible.get("encode_sequence")).size()==1,"visible native sequence");check(visible.containsKey("function_preview"),"already-rendered function preview");
        check(Boolean.FALSE.equals(visible.get("character_specific_complete")),"no blanket coverage claim");
        Function<Object,Map<String,Object>> incompleteProjector=c->{Map<String,Object> row=new HashMap<>();row.put("displayed_cost_complete",false);return row;};
        Map<?,?> incomplete=(Map<?,?>)capture.invoke(null,actor,incompleteProjector);
        check(Boolean.FALSE.equals(incomplete.get("information_complete")) && ((List<?>)incomplete.get("information_issues")).contains("encode_sequence[0].displayed_cost_complete"),"nested cost completeness integrated into real mechanic capture");
        Map<?,?> mixed=(Map<?,?>)capture.invoke(null,player.newInstance(),projector);
        check(mixed.containsKey("encode_sequence"),"visible encode panel also supported on other characters");
        card.getField("isFlipped").setBoolean(cards.get(0),true);Map<?,?> flipped=(Map<?,?>)capture.invoke(null,actor,projector);
        check(((List<?>)flipped.get("encode_sequence")).isEmpty(),"masked card withheld");
        check(cards.size()==1 && helper.getField("secretStorage").get(null)!=null,"source cards untouched");
        card.getField("isFlipped").setBoolean(cards.get(0),false);for(int i=0;i<4;i++)cards.add(card.newInstance());
        check(((List<?>)((Map<?,?>)capture.invoke(null,actor,projector)).get("encode_sequence")).size()==3,"native encode slot capacity, not internal excess");
        Object orb=loader.loadClass("guardian.orbs.StasisOrb").newInstance();orb.getClass().getField("stasisCard").set(orb,card.newInstance());
        Method orbDetails=loader.loadClass(name).getMethod("orbDetails",Object.class,Function.class);
        check(((Map<?,?>)orbDetails.invoke(null,orb,projector)).containsKey("stasis_card"),"stasis card UI attached");
        Class<?> hexaHelper=loader.loadClass("theHexaghost.GhostflameHelper");Object inferno=loader.loadClass(flame.getName()).newInstance();
        ((List<Object>)hexaHelper.getField("hexaGhostFlames").get(null)).add(inferno);hexaHelper.getField("activeGhostFlame").set(null,inferno);
        Map<?,?> hexa=(Map<?,?>)capture.invoke(null,loader.loadClass("theHexaghost.TheHexaghost").newInstance(),projector);
        check(hexa.containsKey("ghostflames"),"side-effecting Inferno description getter is never invoked");
        Map<?,?> infernoView=(Map<?,?>)((List<?>)hexa.get("ghostflames")).get(0);
        check(Boolean.TRUE.equals(infernoView.get("description_complete")),"Inferno localized prose without invoking logging getter");
        check("onedamage6!active".equals(infernoView.get("description")),"Inferno exact current public description");
        ((List<Object>)hexaHelper.getField("hexaGhostFlames").get(null)).add(new Object());
        Map<?,?> failedPanel=(Map<?,?>)capture.invoke(null,actor,projector);
        check("native_character_panel_unavailable".equals(failedPanel.get("ghostflames_unavailable_reason")),"unknown renderer has a panel-local failure");
        check(failedPanel.containsKey("encode_sequence"),"one failing observer cannot erase another public panel");
        check(Boolean.FALSE.equals(failedPanel.get("information_complete")),"observer failure remains incomplete at the aggregate boundary");
        System.out.println("PASS: real mechanic projector, visibility gates, native sequence/preview and explicit partial coverage");
    }
    static void fixture(ClassPool p,Map<String,byte[]> bytes,String name,String parent,String[] fields)throws Exception{CtClass c=parent==null?p.makeClass(name):p.makeClass(name,p.get(parent));for(String f:fields)c.addField(CtField.make(f,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));bytes.put(name,c.toBytecode());}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
