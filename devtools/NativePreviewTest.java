import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Graphics-free fixtures around the production public-preview projection, never a game launch. */
public final class NativePreviewTest {
    static final ClassPool pool=new ClassPool(true);
    static final Map<String,byte[]> classes=new HashMap<>();
    static ClassLoader loader;
    static Method cards,monsters;
    static Function<Object,Map<String,Object>> view;
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        fixture("com.megacrit.cardcrawl.cards.AbstractCard",null,
            "public boolean isSeen=true;","public boolean isLocked;","public boolean isFlipped;","public boolean upgraded;",
            "public String cardID=\"card\";","public String name=\"Card\";","public String description=\"Do not discard. Deal 3 damage.\";",
            "public boolean exhaust;","public int cost=1;","public int timesUpgraded;","public com.megacrit.cardcrawl.cards.AbstractCard cardsToPreview;");
        fixture("com.megacrit.cardcrawl.cards.CardGroup",null,"public java.util.ArrayList group=new java.util.ArrayList();");
        fixture("com.megacrit.cardcrawl.helpers.PowerTip",null,"public String header=\"Stasis Egg\";","public String body=\"Lose 3 HP. Do not discard.\";");
        fixture("com.megacrit.cardcrawl.relics.AbstractRelic",null,"public String relicId=\"egg\";","public String name=\"Stasis Egg\";","public java.util.ArrayList tips=new java.util.ArrayList();");
        fixture("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton",null,"public com.megacrit.cardcrawl.relics.AbstractRelic relicToPreview;","public boolean isDisabled;","public void renderRelicPreview(){throw new AssertionError((Object)\"Never render to observe\");}");
        fixture("guardian.ui.RelicPreviewButton","com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton","public com.megacrit.cardcrawl.relics.AbstractRelic r;");
        fixture("thirdparty.RelicPreviewButton","guardian.ui.RelicPreviewButton");
        fixture("com.megacrit.cardcrawl.helpers.CardLibrary",null,"public static java.util.HashMap cards=new java.util.HashMap();");
        // This projection fixture has no captured upgrade lifecycle. The real service,
        // native upgrade bodies, cache and source immutability have their own GameTest.
        fixture("communicationmod.observation.CardUpgradeObservation",null,"public static int galleryCalls;",
            "public static void offerGallery(java.util.Map g,java.util.List sources){if(((java.util.List)g.get(\"cards\")).size()!=sources.size())throw new AssertionError((Object)\"gallery row/source binding\");galleryCalls++;g.put(\"cards_complete\",Boolean.FALSE);g.put(\"unavailable_reason\",\"upgraded_gallery_context_not_captured\");}");
        fixture("com.megacrit.cardcrawl.characters.AbstractPlayer$PlayerClass",null);
        fixture("com.megacrit.cardcrawl.characters.AbstractPlayer",null,"public com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass chosenClass=new com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass();");
        fixture("com.megacrit.cardcrawl.monsters.AbstractMonster",null,"public String id=\"slime\";");
        fixture("com.megacrit.cardcrawl.dungeons.AbstractDungeon",null,"public static com.megacrit.cardcrawl.characters.AbstractPlayer player;");
        fixture("collector.CollectorChar$Enums",null,"public static com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass THE_COLLECTOR=new com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass();");
        fixture("collector.CollectorCollection",null,"public static java.util.HashMap collectionPool=new java.util.HashMap();",
            "public static com.megacrit.cardcrawl.cards.CardGroup collection=new com.megacrit.cardcrawl.cards.CardGroup();",
            "public static Object getCollectedCard(Object ignored){throw new AssertionError((Object)\"No native factories or RNG\");}");
        fixture("com.megacrit.cardcrawl.localization.UIStrings",null,"public String[] TEXT=new String[]{\"Collectible. NL \",\"Collectible\"};");
        fixture("collector.cardmods.CollectedCardMod",null,"public static com.megacrit.cardcrawl.localization.UIStrings uiStrings=new com.megacrit.cardcrawl.localization.UIStrings();");
        fixture("sneckomod.cards.unknowns.AbstractUnknownCard","com.megacrit.cardcrawl.cards.AbstractCard",
            "public static java.util.ArrayList unknownReplacements=new java.util.ArrayList();",
            "public com.megacrit.cardcrawl.cards.AbstractCard lastUnknownRoll;",
            "public java.util.ArrayList myList(){return unknownReplacements;}");
        fixture("sneckomod.cards.unknowns.Unknown","sneckomod.cards.unknowns.AbstractUnknownCard");
        fixture("thirdparty.Unknown","sneckomod.cards.unknowns.AbstractUnknownCard",
            "public java.util.ArrayList myList(){throw new AssertionError((Object)\"Do not call unaudited getters\");}");
        fixture("slimebound.cards.SplitSpecialist","com.megacrit.cardcrawl.cards.AbstractCard","private java.util.ArrayList cardList=new java.util.ArrayList();",
            "public java.util.ArrayList choiceList(){throw new AssertionError((Object)\"This native method shuffles game RNG\");}");
        fixture("charbosses.bosses.AbstractBossDeckArchetype",null,"public int actNum=2;");
        fixture("charbosses.bosses.Ironclad.CharBossIronclad","com.megacrit.cardcrawl.monsters.AbstractMonster",
            "public charbosses.bosses.AbstractBossDeckArchetype chosenArchetype=new charbosses.bosses.AbstractBossDeckArchetype();");
        loader=new ClassLoader(NativePreviewTest.class.getClassLoader()) {
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    Class<?> c=findLoadedClass(name);byte[] b=classes.get(name);
                    if(c==null&&b==null&&name.startsWith("communicationmod.observation."))try{b=Files.readAllBytes(Paths.get(args[0],name.replace('.','/')+".class"));}catch(java.io.IOException absent){}
                    if(c==null&&b!=null)c=defineClass(name,b,0,b.length);
                    if(c==null)c=super.loadClass(name,resolve);if(resolve)resolveClass(c);return c;
                }
            }
        };
        try {
            cards=loader.loadClass("communicationmod.observation.CardPreviewObservation").getMethod("addTo",Map.class,loader.loadClass("com.megacrit.cardcrawl.cards.AbstractCard"),Function.class);
            monsters=loader.loadClass("communicationmod.observation.CollectorPreviewObservation").getMethod("addTo",Map.class,loader.loadClass("com.megacrit.cardcrawl.monsters.AbstractMonster"),Function.class);
        } catch(ClassNotFoundException missing) {throw new AssertionError("Missing public card/collectible preview observation",missing);}
        view=c->{try{Map<String,Object> row=new LinkedHashMap<>();for(String key:new String[]{"cardID","name","description","cost","exhaust"})row.put(key.equals("cardID")?"id":key.equals("exhaust")?"exhausts":key,get(c,key));row.put("description_complete",true);return row;}catch(Exception e){throw new AssertionError(e);}};
        Object source=card("source"),preview=card("lick");set(source,"cardsToPreview",preview);
        Map<?,?> shown=preview(capture(source,false));check(((List<?>)shown.get("cards")).size()==1,"existing generated-card preview exposed");
        check(Boolean.TRUE.equals(shown.get("cards_complete")),"single complete preview");
        set(preview,"isFlipped",true);shown=preview(capture(source,false));check(((List<?>)shown.get("cards")).isEmpty()&&Boolean.FALSE.equals(shown.get("cards_complete")),"hidden preview withheld with incomplete flag");
        set(preview,"isFlipped",false);set(source,"isLocked",true);check(capture(source,false).isEmpty(),"hidden parent has no preview refs");set(source,"isLocked",false);
        Object ordinary=card("ordinary");check(capture(ordinary,false).isEmpty(),"unrelated cards unchanged");
        set(source,"cardsToPreview",source);shown=preview(capture(source,false));check(Boolean.FALSE.equals(shown.get("cards_complete")),"self-cycle is explicit, never recursed");
        set(source,"cardsToPreview",preview);
        Function<Object,Map<String,Object>> simple=view;
        view=c->{Map<String,Object> row=simple.apply(c);try{cards.invoke(null,row,c,view);}catch(Exception e){throw new AssertionError(e);}return row;};
        set(preview,"cardsToPreview",source);shown=preview(capture(source,false));check(shown.toString().length()<2000,"nested cycles bounded");view=simple;set(preview,"cardsToPreview",null);
        Map<String,Object> library=(Map<String,Object>)get(loader.loadClass("com.megacrit.cardcrawl.helpers.CardLibrary"),"cards");library.put("lick",preview);library.put("Reaper",card("Reaper"));library.put("collector:DefaultCollectibleCard",card("collector:DefaultCollectibleCard"));
        Object unknown=make("sneckomod.cards.unknowns.Unknown");List<String> ids=(List<String>)get(loader.loadClass("sneckomod.cards.unknowns.AbstractUnknownCard"),"unknownReplacements");ids.add("lick");set(unknown,"lastUnknownRoll",card("SECRET_FUTURE"));
        shown=preview(capture(unknown,false));check(shown.toString().contains("lick")&&!shown.toString().contains("SECRET_FUTURE"),"gallery is public candidates, never saved future roll");
        check(!Boolean.TRUE.equals(shown.get("order_visible")),"gallery order not a future draw order");
        set(unknown,"upgraded",true);shown=preview(capture(unknown,false));check(Boolean.FALSE.equals(shown.get("cards_complete"))&&shown.toString().contains("upgraded_gallery"),"never mislabel base templates as upgraded variants");
        check(get(loader.loadClass("communicationmod.observation.CardUpgradeObservation"),"galleryCalls").equals(1),"upgraded public catalog delegates once to bounded preview service");
        set(unknown,"upgraded",false);check(preview(capture(make("thirdparty.Unknown"),false)).get("status").equals("unavailable"),"unknown gallery subclass refused");
        ids.add("missing");shown=preview(capture(unknown,false));check(Boolean.FALSE.equals(shown.get("cards_complete")),"missing library entry is not silently complete");ids.remove("missing");
        for(int i=0;i<300;i++)ids.add("lick");shown=preview(capture(unknown,false));check(((List<?>)shown.get("cards")).size()<=256,"gallery projection bounded");ids.clear();ids.add("lick");
        Object split=make("slimebound.cards.SplitSpecialist");Field splitList=split.getClass().getDeclaredField("cardList");splitList.setAccessible(true);((List<Object>)splitList.get(split)).add(preview);check(preview(capture(split,false)).toString().contains("lick"),"private cached public rotating candidates retained without calling RNG getter");
        Object actor=make("com.megacrit.cardcrawl.characters.AbstractPlayer"),monster=make("com.megacrit.cardcrawl.monsters.AbstractMonster");set(loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon"),"player",actor);
        Map<String,String> mapping=(Map<String,String>)get(loader.loadClass("collector.CollectorCollection"),"collectionPool");mapping.put("slime","lick");
        check(capture(monster,true).isEmpty(),"Collector-only information absent for unrelated characters");set(actor,"chosenClass",get(loader.loadClass("collector.CollectorChar$Enums"),"THE_COLLECTOR"));
        Map<?,?> collected=(Map<?,?>)capture(monster,true).get("collectible_preview"),collectedCard=(Map<?,?>)collected.get("card");
        check("lick".equals(collectedCard.get("id"))&&Boolean.TRUE.equals(collectedCard.get("exhausts")),"native collectible mapping and forced Exhaust preserved");
        check(collectedCard.get("description").toString().contains("Do not discard. Deal 3 damage.")&&collectedCard.get("description").toString().contains("Collectible"),"native prefix preserves negation and numbers");
        check(Boolean.FALSE.equals(get(preview,"exhaust"))&&!get(preview,"description").toString().contains("Collectible"),"catalog source remains untouched");
        Object boss=make("charbosses.bosses.Ironclad.CharBossIronclad");set(boss,"id","boss");collected=(Map<?,?>)capture(boss,true).get("collectible_preview");check(((Map<?,?>)collected.get("card")).get("id").equals("Reaper"),"pinned native public boss preview mapping");
        set(actor,"chosenClass",make("com.megacrit.cardcrawl.characters.AbstractPlayer$PlayerClass"));((List<Object>)get(get(loader.loadClass("collector.CollectorCollection"),"collection"),"group")).add(preview);check(capture(monster,true).containsKey("collectible_preview"),"native mixed-run collection visibility");
        set(preview,"isLocked",true);collected=(Map<?,?>)capture(monster,true).get("collectible_preview");check(collected.get("status").equals("unavailable")&&!collected.containsKey("card"),"hidden collectible source never serialized");
        eventRelics();
        System.out.println("PASS: public preview visibility, source immutability, cycles, gallery variants/bounds, collectible mapping, event relic tips and mixed-run gates");
    }
    @SuppressWarnings("unchecked") static void eventRelics()throws Exception {
        Method project;
        try {project=loader.loadClass("communicationmod.observation.EventPreviewObservation").getMethod("addRelicTo",Map.class,loader.loadClass("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton"));}
        catch(ClassNotFoundException|NoSuchMethodException missing){throw new AssertionError("Event relic preview projection missing",missing);}
        Object relic=make("com.megacrit.cardcrawl.relics.AbstractRelic"),tip=make("com.megacrit.cardcrawl.helpers.PowerTip");
        List<Object> tips=(List<Object>)get(relic,"tips");tips.add(tip);
        for(String name:new String[]{"com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton","guardian.ui.RelicPreviewButton"}) {
            Object button=make(name);String field=name.startsWith("guardian")?"r":"relicToPreview";
            Map<String,Object> out=new LinkedHashMap<>();project.invoke(null,out,button);check(out.isEmpty(),"absent relic has no preview ref");
            set(button,field,relic);set(button,"isDisabled",true);project.invoke(null,out,button);
            Map<?,?> preview=(Map<?,?>)out.get("relic_preview");check(preview!=null,"public cached event relic preview");
            check("egg".equals(preview.get("id")) && Boolean.TRUE.equals(preview.get("tooltips_complete")),"native relic identity and complete cached tips");
            Map<?,?> shown=(Map<?,?>)((List<?>)preview.get("tooltips")).get(0);
            check("Lose 3 HP. Do not discard.".equals(shown.get("description")),"numeric effect and negation preserved even on disabled hoverable option");
            check(tips.size()==1 && "Lose 3 HP. Do not discard.".equals(get(tip,"body")),"observation leaves native tips unchanged");
            set(button,field,null);out.clear();project.invoke(null,out,button);check(out.isEmpty(),"cleared preview is not cached across state");
        }
        Object unknown=make("thirdparty.RelicPreviewButton");set(unknown,"r",relic);Map<String,Object> out=new LinkedHashMap<>();project.invoke(null,out,unknown);check(out.isEmpty(),"unknown renderer subclass withheld");
        Object button=make("guardian.ui.RelicPreviewButton");set(button,"r",relic);
        for(int i=0;i<40;i++)tips.add(tip);project.invoke(null,out,button);
        Map<?,?> preview=(Map<?,?>)out.get("relic_preview");check(((List<?>)preview.get("tooltips")).size()==32 && Boolean.FALSE.equals(preview.get("tooltips_complete")),"oversized cached tooltips explicitly incomplete");
        tips.clear();tips.add(null);out.clear();project.invoke(null,out,button);
        check(Boolean.FALSE.equals(((Map<?,?>)out.get("relic_preview")).get("tooltips_complete")),"null cached tooltip is not complete");
    }
    static Map<?,?> preview(Map<?,?> data){Object value=data.get("previews");check(value instanceof Map,"missing card preview directory");return (Map<?,?>)value;}
    static Map<String,Object> capture(Object value,boolean monster)throws Exception {Map<String,Object> out=new LinkedHashMap<>();(monster?monsters:cards).invoke(null,out,value,view);return out;}
    static Object make(String name)throws Exception{return loader.loadClass(name).newInstance();}
    static Object card(String id)throws Exception{Object c=make("com.megacrit.cardcrawl.cards.AbstractCard");set(c,"cardID",id);return c;}
    static Object get(Object o,String key)throws Exception{return (o instanceof Class?(Class<?>)o:o.getClass()).getField(key).get(o instanceof Class?null:o);}
    static void set(Object o,String key,Object value)throws Exception{(o instanceof Class?(Class<?>)o:o.getClass()).getField(key).set(o instanceof Class?null:o,value);}
    static void fixture(String name,String parent,String...members)throws Exception {CtClass c=parent==null?pool.makeClass(name):pool.makeClass(name,pool.get(parent));for(String s:members)if(s.contains("){"))c.addMethod(CtNewMethod.make(s,c));else c.addField(CtField.make(s,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));classes.put(name,c.toBytecode());}
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
}
