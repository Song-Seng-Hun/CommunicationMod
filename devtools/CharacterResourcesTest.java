import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.io.*;

/** Production projectors with field-only graphics fixtures and selected actual installed method bodies. */
public final class CharacterResourcesTest {
    static ClassPool p=new ClassPool(true),nativePool=new ClassPool(true);
    static Map<String,byte[]> bytes=new HashMap<>();static ClassLoader loader;static Method add,details;
    public static void main(String[] args)throws Exception {
        nativePool.insertClassPath(args[1]);nativePool.insertClassPath(args[2]);
        fixture("com.megacrit.cardcrawl.cards.AbstractCard",null,
            "public boolean isSeen=true;","public boolean isLocked;","public boolean isFlipped;","public String cardID=\"card\";","public String name=\"Card\";",
            "public java.util.UUID uuid=java.util.UUID.randomUUID();","public java.util.ArrayList tags=new java.util.ArrayList();");
        fixture("com.megacrit.cardcrawl.cards.CardGroup",null,"public java.util.ArrayList group=new java.util.ArrayList();");
        fixture("com.megacrit.cardcrawl.orbs.AbstractOrb",null,"public String name=\"Orb\";");
        fixture("com.megacrit.cardcrawl.stances.AbstractStance",null,"public String ID=\"stance\";","public String description=\"native cached technique\";");
        fixture("com.megacrit.cardcrawl.characters.AbstractPlayer",null,"public int currentHealth=17;","public java.util.ArrayList orbs=new java.util.ArrayList();",
            "public com.megacrit.cardcrawl.stances.AbstractStance stance;","public boolean hasPower(String id){return false;}");
        fixture("communicationmod.observation.CombatObservation",null,"public static boolean combat;","public static boolean inCombat(){return combat;}");
        for(String name:new String[]{"NewReserves","EssenceSystem"}) {
            String cls="collector.util."+name;fixture(cls,null,"public static int "+(name.equals("NewReserves")?"curReserves=4;":"curEssence=23;"));
            nativeMethod(cls,name.equals("NewReserves")?"reserveCount":"essenceCount");
        }
        fixture("collector.CollectorChar","com.megacrit.cardcrawl.characters.AbstractPlayer");
        fixture("collector.CollectorCollection",null,"public static com.megacrit.cardcrawl.cards.CardGroup collection=new com.megacrit.cardcrawl.cards.CardGroup();",
            "public static com.megacrit.cardcrawl.cards.CardGroup combatCollection=new com.megacrit.cardcrawl.cards.CardGroup();");
        fixture("gremlin.patches.GremlinMobState",null,"public java.util.ArrayList gremlins=new java.util.ArrayList();","public java.util.ArrayList gremlinHP=new java.util.ArrayList();",
            "public java.util.ArrayList enslaved=new java.util.ArrayList();","public boolean inCombat;");
        nativeMethod("gremlin.patches.GremlinMobState","getGremlinHP");nativeMethod("gremlin.patches.GremlinMobState","isEnslaved");
        fixture("gremlin.characters.GremlinCharacter","com.megacrit.cardcrawl.characters.AbstractPlayer","public String currentGremlin=\"a\";","public boolean nob;",
            "public gremlin.patches.GremlinMobState mobState=new gremlin.patches.GremlinMobState();");
        fixture("gremlin.orbs.GremlinStandby","com.megacrit.cardcrawl.orbs.AbstractOrb","public String assetFolder=\"b\";","public int hp=9;");
        fixture("awakenedOne.util.Wiz",null,"public static int POWERS_TO_AWAKEN=4;","public static boolean conjure;","public static boolean awakened;",
            "public static boolean isInCombat(){return communicationmod.observation.CombatObservation.combat;}","public static boolean hasConjure(){return conjure;}","public static boolean isAwakened(){return awakened;}");
        fixture("awakenedOne.actions.ConjureAction",null,"public static int conjuresThisCombat;");
        fixture("awakenedOne.powers.DemonGlyphPower",null,"public static String POWER_ID=\"glyph\";");
        fixture("awakenedOne.AwakenedOneMod",null,"public static int powersThisCombat=8;","public static Object UP_NEXT=new Object();");
        fixture("awakenedOne.AwakenedOneChar","com.megacrit.cardcrawl.characters.AbstractPlayer");
        fixture("awakenedOne.ui.OrbitingSpells",null,"public static java.util.ArrayList spellCards=new java.util.ArrayList();");
        fixture("champ.stances.AbstractChampStance","com.megacrit.cardcrawl.stances.AbstractStance","public boolean charged=true;","public float[] animAlphaBySlot=new float[]{0f,0f,0f};","public boolean[] useBrightTexture=new boolean[]{true,true,false};");
        nativeMethod("champ.stances.AbstractChampStance","getRemainingChargeCount");
        fixture("champ.stances.DefensiveStance","champ.stances.AbstractChampStance");
        fixture("slimebound.characters.SlimeboundCharacter","com.megacrit.cardcrawl.characters.AbstractPlayer","public boolean puddleForm=true;");
        fixture("guardian.cards.AbstractGuardianCard","com.megacrit.cardcrawl.cards.AbstractCard","public Integer socketCount=Integer.valueOf(2);","public java.util.ArrayList sockets=new java.util.ArrayList();","public java.lang.Enum thisGemsType;");
        fixture("theHexaghost.ghostflames.AbstractGhostflame",null);
        fixture("theHexaghost.GhostflameHelper",null,"public static theHexaghost.ghostflames.AbstractGhostflame activeGhostFlame;");
        fixture("theHexaghost.ghostflames.InfernoGhostflame","theHexaghost.ghostflames.AbstractGhostflame","public boolean charged;","public int energySpentThisTurn;",
            "public String[] DESCRIPTIONS=new String[]{\"charged\",\"three\",\"two\",\"one\",\"remaining\",\"inactive\",\"damage\",\"!\",\"active\"};","public int getEffectCount(){return 6;}");
        nativeMethod("theHexaghost.ghostflames.InfernoGhostflame","getDescription");
        loader=new ClassLoader(CharacterResourcesTest.class.getClassLoader()) {
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);byte[] b=bytes.get(name);
                    if(c==null && b==null && name.startsWith("communicationmod.observation."))try{b=Files.readAllBytes(Paths.get(args[0],name.replace('.','/')+".class"));}catch(IOException missing){}
                    if(c==null && b!=null)c=defineClass(name,b,0,b.length);if(c==null)c=super.loadClass(name,resolve);if(resolve)resolveClass(c);return c;}
            }
        };
        add=loader.loadClass("communicationmod.observation.CharacterResources").getMethod("addTo",Map.class,loader.loadClass("com.megacrit.cardcrawl.characters.AbstractPlayer"),Function.class);
        details=loader.loadClass("communicationmod.observation.CharacterResources").getMethod("cardDetails",loader.loadClass("com.megacrit.cardcrawl.cards.AbstractCard"));
        Object collector=make("collector.CollectorChar"),collection=get(loader.loadClass("collector.CollectorCollection"),"collection");
        Object z=card("z"),a=card("a");list(collection,"group").add(z);list(collection,"group").add(a);
        Map<?,?> c=capture(collector);check(c.get("reserves").equals(4)&&c.get("essence").equals(23),"actual native resource getters");
        Map<?,?> deck=(Map<?,?>)c.get("collection");check(Boolean.FALSE.equals(deck.get("order_visible")),"collection draw order hidden");
        check("a".equals(((Map<?,?>)((List<?>)deck.get("cards")).get(0)).get("id")),"sorted public contents");
        check(list(collection,"group").get(0)==z,"collection source order unchanged");check(!c.containsKey("combat_collection"),"combat collection hidden outside combat");
        set(a,"isFlipped",true);check(Boolean.FALSE.equals(((Map<?,?>)capture(collector).get("collection")).get("cards_complete")),"masked collection card omitted explicitly");
        for(int i=0;i<135;i++)list(collection,"group").add(card(String.format("card-%03d",i)));
        deck=(Map<?,?>)capture(collector).get("collection");
        check(((List<?>)deck.get("cards")).size()==136,"public collection contents beyond 128 retained");
        list(collection,"group").add(null);
        deck=(Map<?,?>)capture(collector).get("collection");
        check(deck!=null && ((List<?>)deck.get("cards")).size()==136,"null and hidden entries excluded before sorting");
        check(list(collection,"group").get(0)==z && list(collection,"group").get(1)==a,"large source order unchanged");
        Object sameHigh=card("same"),sameLow=card("same");
        set(sameHigh,"uuid",UUID.fromString("00000000-0000-0000-0000-000000000002"));
        set(sameLow,"uuid",UUID.fromString("00000000-0000-0000-0000-000000000001"));
        list(collection,"group").add(sameHigh);list(collection,"group").add(sameLow);
        List<?> sorted=(List<?>)((Map<?,?>)capture(collector).get("collection")).get("cards");
        List<Object> same=new ArrayList<>();for(Object row:sorted)if("same".equals(((Map<?,?>)row).get("id")))same.add(((Map<?,?>)row).get("uuid"));
        check(same.equals(Arrays.asList(get(sameLow,"uuid"),get(sameHigh,"uuid"))),"duplicate IDs sorted by UUID not native relative order");
        Object gremlin=make("gremlin.characters.GremlinCharacter"),mob=get(gremlin,"mobState");
        list(mob,"gremlins").addAll(Arrays.asList("a","b","c"));list(mob,"gremlinHP").addAll(Arrays.asList(12,11,10));list(mob,"enslaved").add("c");
        check(((Map<?,?>)((List<?>)capture(gremlin).get("gremlins")).get(0)).get("hp").equals(12),"out-of-combat actual native mob HP");
        set(mob,"inCombat",true);list(gremlin,"orbs").add(make("gremlin.orbs.GremlinStandby"));
        List<?> formation=(List<?>)capture(gremlin).get("gremlins");check(((Map<?,?>)formation.get(0)).get("hp").equals(17),"active combat HP current not stale mob value");
        check(((Map<?,?>)formation.get(1)).get("hp").equals(9),"standby HP current");check(Boolean.TRUE.equals(((Map<?,?>)formation.get(2)).get("enslaved")),"enslaved distinguished from dead");
        set(loader.loadClass("communicationmod.observation.CombatObservation"),"combat",true);
        Object combatCollection=get(loader.loadClass("collector.CollectorCollection"),"combatCollection");
        for(int i=131;i>=0;i--)list(combatCollection,"group").add(card(String.format("combat-%03d",i)));
        Object hiddenCombat=card("hidden-combat");set(hiddenCombat,"isFlipped",true);list(combatCollection,"group").add(hiddenCombat);
        Map<?,?> combatView=capture(collector),combatDeck=(Map<?,?>)combatView.get("combat_collection");
        check(((List<?>)combatDeck.get("cards")).size()==132 && Boolean.FALSE.equals(combatDeck.get("cards_complete")),"independent large combat collection excludes masked rows");
        check("combat-000".equals(((Map<?,?>)((List<?>)combatDeck.get("cards")).get(0)).get("id")),"combat collection sorted");
        check("combat-131".equals(get(list(combatCollection,"group").get(0),"cardID")),"combat source order unchanged");
        check(((List<?>)((Map<?,?>)combatView.get("collection")).get("cards")).size()==138,"persistent collection remains independent");
        Object other=make("com.megacrit.cardcrawl.characters.AbstractPlayer");check(!capture(other).containsKey("spells"),"hidden spell panel withheld");
        set(loader.loadClass("awakenedOne.util.Wiz"),"conjure",true);
        Object spell=card("spell"),duplicate=card("spell");list(duplicate,"tags").add(get(loader.loadClass("awakenedOne.AwakenedOneMod"),"UP_NEXT"));
        list(loader.loadClass("awakenedOne.ui.OrbitingSpells"),"spellCards").addAll(Arrays.asList(spell,duplicate));
        Map<?,?> spells=capture(other);Map<?,?> slot=(Map<?,?>)((List<?>)spells.get("spells")).get(0);
        check(slot.get("count").equals(2)&&Boolean.TRUE.equals(slot.get("up_next")),"mixed-character spells group count and displayed next marker");
        check(Boolean.TRUE.equals(spells.get("spells_complete")),"visible spell groups complete");
        set(duplicate,"isFlipped",true);
        check(Boolean.FALSE.equals(capture(other).get("spells_complete")),"masked spell omission explicitly incomplete");
        check(spells.get("awakening_progress").equals(3),"awakening pips capped exactly like renderer");
        set(other,"stance",make("champ.stances.DefensiveStance"));check(((Map<?,?>)capture(other).get("champ_stance")).get("technique_charges").equals(2),"actual native remaining technique charge getter");
        check(Boolean.TRUE.equals(capture(make("slimebound.characters.SlimeboundCharacter")).get("puddle_form")),"slime form");
        Object socket=make("guardian.cards.AbstractGuardianCard");list(socket,"sockets").add(Thread.State.NEW);set(socket,"thisGemsType",Thread.State.RUNNABLE);
        Map<?,?> sockets=(Map<?,?>)details.invoke(null,socket);check(sockets.get("socket_capacity").equals(2)&&((List<?>)sockets.get("sockets")).size()==1,"socket colors and capacity");
        set(socket,"isFlipped",true);check(((Map<?,?>)details.invoke(null,socket)).isEmpty(),"hidden sockets withheld");
        Object inferno=make("theHexaghost.ghostflames.InfernoGhostflame");Method format=loader.loadClass("communicationmod.observation.PlayerMechanicsObservation").getMethod("infernoDescription",String[].class,boolean.class,boolean.class,int.class,int.class);
        PrintStream original=System.out;try {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            for(boolean active:new boolean[]{false,true})for(boolean charged:new boolean[]{false,true})for(int spent=-2;spent<=6;spent++){
                set(loader.loadClass("theHexaghost.GhostflameHelper"),"activeGhostFlame",active?inferno:null);set(inferno,"charged",charged);set(inferno,"energySpentThisTurn",spent);
                String nativeText=(String)inferno.getClass().getMethod("getDescription").invoke(inferno);
                check(nativeText.equals(format.invoke(null,get(inferno,"DESCRIPTIONS"),charged,active,spent,6)),"actual native Inferno prose equivalence");
            }
        }finally{System.setOut(original);}
        System.out.println("PASS: Collector/Gremlin/Awakened/Champ/Guardian/Slime resources; native read-only getters; mixed/hidden state; 36 native Inferno prose cases");
    }
    static void fixture(String name,String parent,String... members)throws Exception {
        CtClass c=parent==null?p.makeClass(name):p.makeClass(name,p.get(parent));
        for(String member:members)if(member.contains("){"))c.addMethod(CtNewMethod.make(member,c));else c.addField(CtField.make(member,c));
        c.addConstructor(CtNewConstructor.defaultConstructor(c));bytes.put(name,c.toBytecode());c.defrost();
    }
    static void nativeMethod(String type,String method)throws Exception{CtClass c=p.get(type);c.addMethod(CtNewMethod.copy(nativePool.get(type).getDeclaredMethod(method),c,null));bytes.put(type,c.toBytecode());c.defrost();}
    static Object make(String type)throws Exception{return loader.loadClass(type).newInstance();}
    static Object get(Object o,String key)throws Exception{return (o instanceof Class?(Class<?>)o:o.getClass()).getField(key).get(o instanceof Class?null:o);}
    static void set(Object o,String key,Object value)throws Exception{(o instanceof Class?(Class<?>)o:o.getClass()).getField(key).set(o instanceof Class?null:o,value);}
    @SuppressWarnings("unchecked")static List<Object> list(Object o,String key)throws Exception{return (List<Object>)get(o,key);}
    static Object card(String id)throws Exception{Object card=make("com.megacrit.cardcrawl.cards.AbstractCard");set(card,"cardID",id);return card;}
    static Map<?,?> capture(Object player)throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();Function<Object,Map<String,Object>> project=card->{Map<String,Object> row=new HashMap<>();try{row.put("id",get(card,"cardID"));row.put("uuid",get(card,"uuid"));}catch(Exception e){throw new RuntimeException(e);}return row;};
        add.invoke(null,out,player,project);return out;
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
