import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
/** Real observer fixtures catch hidden face/intent leakage, stale global ownership and source mutation. */
public final class BossObservationTest {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        CtClass creature=p.makeClass("com.megacrit.cardcrawl.core.AbstractCreature");creature.addConstructor(CtNewConstructor.defaultConstructor(creature));creature.addMethod(CtNewMethod.abstractMethod(CtClass.voidType,"render",new CtClass[0],new CtClass[0],creature));bytes.put(creature.getName(),creature.toBytecode());
        fixture(p,bytes,"com.megacrit.cardcrawl.monsters.AbstractMonster","com.megacrit.cardcrawl.core.AbstractCreature",new String[]{"public boolean isDead;","public boolean isDying;","public boolean isEscaping;","public boolean escaped;"});
        CtClass monsterFixture=p.get("com.megacrit.cardcrawl.monsters.AbstractMonster");monsterFixture.defrost();
        monsterFixture.addField(CtField.make("public boolean wheelActive=true;",monsterFixture));
        monsterFixture.addMethod(CtNewMethod.make("public boolean hasPower(String id){return wheelActive && id.equals(\"downfall:HermitWheelOfFortune\");}",monsterFixture));bytes.put(monsterFixture.getName(),monsterFixture.toBytecode());
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.AbstractCard",null,new String[]{"public boolean isSeen=true;","public boolean isLocked;","public boolean isFlipped;","public float current_y=300.0f;"});
        CtClass hiddenCard=p.makeClass("thirdparty.HiddenCard",p.get("com.megacrit.cardcrawl.cards.AbstractCard"));hiddenCard.addConstructor(CtNewConstructor.defaultConstructor(hiddenCard));hiddenCard.addMethod(CtNewMethod.make("public void render(){throw new IllegalStateException(\"observer must never invoke renderer\");}",hiddenCard));bytes.put(hiddenCard.getName(),hiddenCard.toBytecode());
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.CardGroup",null,new String[]{"public java.util.ArrayList group=new java.util.ArrayList();"});
        fixture(p,bytes,"com.megacrit.cardcrawl.relics.AbstractRelic",null,new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.orbs.AbstractOrb",null,new String[]{"public String ID=\"EnemyDark\";","public String name=\"Dark\";","public String description=\"Deal 12 damage.\";","public int passiveAmount=6;","public int evokeAmount=12;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.core.Settings",null,new String[]{"public static boolean hideCards;","public static boolean hidePopupDetails;","public static float scale=1.0f;","public static int HEIGHT=1080;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.ui.buttons.PeekButton",null,new String[]{"public static boolean isPeeking;"});
        // Enum names are enough to exercise the native selected-card visibility branch.
        CtClass screen=p.makeClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen");
        screen.addField(CtField.make("public static final com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen NONE=new com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen();",screen));
        screen.addField(CtField.make("public static final com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen HAND_SELECT=new com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen();",screen));screen.addConstructor(CtNewConstructor.defaultConstructor(screen));bytes.put(screen.getName(),screen.toBytecode());
        fixture(p,bytes,"com.megacrit.cardcrawl.dungeons.AbstractDungeon",null,new String[]{"public static boolean isScreenUp;","public static com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen screen=com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen.NONE;"});
        fixture(p,bytes,"charbosses.bosses.AbstractCharBoss","com.megacrit.cardcrawl.monsters.AbstractMonster",new String[]{"public static Object boss;","public com.megacrit.cardcrawl.cards.CardGroup hand=new com.megacrit.cardcrawl.cards.CardGroup();","public Object cardInUse;","public Object energy;","public Object energyPanel=new Object();","public Object stance;","public java.util.ArrayList relics=new java.util.ArrayList();","public java.util.ArrayList orbs=new java.util.ArrayList();","public Object chosenArchetype=new Object();","public String futureDeck=\"secret\";"});
        fixture(p,bytes,"charbosses.bosses.Hermit.CharBossHermit","charbosses.bosses.AbstractCharBoss",new String[]{"public static com.megacrit.cardcrawl.cards.AbstractCard previewCard;","private boolean noRender;"});
        fixture(p,bytes,"charbosses.core.EnemyEnergyManager",null,new String[]{"public int energy=3;","public int energyMaster=99;"});
        fixture(p,bytes,"charbosses.ui.EnemyEnergyPanel",null,new String[]{"public static int totalCount=2;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.stances.AbstractStance",null,new String[]{"public String ID=\"Wrath\";","public String name=\"WRONG BASE NAME\";","public String description=\"WRONG BASE DESCRIPTION\";"});
        fixture(p,bytes,"charbosses.stances.AbstractEnemyStance","com.megacrit.cardcrawl.stances.AbstractStance",new String[]{"public String name=\"Native Wrath\";","public String description=\"Deal 1.5 times damage.\";","protected Object img;"});
        fixture(p,bytes,"charbosses.stances.EnWrathStance","charbosses.stances.AbstractEnemyStance",new String[0]);
        fixture(p,bytes,"charbosses.orbs.AbstractEnemyOrb","com.megacrit.cardcrawl.orbs.AbstractOrb",new String[]{"public boolean showValues=true;"});
        fixture(p,bytes,"charbosses.orbs.EnemyEmptyOrbSlot","charbosses.orbs.AbstractEnemyOrb",new String[0]);
        fixture(p,bytes,"automaton.EasyInfoDisplayPanel",null,new String[]{"public static java.util.ArrayList specialDisplays=new java.util.ArrayList();"});
        fixture(p,bytes,"charbosses.BossMechanicDisplayPanel","automaton.EasyInfoDisplayPanel",new String[]{"public static String mechanicName=\"Fortification\";","public static String mechanicDesc=\"Gain #b3 Block.\";"});
        for(String simple:new String[]{"BossObservation","NativeMechanicAccess","NativeMechanicAccess$Read","MechanicsCompleteness","PublicDescription"}){
            String name="communicationmod.observation."+simple;bytes.put(name,Files.readAllBytes(Paths.get(args[0],name.replace('.','/')+".class")));
        }
        ClassLoader loader=new ClassLoader(BossObservationTest.class.getClassLoader()){
            protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);if(c==null&&b!=null)c=defineClass(n,b,0,b.length);if(c==null)c=super.loadClass(n,resolve);if(resolve)resolveClass(c);return c;}}
        };
        Class<?> actorClass=loader.loadClass("charbosses.bosses.AbstractCharBoss"),cardClass=loader.loadClass("com.megacrit.cardcrawl.cards.AbstractCard"),settings=loader.loadClass("com.megacrit.cardcrawl.core.Settings"),dungeon=loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon"),panel=loader.loadClass("charbosses.BossMechanicDisplayPanel");
        Object actor=actorClass.newInstance(),card=cardClass.newInstance(),flipped=cardClass.newInstance();actorClass.getField("boss").set(null,actor);cardClass.getField("isFlipped").setBoolean(flipped,true);
        actorClass.getField("energy").set(actor,loader.loadClass("charbosses.core.EnemyEnergyManager").newInstance());actorClass.getField("stance").set(actor,loader.loadClass("charbosses.stances.EnWrathStance").newInstance());
        List<Object> cards=(List<Object>)actorClass.getField("hand").get(actor).getClass().getField("group").get(actorClass.getField("hand").get(actor));cards.add(card);cards.add(flipped);
        List<Object> relics=(List<Object>)actorClass.getField("relics").get(actor);relics.add(loader.loadClass("com.megacrit.cardcrawl.relics.AbstractRelic").newInstance());
        List<Object> orbs=(List<Object>)actorClass.getField("orbs").get(actor);Object orb=loader.loadClass("charbosses.orbs.AbstractEnemyOrb").newInstance();orbs.add(orb);orbs.add(loader.loadClass("charbosses.orbs.EnemyEmptyOrbSlot").newInstance());
        List<Object> panels=(List<Object>)loader.loadClass("automaton.EasyInfoDisplayPanel").getField("specialDisplays").get(null);panels.add(panel.newInstance());
        Map<Object,String> cardIds=new IdentityHashMap<>();
        int[] projected={0};Function<Object,Map<String,Object>> cardView=c->{projected[0]++;Map<String,Object> row=new LinkedHashMap<>();row.put("id",cardIds.getOrDefault(c,"public-card"));row.put("name","Visible Attack");row.put("description","Deal 6 damage.");row.put("description_complete",true);row.put("displayed_cost_text","1");row.put("displayed_cost_complete",true);row.put("intent","SECRET INTENT");row.put("available_energy",500);row.put("misc",4242);row.put("previews","SECRET PREVIEW");return row;};
        Function<Object,Map<String,Object>> relicView=r->{Map<String,Object> row=new LinkedHashMap<>();row.put("id","NativeRelic");return row;};
        Method add=loader.loadClass("communicationmod.observation.BossObservation").getMethod("addTo",Map.class,loader.loadClass("com.megacrit.cardcrawl.monsters.AbstractMonster"),Function.class,Function.class);
        Map<String,Object> nativeView=capture(add,actor,cardView,relicView);
        check(projected[0]==1,"flipped card never reaches projector");List<?> hand=(List<?>)nativeView.get("hand");check(hand.size()==2,"visible hand with masked card back");
        check("public-card".equals(((Map<?,?>)hand.get(0)).get("id")),"public card attached");check(!((Map<?,?>)hand.get(1)).containsKey("id"),"hidden identity withheld");
        check(!nativeView.toString().contains("SECRET")&&!nativeView.toString().contains("500")&&!nativeView.toString().contains("4242"),"intent, previews, player payment budget and internal fields withheld");
        check("2/3".equals(((Map<?,?>)nativeView.get("energy")).get("displayed_text")),"actual native energy text, not master budget");
        check(nativeView.containsKey("stance"),"native current stance is public despite null dormant image");
        check("Native Wrath".equals(((Map<?,?>)nativeView.get("stance")).get("name")),"enemy stance shadowed fields");
        check(((List<?>)nativeView.get("relics")).size()==1 && ((List<?>)nativeView.get("orbs")).size()==2,"native relic/orb UI attached");
        check(Boolean.TRUE.equals(((Map<?,?>)((List<?>)nativeView.get("orbs")).get(1)).get("empty_slot")),"empty slot has no invented amounts");
        check("Fortification".equals(((Map<?,?>)nativeView.get("mechanic_panel")).get("name")),"registered current native panel");
        check(Boolean.TRUE.equals(nativeView.get("information_complete")),"complete bounded fixture");
        check(cards.size()==2 && cardClass.getField("isFlipped").getBoolean(flipped) && actorClass.getField("chosenArchetype").get(actor)!=null,"observer preserves source state");
        settings.getField("hideCards").setBoolean(null,true);int previous=projected[0];Map<?,?> hidden=capture(add,actor,cardView,relicView);check(((List<?>)hidden.get("hand")).isEmpty() && projected[0]==previous,"native hideCards prevents identity reads");settings.getField("hideCards").setBoolean(null,false);
        cardClass.getField("isLocked").setBoolean(card,true);check(!((Map<?,?>)((List<?>)capture(add,actor,cardView,relicView).get("hand")).get(0)).containsKey("name"),"locked face hidden");cardClass.getField("isLocked").setBoolean(card,false);
        cardClass.getField("current_y").setFloat(card,2000.0f);check(!((Map<?,?>)((List<?>)capture(add,actor,cardView,relicView).get("hand")).get(0)).containsKey("id"),"native offscreen card face hidden");cardClass.getField("current_y").setFloat(card,300.0f);
        Object unsupported=loader.loadClass("thirdparty.HiddenCard").newInstance();cards.add(unsupported);Map<?,?> unknown=capture(add,actor,cardView,relicView);check(!((Map<?,?>)((List<?>)unknown.get("hand")).get(2)).containsKey("id")&&Boolean.FALSE.equals(unknown.get("information_complete")),"unknown face renderer fails incomplete without projecting hidden identity");cards.remove(unsupported);
        actorClass.getField("cardInUse").set(actor,card);loader.loadClass("com.megacrit.cardcrawl.ui.buttons.PeekButton").getField("isPeeking").setBoolean(null,true);check(((List<?>)capture(add,actor,cardView,relicView).get("hand")).size()==1,"in-use card hidden while peeking");loader.loadClass("com.megacrit.cardcrawl.ui.buttons.PeekButton").getField("isPeeking").setBoolean(null,false);actorClass.getField("cardInUse").set(actor,null);
        for(String field:new String[]{"isDead","isDying","isEscaping","escaped"}){actorClass.getField(field).setBoolean(actor,true);Map<?,?> dead=capture(add,actor,cardView,relicView);check(Boolean.FALSE.equals(dead.get("visible"))&&!dead.containsKey("hand")&&!dead.containsKey("energy")&&!dead.containsKey("mechanic_panel"),"dead/leaving boss withheld: "+field);actorClass.getField(field).setBoolean(actor,false);}
        dungeon.getField("isScreenUp").setBoolean(null,true);check(!capture(add,actor,cardView,relicView).containsKey("mechanic_panel"),"screen overlay hides panel");dungeon.getField("isScreenUp").setBoolean(null,false);
        settings.getField("hidePopupDetails").setBoolean(null,true);check(!capture(add,actor,cardView,relicView).containsKey("mechanic_panel"),"hidePopupDetails hides panel");settings.getField("hidePopupDetails").setBoolean(null,false);
        for(String text:new String[]{null,"","NORENDER"}){panel.getField("mechanicDesc").set(null,text);check(!capture(add,actor,cardView,relicView).containsKey("mechanic_panel"),"inactive panel withheld");}panel.getField("mechanicDesc").set(null,"Public mechanic");
        panels.clear();check(!capture(add,actor,cardView,relicView).containsKey("mechanic_panel"),"unregistered stale panel withheld");panels.add(panel.newInstance());
        orb.getClass().getField("showValues").setBoolean(orb,false);Map<?,?> maskedOrb=(Map<?,?>)((List<?>)capture(add,actor,cardView,relicView).get("orbs")).get(0);check(!maskedOrb.containsKey("evoke_amount")&&!maskedOrb.containsKey("description"),"native hidden orb values withheld");
        actorClass.getField("boss").set(null,actorClass.newInstance());Map<?,?> stale=capture(add,actor,cardView,relicView);check(!stale.containsKey("energy")&&!stale.containsKey("mechanic_panel")&&Boolean.FALSE.equals(stale.get("information_complete")),"global UI ownership checked, incomplete rather than fabricated");actorClass.getField("boss").set(null,actor);
        actorClass.getField("energy").set(actor,new Object());Map<?,?> missing=capture(add,actor,cardView,relicView);check(Boolean.FALSE.equals(missing.get("information_complete"))&&missing.containsKey("energy_unavailable_reason")&&missing.containsKey("hand"),"missing binding cannot erase independent public hand");
        for(int i=0;i<35;i++)cards.add(cardClass.newInstance());Map<?,?> bounded=capture(add,actor,cardView,relicView);check(((List<?>)bounded.get("hand")).size()==32&&Boolean.TRUE.equals(bounded.get("hand_truncated"))&&Boolean.FALSE.equals(bounded.get("information_complete")),"bounded hand reports truncation");
        Map<String,Object> ordinary=new LinkedHashMap<>();add.invoke(null,ordinary,loader.loadClass("com.megacrit.cardcrawl.monsters.AbstractMonster").newInstance(),cardView,relicView);check(!ordinary.containsKey("public_combat"),"ordinary monsters unchanged");
        Class<?> hermitClass=loader.loadClass("charbosses.bosses.Hermit.CharBossHermit");Object hermit=hermitClass.newInstance();
        actorClass.getField("boss").set(null,hermit);actorClass.getField("energy").set(hermit,loader.loadClass("charbosses.core.EnemyEnergyManager").newInstance());
        Object preview=cardClass.newInstance(),replacement=cardClass.newInstance();cardIds.put(preview,"roulette-a");cardIds.put(replacement,"roulette-b");hermitClass.getField("previewCard").set(null,preview);
        Map<?,?> roulette=(Map<?,?>)capture(add,hermit,cardView,relicView).get("roulette_preview");
        check(roulette!=null && Boolean.TRUE.equals(roulette.get("visible")) && "roulette-a".equals(((Map<?,?>)roulette.get("card")).get("id")),"rendered roulette preview captured separately from empty hand");
        check(!roulette.toString().contains("SECRET")&&!roulette.toString().contains("500"),"roulette reuses public card allowlist");
        hermitClass.getField("previewCard").set(null,replacement);roulette=(Map<?,?>)capture(add,hermit,cardView,relicView).get("roulette_preview");check("roulette-b".equals(((Map<?,?>)roulette.get("card")).get("id")),"roulette replacement reads current public object");
        Field noRender=hermitClass.getDeclaredField("noRender");noRender.setAccessible(true);
        for(String gate:new String[]{"noRender","wheelActive","hideCards","isSeen","isLocked","isFlipped","offscreen","missing"}){
            if(gate.equals("noRender"))noRender.setBoolean(hermit,true);
            else if(gate.equals("wheelActive"))actorClass.getField("wheelActive").setBoolean(hermit,false);
            else if(gate.equals("hideCards"))settings.getField("hideCards").setBoolean(null,true);
            else if(gate.equals("missing"))hermitClass.getField("previewCard").set(null,null);
            else if(gate.equals("offscreen"))cardClass.getField("current_y").setFloat(replacement,2000.0f);
            else cardClass.getField(gate).setBoolean(replacement,!gate.equals("isSeen"));
            previous=projected[0];roulette=(Map<?,?>)capture(add,hermit,cardView,relicView).get("roulette_preview");check(roulette!=null && Boolean.FALSE.equals(roulette.get("visible"))&&!roulette.containsKey("card")&&projected[0]==previous,"hidden preview never reaches projector: "+gate);
            noRender.setBoolean(hermit,false);actorClass.getField("wheelActive").setBoolean(hermit,true);settings.getField("hideCards").setBoolean(null,false);hermitClass.getField("previewCard").set(null,replacement);cardClass.getField("current_y").setFloat(replacement,300.0f);cardClass.getField("isSeen").setBoolean(replacement,true);cardClass.getField("isLocked").setBoolean(replacement,false);cardClass.getField("isFlipped").setBoolean(replacement,false);
        }
        hermitClass.getField("previewCard").set(null,unsupported);roulette=(Map<?,?>)capture(add,hermit,cardView,relicView).get("roulette_preview");check(!((Map<?,?>)roulette.get("card")).containsKey("id") && Boolean.FALSE.equals(capture(add,hermit,cardView,relicView).get("information_complete")),"unknown preview renderer reports incomplete without identity");
        hermitClass.getField("previewCard").set(null,replacement);actorClass.getField("boss").set(null,actor);Map<?,?> stalePreview=capture(add,hermit,cardView,relicView);check(stalePreview.containsKey("roulette_preview_unavailable_reason")&&!stalePreview.containsKey("roulette_preview"),"static preview cannot attach to stale boss");
        actorClass.getField("boss").set(null,hermit);for(String flag:new String[]{"isDead","isDying","isEscaping","escaped"}){actorClass.getField(flag).setBoolean(hermit,true);check(!capture(add,hermit,cardView,relicView).containsKey("roulette_preview"),"dead/leaving preview withheld: "+flag);actorClass.getField(flag).setBoolean(hermit,false);}
        check(hermitClass.getField("previewCard").get(null)==replacement&&!noRender.getBoolean(hermit),"preview observer preserves source fields");
        System.out.println("PASS: public boss hand/energy/stance/relic/orb/panel, hidden state and read-only bounded projection");
    }
    @SuppressWarnings("unchecked") static Map<String,Object> capture(Method m,Object a,Function<?,?> cards,Function<?,?> relics)throws Exception{Map<String,Object> root=new LinkedHashMap<>();m.invoke(null,root,a,cards,relics);return (Map<String,Object>)root.get("public_combat");}
    static void fixture(ClassPool p,Map<String,byte[]> bytes,String name,String parent,String[] fields)throws Exception{CtClass c=parent==null?p.makeClass(name):p.makeClass(name,p.get(parent));for(String f:fields)c.addField(CtField.make(f,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));bytes.put(name,c.toBytecode());}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}




