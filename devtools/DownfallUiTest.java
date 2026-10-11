import javassist.*;
import javassist.expr.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Production adapters and installed native handler bodies; isolated graphics fixtures, never game startup. */
public final class DownfallUiTest {
    static final String O="communicationmod.observation.", D="com.megacrit.cardcrawl.dungeons.AbstractDungeon",
        H="com.megacrit.cardcrawl.helpers.Hitbox", I="com.megacrit.cardcrawl.helpers.input.InputHelper",
        C="com.megacrit.cardcrawl.cards.AbstractCard", R="com.megacrit.cardcrawl.rewards.RewardItem",
        E="com.megacrit.cardcrawl.events.AbstractEvent", T="com.megacrit.cardcrawl.ui.FtueTip";
    static final String[] TUTORIALS={"Guardian","Hexaghost","Charboss","Collector","SlimeBoss","Champ","Automaton","Gremlins","Snecko","AwakenedOne"};
    static final String BASE_MATCH="com.megacrit.cardcrawl.events.shrines.GremlinMatchGame";
    static final String[] WHEELS={"com.megacrit.cardcrawl.events.shrines.GremlinWheelGame","downfall.events.GremlinWheelGame_Rest","downfall.events.GremlinWheelGame_Evil"};
    final ClassPool p=new ClassPool(true), nativePool=new ClassPool(true);
    final Map<String,byte[]> bytes=new HashMap<>();
    final List<CtClass> fixtures=new ArrayList<>();
    final Path compiled;
    final String nativeJar;
    ClassLoader loader;
    int assertions;
    NativeRewardCases nativeRewards;
    DownfallUiTest(String[] args)throws Exception {
        compiled=Paths.get(args[1]);nativeJar=args[2];p.insertClassPath(compiled.toString());nativePool.insertClassPath(args[2]);nativePool.insertClassPath(args[3]);nativePool.insertClassPath(args[4]);
    }
    public static void main(String[] args)throws Exception {
        DownfallUiTest t=new DownfallUiTest(args);
        String adapter;
        switch(args[0]) {
            case "tutorial":adapter="DownfallTutorialUi";break;
            case "reward":adapter="CollectorRewardUi";break;
            case "wheel":adapter="RestWheelUi";break;
            case "match":adapter="EvilMatchUi";break;
            case "base-match":adapter="EvilMatchUi";break;
            default:throw new IllegalArgumentException("Unknown UI test group: "+args[0]);
        }
        check(Files.exists(t.compiled.resolve((O+adapter).replace('.','/')+".class")),"Missing "+adapter+" offered native UI path");
        t.common();
        if(args[0].equals("tutorial"))t.tutorial();else if(args[0].equals("reward"))t.rewards();else if(args[0].equals("wheel"))t.wheel();else t.match(args[0].equals("base-match")?BASE_MATCH:"downfall.events.GremlinMatchGame_Evil");
        check(t.assertions>0,"Empty UI test group: "+args[0]);
        System.out.println("PASS: "+args[0]+" production/native UI ("+t.assertions+" assertions); no game initialization");
    }
    void common()throws Exception {
        make("com.megacrit.cardcrawl.core.Settings",null,"public static boolean isTouchScreen;","public static boolean isControllerMode;","public static int WIDTH=1920;","public static float scale=1f;");
        make(I,null,"public static boolean justClickedLeft;","public static boolean justClickedRight;","public static boolean justReleasedClickLeft;");
        make(H,null,"public boolean clicked;","public boolean hovered;","public boolean clickStarted;","public boolean justHovered;","public float cX;","public float cY=50f;",
            "public void update(){communicationmod.observation.NativeUiInput.afterHitbox(this);}","public void move(float x,float y){cX=x;cY=y;}");
        make("com.badlogic.gdx.graphics.Color",null,"public float a=0.8f;");
        CtClass graphics=p.makeInterface("com.badlogic.gdx.Graphics");graphics.addMethod(CtNewMethod.abstractMethod(CtClass.floatType,"getDeltaTime",new CtClass[0],new CtClass[0],graphics));
        fixtures.add(graphics);
        make("fixture.Graphics",null,"public float getDeltaTime(){return 0.1f;}");p.get("fixture.Graphics").addInterface(graphics);
        make("com.badlogic.gdx.Gdx",null,"public static com.badlogic.gdx.Graphics graphics=new fixture.Graphics();");
        make("com.badlogic.gdx.math.Interpolation",null,"public static com.badlogic.gdx.math.Interpolation fade=new com.badlogic.gdx.math.Interpolation();","public float apply(float a,float b,float t){return a;}");
        make("com.megacrit.cardcrawl.helpers.MathHelper",null,"public static float cardLerpSnap(float a,float b){return b;}");
        make("com.megacrit.cardcrawl.helpers.controller.CInputAction",null,"public boolean isJustPressed(){return false;}","public void unpress(){}");
        make("com.megacrit.cardcrawl.helpers.controller.CInputActionSet",null,"public static com.megacrit.cardcrawl.helpers.controller.CInputAction proceed=new com.megacrit.cardcrawl.helpers.controller.CInputAction();");
        make("com.megacrit.cardcrawl.audio.SoundMaster",null,"public long play(String s){return 0L;}");
        make("com.megacrit.cardcrawl.core.CardCrawlGame",null,"public static boolean isPopupOpen;","public static com.megacrit.cardcrawl.audio.SoundMaster sound=new com.megacrit.cardcrawl.audio.SoundMaster();");
        make("com.megacrit.cardcrawl.ui.buttons.ProceedButton",null,"public boolean isHovered;","public boolean isHidden;","public float current_x;","public float target_x;","public String label=\"Next\";","public "+H+" hb=new "+H+"();","public void setLabel(String s){label=s;}","public void hide(){isHidden=true;}");
        make("com.megacrit.cardcrawl.core.OverlayMenu",null,"public com.megacrit.cardcrawl.ui.buttons.ProceedButton proceedButton=new com.megacrit.cardcrawl.ui.buttons.ProceedButton();");
        make(T,null,"public void update(){}");
        make(E,null,"public void update(){}");
        make("com.megacrit.cardcrawl.events.AbstractImageEvent",E,"public void update(){}");
        make("com.megacrit.cardcrawl.rooms.AbstractRoom",null,"public "+E+" event;");
        make("com.megacrit.cardcrawl.characters.AbstractPlayer",null);
        make(D+"$CurrentScreen",null,"public static "+D+"$CurrentScreen NONE=new "+D+"$CurrentScreen();","public static "+D+"$CurrentScreen FTUE=new "+D+"$CurrentScreen();","public static "+D+"$CurrentScreen COMBAT_REWARD=new "+D+"$CurrentScreen();");
        make(D,null,"public static "+D+"$CurrentScreen screen="+D+"$CurrentScreen.NONE;","public static boolean isScreenUp;","public static "+T+" ftue;","public static com.megacrit.cardcrawl.characters.AbstractPlayer player=new com.megacrit.cardcrawl.characters.AbstractPlayer();","public static com.megacrit.cardcrawl.core.OverlayMenu overlayMenu=new com.megacrit.cardcrawl.core.OverlayMenu();","public static java.util.ArrayList effectList=new java.util.ArrayList();","public static java.util.ArrayList topLevelEffects=new java.util.ArrayList();","public static com.megacrit.cardcrawl.rooms.AbstractRoom room=new com.megacrit.cardcrawl.rooms.AbstractRoom();","public static com.megacrit.cardcrawl.rooms.AbstractRoom getCurrRoom(){return room;}","public static void closeCurrentScreen(){screen="+D+"$CurrentScreen.NONE;isScreenUp=false;}");
        make("communicationmod.GameStateConverter",null,"public static String removeTextFormatting(String s){return s;}");
        // Existing room safety is separately regression tested; only environment readiness is a fixture here.
        make(O+"RoomUi",null,"public static boolean ready(){return !com.megacrit.cardcrawl.core.CardCrawlGame.isPopupOpen && !"+I+".justClickedLeft && !"+I+".justClickedRight && !"+I+".justReleasedClickLeft;}");
    }
    void tutorial()throws Exception {
        for(String name:TUTORIALS){
            String n="downfall.tutorials."+name+"Tutorials";
            CtClass c=p.makeClass(n,p.get(T));
            fixtures.add(c);
            for(String f:new String[]{"public static String[] LABEL=new String[]{\"Next\",\"Done\",\"Footer\"};","private static String txt1=\"Native first page\";","private static String txt2=\"Native second page\";","private int closeScreen=-1;","private int currentSlot;","private float scrollTimer;","private float x;","private float startX;","private float targetX;","private com.badlogic.gdx.graphics.Color screen=new com.badlogic.gdx.graphics.Color();"})c.addField(CtField.make(f,c));
            c.addConstructor(CtNewConstructor.defaultConstructor(c));
            c.addMethod(CtNewMethod.copy(nativePool.get(n).getDeclaredMethod("update"),c,null));
        }
        make("fixture.UnknownTutorial",T,"public void update(){}");
        start(O+"DownfallTutorialUi");
        for(String name:TUTORIALS){
            Object tip=newObject("downfall.tutorials."+name+"Tutorials");set(D,"ftue",tip);set(D,"screen",get(D+"$CurrentScreen","FTUE"));set(D,"isScreenUp",true);set(get(get(D,"overlayMenu"),"proceedButton"),"isHidden",false);
            JsonObject v=new JsonObject(),status=new JsonObject();List<?> actions=(List<?>)call(O+"DownfallTutorialUi","capture",v,status);
            expect(actions.size()==1,"tutorial offered "+name);expect(v.getAsJsonObject("tutorial").get("body").getAsString().equals("Native first page"),"native page text");
            Object first=actions.get(0);execute(first);expect(((Integer)get(tip,"currentSlot"))==-1,"original native update advances page");reject(first,"stale tutorial page");
            expect(((List<?>)call(O+"DownfallTutorialUi","capture",new JsonObject(),new JsonObject())).isEmpty(),"scroll transition withheld");
            set(tip,"scrollTimer",0f);v=new JsonObject();actions=(List<?>)call(O+"DownfallTutorialUi","capture",v,new JsonObject());expect(v.getAsJsonObject("tutorial").get("page").getAsInt()==2,"second page index");expect(v.getAsJsonObject("tutorial").get("body").getAsString().equals("Native second page"),"second page text");
            execute(actions.get(0));expect(get(D,"screen")==get(D+"$CurrentScreen","NONE"),"native final close");expect(!((Boolean)get(I,"justClickedLeft")),"press restored");
        }
        set(D,"ftue",newObject("fixture.UnknownTutorial"));expect(!((Boolean)call(O+"DownfallTutorialUi","supports",get(D,"ftue"))),"unknown tutorial refused");
        wiring("communicationmod.observation.RunUi","DownfallTutorialUi.capture");
    }
    void rewards()throws Exception {
        make(C,null,"public String name=\"Collected\";","public String cardID=\"test\";","public java.util.UUID uuid=java.util.UUID.randomUUID();","public String rawDescription=\"public text\";");
        // The real detached-copy/cache semantics are exercised by UpgradePreviewGameTest.
        make(O+"CardUpgradeObservation",null,"public static java.util.Map snapshotWithUpgrade("+C+" c){java.util.HashMap m=new java.util.HashMap();m.put(\"name\",c.name);m.put(\"description\",c.rawDescription);java.util.HashMap p=new java.util.HashMap();p.put(\"status\",\"available\");p.put(\"source\",\"current_decision_cache\");m.put(\"upgrade_preview\",p);return m;}");
        make(R+"$RewardType",null,"public String value;","public String name(){return value;}");
        make(R,null,"public "+R+"$RewardType type=new "+R+"$RewardType();","public boolean isDone;","public boolean ignoreReward;","public float flashTimer;","public "+H+" hb=new "+H+"();","public java.util.ArrayList cards=new java.util.ArrayList();","public void update(){hb.update();if(hb.clicked)isDone=true;}","public boolean claimReward(){return false;}");
        make("com.megacrit.cardcrawl.vfx.AbstractGameEffect",null,"public boolean isDone;","public void update(){}");
        CtClass glow=make("com.megacrit.cardcrawl.vfx.RewardGlowEffect","com.megacrit.cardcrawl.vfx.AbstractGameEffect");glow.addConstructor(CtNewConstructor.make("public RewardGlowEffect(float x,float y){}",glow));
        p.get("com.megacrit.cardcrawl.audio.SoundMaster").addMethod(CtNewMethod.make("public long playA(String s,float f){return 0L;}",p.get("com.megacrit.cardcrawl.audio.SoundMaster")));
        p.get("com.megacrit.cardcrawl.helpers.controller.CInputActionSet").addField(CtField.make("public static com.megacrit.cardcrawl.helpers.controller.CInputAction select=new com.megacrit.cardcrawl.helpers.controller.CInputAction();",p.get("com.megacrit.cardcrawl.helpers.controller.CInputActionSet")));
        // Retain the installed update and its lambda/bootstrap metadata; replace graphics constructors only.
        CtClass custom=p.makeClass(new java.io.ByteArrayInputStream(nativePool.get("basemod.abstracts.CustomReward").toBytecode()));fixtures.add(custom);
        for(CtConstructor ctor:custom.getDeclaredConstructors())custom.removeConstructor(ctor);
        custom.removeMethod(custom.getDeclaredMethod("render"));
        custom.addConstructor(CtNewConstructor.make("public CustomReward(){super();effects=new java.util.ArrayList();}",custom));
        make("collector.util.EssenceSystem",null,"public static int essence=5;","public static int essenceCount(){return essence;}","public static void changeEssence(int n){essence+=n;}");
        make("com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect",null);
        CtClass effect=p.get("com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect");effect.addConstructor(CtNewConstructor.make("public ShowCardAndObtainEffect("+C+" c,float x,float y){}",effect));
        p.get(I).addField(CtField.make("public static int mX;",p.get(I)));p.get(I).addField(CtField.make("public static int mY;",p.get(I)));
        for(String n:new String[]{"collector.util.EssenceReward","collector.util.CollectibleCardReward","downfall.util.RareCardReward"}){
            CtClass c=make(n,n.contains("Rare")?R:"basemod.abstracts.CustomReward",n.contains("Collectible")?"public "+C+" card=new "+C+"();":"public int amount=2;");
            if(!n.contains("Rare"))c.addMethod(CtNewMethod.copy(nativePool.get(n).getDeclaredMethod("claimReward"),c,null));
        }
        String sealName="theHexaghost.util.SealSealReward";
        CtClass sealClass=make(sealName,"basemod.abstracts.CustomReward","public static String[] TEXT=new String[]{\"Seal\",\"Choose a seal\"};");
        sealClass.addMethod(CtNewMethod.copy(nativePool.get(sealName).getDeclaredMethod("claimReward"),sealClass,null));
        p.get(D+"$CurrentScreen").addField(CtField.make("public static "+D+"$CurrentScreen CARD_REWARD=new "+D+"$CurrentScreen();",p.get(D+"$CurrentScreen")));
        make("com.megacrit.cardcrawl.screens.CardRewardScreen",null,"public java.util.ArrayList opened;","public "+R+" owner;","public void open(java.util.ArrayList cards,"+R+" reward,String text){opened=cards;owner=reward;"+D+".screen="+D+"$CurrentScreen.CARD_REWARD;}");
        p.get(D).addField(CtField.make("public static "+D+"$CurrentScreen previousScreen;",p.get(D)));
        p.get(D).addField(CtField.make("public static com.megacrit.cardcrawl.screens.CardRewardScreen cardRewardScreen=new com.megacrit.cardcrawl.screens.CardRewardScreen();",p.get(D)));
        make("fixture.UnknownReward",R);make("fixture.DerivedCollectible","collector.util.CollectibleCardReward");make("fixture.DerivedSeal",sealName);
        nativeRewards=new NativeRewardCases(this,nativeJar,System.getProperty("native.reward.manifest"));
        nativeRewards.prepare(this);
        start(O+"CollectorRewardUi");
        Object essence=newObject("collector.util.EssenceReward");type(essence,"COLLECTOR_ESSENCE");Object card=newObject("collector.util.CollectibleCardReward");type(card,"COLLECTOR_COLLECTIBLECARDREWARD");Object rare=newObject("downfall.util.RareCardReward");type(rare,"CARD");
        expect((Boolean)call(O+"CollectorRewardUi","supports",essence),"essence supported");expect((Boolean)call(O+"CollectorRewardUi","supports",card),"collectible supported");expect((Boolean)call(O+"CollectorRewardUi","supports",rare),"rare supported");
        type(card,"CARD");expect(!((Boolean)call(O+"CollectorRewardUi","supports",card)),"mismatched native type refused");type(card,"COLLECTOR_COLLECTIBLECARDREWARD");
        Object hidden=newObject(C);set(hidden,"name","SECRET_UNOPENED_RARE_CHOICE");((List)get(rare,"cards")).add(hidden);
        JsonObject rareDetails=(JsonObject)call(O+"CollectorRewardUi","details",rare),deferred=new JsonObject();deferred.addProperty("choice_details_available_after_claim",true);
        expect(rareDetails.equals(deferred),"unopened rare reward exposes only deferred-choice flag, never hidden cards or count");
        ((List)get(rare,"cards")).clear();expect(rareDetails.equals(call(O+"CollectorRewardUi","details",rare)),"hidden choice changes cannot affect unopened reward details");
        final int[] cardReads={0};nativePool.get("downfall.util.RareCardReward").getDeclaredMethod("render").instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isReader()&&f.getFieldName().equals("cards"))cardReads[0]++;}});
        expect(cardReads[0]==0,"installed RareCardReward renderer does not show candidate cards");
        expect(nativePool.get("downfall.util.RareCardReward").getDeclaredMethods("claimReward").length==0,"rare claim remains inherited native reward handler");
        Object other=newObject("fixture.UnknownReward");type(other,"CARD");expect(!((Boolean)call(O+"CollectorRewardUi","supports",other)),"unknown refused");other=newObject("fixture.DerivedCollectible");type(other,"COLLECTOR_COLLECTIBLECARDREWARD");expect(!((Boolean)call(O+"CollectorRewardUi","supports",other)),"subclass refused");
        JsonObject detail=(JsonObject)call(O+"CollectorRewardUi","details",card);expect(detail.get("cost").getAsInt()==3,"native pinned essence price");expect(detail.get("essence").getAsInt()==5,"current currency");expect(detail.getAsJsonObject("card").get("description").getAsString().equals("public text"),"current card detail");
        JsonObject upgrade=detail.getAsJsonObject("card").getAsJsonObject("upgrade_preview");
        expect(upgrade!=null && "available".equals(upgrade.get("status").getAsString()) && "current_decision_cache".equals(upgrade.get("source").getAsString()),"collectible delegates upgrade facts to current decision preview service");
        set("collector.util.EssenceSystem","essence",2);expect(!((Boolean)call(O+"CollectorRewardUi","claimable",card)),"insufficient essence");set("collector.util.EssenceSystem","essence",5);
        set(card,"ignoreReward",true);expect(!((Boolean)call(O+"CollectorRewardUi","claimable",card)),"ignored refused");set(card,"ignoreReward",false);set(card,"isDone",true);expect(!((Boolean)call(O+"CollectorRewardUi","claimable",card)),"pending refused");set(card,"isDone",false);
        Object hb=get(card,"hb");set(hb,"clicked",true);expect(!((Boolean)call(O+"CollectorRewardUi","claimable",card)),"pending physical click refused");set(hb,"clicked",false);
        call(O+"CollectorRewardUi","click",card);expect((Boolean)get(card,"isDone"),"native reward input consumed");
        expect(((Integer)get("collector.util.EssenceSystem","essence"))==5 && ((List<?>)get(D,"topLevelEffects")).isEmpty(),"input defers all outcomes to native claim handler");
        // Execute the installed claim body that the normal reward-screen update owns.
        expect((Boolean)invoke(card,"claimReward"),"native collectible claim");expect(((Integer)get("collector.util.EssenceSystem","essence"))==2,"native claim charges exactly three");expect(((List<?>)get(D,"topLevelEffects")).size()==1,"native obtain effect queued");
        expect((Boolean)invoke(essence,"claimReward"),"native essence claim");expect(((Integer)get("collector.util.EssenceSystem","essence"))==4,"native essence award");
        wiring("communicationmod.observation.RewardUi","CollectorRewardUi.click");wiring("communicationmod.observation.RewardUi","CollectorRewardUi.details");
        wiring(O+"CollectorRewardUi","CardUpgradeObservation.snapshotWithUpgrade");
        Object seal=newObject(sealName);type(seal,"SEALCARD");
        expect((Boolean)call(O+"CollectorRewardUi","supports",seal),"native SealSealReward SEALCARD supported");
        type(seal,"CARD");expect(!(Boolean)call(O+"CollectorRewardUi","supports",seal),"seal wrong type denied");type(seal,"SEALCARD");
        Object derivedSeal=newObject("fixture.DerivedSeal");type(derivedSeal,"SEALCARD");expect(!(Boolean)call(O+"CollectorRewardUi","supports",derivedSeal),"seal subclass denied");
        ((List)get(seal,"cards")).add(hidden);
        expect(deferred.equals(call(O+"CollectorRewardUi","details",seal)),"unopened seal hides candidates and count");
        set(D,"screen",get(D+"$CurrentScreen","COMBAT_REWARD"));
        call(O+"CollectorRewardUi","click",seal);
        expect((Boolean)get(seal,"isDone") && get(get(D,"cardRewardScreen"),"opened")==null,"seal input only marks native pending claim");
        expect(!(Boolean)call(O+"CollectorRewardUi","claimable",seal),"pending seal cannot repeat");
        expect(!(Boolean)invoke(seal,"claimReward"),"installed seal claim defers completion");
        expect(get(D,"screen")==get(D+"$CurrentScreen","CARD_REWARD") && get(D,"previousScreen")==get(D+"$CurrentScreen","COMBAT_REWARD"),"installed seal claim opens native card reward with return screen");
        expect(get(get(D,"cardRewardScreen"),"opened")==get(seal,"cards") && get(get(D,"cardRewardScreen"),"owner")==seal,"native selection owns exact seal candidates and reward");
        nativeRewards.verify(this);
    }
    void wheel()throws Exception {
        p.get("com.megacrit.cardcrawl.core.Settings").addField(CtField.make("public static float OPTION_Y=500f;",p.get("com.megacrit.cardcrawl.core.Settings")));
        for(String n:WHEELS){
        String screen=n+"$CUR_SCREEN";fixtures.add(p.makeClass(new java.io.ByteArrayInputStream(nativePool.get(screen).toBytecode())));
        CtClass c=make(n,"com.megacrit.cardcrawl.events.AbstractImageEvent","public "+screen+" screen="+screen+".INTRO;","public boolean startSpin;","public boolean buttonPressed;","public boolean doneSpinning;","public boolean finishSpin;","public boolean bounceIn;","public float bounceTimer;","public float animTimer=2f;","public float spinVelocity;","public float wheelAngle;","public float resultAngle;","public float tmpAngle;","public float imgY;","public static float TARGET_Y;","public boolean fadingOut;","public "+H+" buttonHb=new "+H+"();","public void updatePosition(){}","public void purgeLogic(){}","public void preApplyResult(){}","public void buttonEffect(int i){}");
        // Native wheel update uses its graphics timer only; no result/RNG is exposed by the adapter.
        c.addMethod(CtNewMethod.copy(nativePool.get(n).getDeclaredMethod("update"),c,null));
        c.addField(CtField.make("public com.megacrit.cardcrawl.events.GenericEventDialog imageEventText=new com.megacrit.cardcrawl.events.GenericEventDialog();",c));
        }
        make("com.megacrit.cardcrawl.events.GenericEventDialog",null,"public static boolean selected;","public static boolean waitForInput=true;","public static int getSelectedOption(){return -1;}","public void clearAllDialogs(){}");
        make("communicationmod.ChoiceScreenUtils$EventDialogType",null,"public static communicationmod.ChoiceScreenUtils$EventDialogType NONE=new communicationmod.ChoiceScreenUtils$EventDialogType();");
        make("communicationmod.ChoiceScreenUtils",null,"public static communicationmod.ChoiceScreenUtils$EventDialogType getEventDialogType(){return communicationmod.ChoiceScreenUtils$EventDialogType.NONE;}");
        make("fixture.DerivedWheel","downfall.events.GremlinWheelGame_Evil");
        start(O+"RestWheelUi");
        for(String n:WHEELS){
        Object wheel=newObject(n),room=get(D,"room");set(room,"event",wheel);set(wheel,"startSpin",true);
        expect(nativePool.get(n).getSuperclass().getName().equals("com.megacrit.cardcrawl.events.AbstractImageEvent"),"wheel exact native superclass");
        expect((Boolean)call(O+"RestWheelUi","isIntro",wheel,0),n+": only acknowledged intro qualifies");expect(!((Boolean)call(O+"RestWheelUi","isIntro",wheel,1)),"other choice does not authorize");set(wheel,"screen",get(n+"$CUR_SCREEN","COMPLETE"));expect(!((Boolean)call(O+"RestWheelUi","isIntro",wheel,0)),"result does not authorize new spin");set(wheel,"screen",get(n+"$CUR_SCREEN","INTRO"));
        Object adapter=newObject(O+"RestWheelUi");expect(((List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject())).isEmpty(),"wheel without acknowledged intro refused");
        invoke(adapter,"acceptedIntro",wheel);List<?> actions=(List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject());expect(actions.size()==1,"acknowledged spin offered");
        Object action=actions.get(0);set(wheel,"bounceTimer",1f);reject(action,"wheel animation changed");set(wheel,"bounceTimer",0f);
        actions=(List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject());execute(actions.get(0));expect((Boolean)get(wheel,"buttonPressed"),"installed wheel update receives scoped press");expect(!((Boolean)get(I,"justClickedLeft")),"wheel input restored");expect(((List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject())).isEmpty(),"spin cannot repeat");
        set(wheel,"buttonPressed",false);set(room,"event",newObject(n));expect(((List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject())).isEmpty(),"different event not authorized");
        set(room,"event",wheel);invoke(adapter,"acceptedIntro",wheel);actions=(List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject());
        set(D,"room",newObject("com.megacrit.cardcrawl.rooms.AbstractRoom"));set(get(D,"room"),"event",wheel);reject(actions.get(0),"room identity changed");set(D,"room",room);
        }
        Object derived=newObject("fixture.DerivedWheel");expect(!(Boolean)call(O+"RestWheelUi","isIntro",derived,0),"wheel subclass denied");
        wiring("communicationmod.observation.RunUi","RestWheelUi.capture");wiring("communicationmod.observation.RunUi","RestWheelUi.acceptedIntro");
        orderedCalls("communicationmod.observation.RunUi","communicationmod.ChoiceScreenUtils.makeEventChoice",O+"RestWheelUi.acceptedIntro");
        orderedCalls("communicationmod.ChoiceScreenUtils",O+"DialogueObservation.claimEventChoice","communicationmod.ChoiceScreenUtils.getActiveEventButtons","makeEventChoice");
    }
    void match(String n)throws Exception {
        String group="com.megacrit.cardcrawl.cards.CardGroup",screen=n+"$CUR_SCREEN";
        expect(nativePool.get(n).getSuperclass().getName().equals("com.megacrit.cardcrawl.events.AbstractImageEvent"),"exact match event superclass");
        fixtures.add(p.makeClass(new java.io.ByteArrayInputStream(nativePool.get(screen).toBytecode())));
        p.get("com.megacrit.cardcrawl.core.Settings").addField(CtField.make("public static int HEIGHT=1080;",p.get("com.megacrit.cardcrawl.core.Settings")));
        make(C,null,"public boolean isFlipped=true;","public boolean isSeen=true;","public boolean isLocked;","public String cardID=\"SECRET\";","public String name=\"SECRET_NAME\";","public java.util.UUID uuid=java.util.UUID.randomUUID();","public float drawScale;","public float targetDrawScale;","public float target_x;","public float target_y;","public "+H+" hb=new "+H+"();");
        p.get(C).addMethod(CtNewMethod.make("public "+C+" makeCopy(){return new "+C+"();}",p.get(C)));
        make(group,null,"public java.util.ArrayList group=new java.util.ArrayList();");
        make(O+"CardUpgradeObservation",null,"public static java.util.Map snapshot("+C+" card){if(card.isFlipped)throw new AssertionError((Object)\"Concealed card was inspected\");java.util.HashMap m=new java.util.HashMap();m.put(\"id\",card.cardID);m.put(\"name\",card.name);return m;}");
        CtClass effect=make("com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect",null);effect.addConstructor(CtNewConstructor.make("public ShowCardAndObtainEffect("+C+" c,float x,float y){}",effect));
        CtClass event=make(n,"com.megacrit.cardcrawl.events.AbstractImageEvent","public "+screen+" screen="+screen+".RULE_EXPLANATION;","public "+group+" cards=new "+group+"();","public "+C+" chosenCard;","public "+C+" hoveredCard;","public boolean cardFlipped;","public boolean gameDone;","public float waitTimer;","public int attemptCount=5;","public int cardsMatched;","public java.util.List matchedCards=new java.util.ArrayList();");
        event.addMethod(CtNewMethod.copy(nativePool.get(n).getDeclaredMethod("updateMatchGameLogic"),event,null));
        make("fixture.DerivedMatch",n);
        make("communicationmod.ChoiceScreenUtils$EventDialogType",null,"public static communicationmod.ChoiceScreenUtils$EventDialogType NONE=new communicationmod.ChoiceScreenUtils$EventDialogType();","public static communicationmod.ChoiceScreenUtils$EventDialogType IMAGE=new communicationmod.ChoiceScreenUtils$EventDialogType();");
        make("communicationmod.ChoiceScreenUtils",null,"public static communicationmod.ChoiceScreenUtils$EventDialogType dialog=communicationmod.ChoiceScreenUtils$EventDialogType.NONE;","public static communicationmod.ChoiceScreenUtils$EventDialogType getEventDialogType(){return dialog;}");
        if(n.equals(BASE_MATCH)) {
            make("com.megacrit.cardcrawl.events.shrines.GremlinWheelGame","com.megacrit.cardcrawl.events.AbstractImageEvent");
            make("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton",null,"public String msg=\"Read rules\";");
            make("communicationmod.patches.GremlinMatchGamePatch",null,"public static java.util.HashMap cardPositions;","public static java.util.Set revealedCards;","public static java.util.ArrayList getOrderedCards(){throw new IllegalStateException(\"legacy Match cache was not initialized\");}");
            CtClass choices=p.get("communicationmod.ChoiceScreenUtils");
            choices.addField(CtField.make("public static java.util.ArrayList buttons=new java.util.ArrayList();",choices));
            choices.addMethod(CtNewMethod.make("public static java.util.ArrayList getActiveEventButtons(){return buttons;}",choices));
            choices.addMethod(CtNewMethod.make("public static String getOptionName(String msg){return msg;}",choices));
            ClassPool production=new ClassPool(true);production.insertClassPath(compiled.toString());
            choices.addMethod(CtNewMethod.copy(production.get("communicationmod.ChoiceScreenUtils").getDeclaredMethod("getEventScreenChoices"),choices,null));
        }
        start(O+"EvilMatchUi");Object game=newObject(n),room=get(D,"room"),adapter=newObject(O+"EvilMatchUi");set(room,"event",game);
        List<Object> board=(List<Object>)get(get(game,"cards"),"group");for(int i=0;i<12;i++)board.add(newObject(C));
        expect((Boolean)call(O+"EvilMatchUi","isIntro",game,0),"match acknowledged start recognized");expect(!(Boolean)call(O+"EvilMatchUi","isIntro",game,1),"leave choice never authorizes");
        if(n.equals(BASE_MATCH)){set(game,"screen",get(screen,"INTRO"));expect(!(Boolean)call(O+"EvilMatchUi","isIntro",game,0),"base introduction is not the separately discussed rules page");set(game,"screen",get(screen,"RULE_EXPLANATION"));}
        set(game,"screen",get(screen,"PLAY"));expect(((List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject())).isEmpty(),"unacknowledged match cannot act");
        if(n.equals(BASE_MATCH)) {
            expect(((List<?>)call("communicationmod.ChoiceScreenUtils","getEventScreenChoices")).isEmpty(),"v2 observation never depends on the legacy Match patch cache");
            List<Object> buttons=(List<Object>)get("communicationmod.ChoiceScreenUtils","buttons");buttons.add(newObject("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton"));
            expect(((List<?>)call("communicationmod.ChoiceScreenUtils","getEventScreenChoices")).equals(Arrays.asList("read rules")),"ordinary dialog choices remain available");buttons.clear();
            System.setProperty("communicationmod.play_control","false");
            try {call("communicationmod.ChoiceScreenUtils","getEventScreenChoices");throw new AssertionError("legacy branch was removed");}
            catch(RuntimeException failure){expect(failure.getCause() instanceof IllegalStateException && failure.getCause().getMessage().contains("legacy Match cache"),"legacy branch retained outside v2 play control");}
            finally {System.setProperty("communicationmod.play_control","true");}
            wiring("communicationmod.GameStateConverter","ChoiceScreenUtils.getEventScreenChoices");
        }
        set(game,"screen",get(screen,"RULE_EXPLANATION"));invoke(adapter,"acceptedIntro",game);set(game,"screen",get(screen,"PLAY"));
        JsonObject view=new JsonObject();List<?> actions=(List<?>)invoke(adapter,"capture",view,new JsonObject());expect(actions.size()==12,"twelve offered hidden positions, not wheel action");
        expect(!view.toString().contains("SECRET")&&!view.toString().contains("uuid"),"concealed identities and names absent");
        JsonArray rows=view.getAsJsonObject("match_controls").getAsJsonArray("cards");expect(rows.get(5).getAsJsonObject().get("position").getAsInt()==5,"public board positions");
        String first=view.toString();set(board.get(0),"cardID","SECRET_CHANGED");set(board.get(0),"name","SECRET_CHANGED_NAME");view=new JsonObject();invoke(adapter,"capture",view,new JsonObject());expect(first.equals(view.toString()),"hidden identity mutations never affect projection");
        set(board.get(0),"cardID","SECRET");set(board.get(0),"name","SECRET_NAME");
        Object old=actions.get(1),flip=actions.get(0);set(game,"waitTimer",1f);reject(flip,"animation blocks stale action");set(game,"waitTimer",0f);
        set("com.megacrit.cardcrawl.core.CardCrawlGame","isPopupOpen",true);reject(flip,"popup blocks action");set("com.megacrit.cardcrawl.core.CardCrawlGame","isPopupOpen",false);
        set(I,"justClickedLeft",true);reject(flip,"human input preserved");expect((Boolean)get(I,"justClickedLeft"),"human press unchanged");set(I,"justClickedLeft",false);
        execute(flip);expect(!(Boolean)get(board.get(0),"isFlipped")&&(Boolean)get(board.get(1),"isFlipped"),"installed native body flips only selected card");expect(!(Boolean)get(I,"justClickedLeft"),"scoped press restored");reject(flip,"same flip cannot replay");reject(old,"old second-card action cannot cross first reveal");
        view=new JsonObject();actions=(List<?>)invoke(adapter,"capture",view,new JsonObject());expect(actions.size()==11,"revealed position not offered");expect(view.getAsJsonObject("match_controls").getAsJsonArray("cards").get(0).getAsJsonObject().has("card"),"face-up native card details exposed");
        execute(actions.get(0));expect(((Float)get(game,"waitTimer"))==1f,"native matched pair starts native wait");expect(((List<?>)get(D,"effectList")).isEmpty(),"click never synthesizes award");
        expect(((List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject())).isEmpty(),"pair resolution blocks further flip");
        set(game,"waitTimer",0.01f);invokePrivate(game,"updateMatchGameLogic");expect(board.size()==10&&((Integer)get(game,"attemptCount"))==4,"native timer resolves pair and attempt count");expect(((List<?>)get(D,"effectList")).size()==1,"native resolution owns award");
        view=new JsonObject();actions=(List<?>)invoke(adapter,"capture",view,new JsonObject());expect(actions.size()==10,"remaining native board is actionable");expect(view.getAsJsonObject("match_controls").getAsJsonArray("cards").get(0).getAsJsonObject().get("position").getAsInt()==2,"positions survive pair removal");
        Object remaining=actions.get(0);Collections.swap(board,0,1);reject(remaining,"board reorder invalidates action");Collections.swap(board,0,1);
        set(game,"attemptCount",3);reject(remaining,"changed attempt count invalidates action");set(game,"attemptCount",4);
        set(game,"gameDone",true);reject(remaining,"completed game blocks action");set(game,"gameDone",false);
        set(D,"room",newObject("com.megacrit.cardcrawl.rooms.AbstractRoom"));set(get(D,"room"),"event",game);reject(remaining,"room identity changed");set(D,"room",room);
        Object replacement=newObject(n);set(room,"event",replacement);expect(((List<?>)invoke(adapter,"capture",new JsonObject(),new JsonObject())).isEmpty(),"authorization not transferred to another event");
        set(room,"event",newObject("fixture.DerivedMatch"));expect(!(Boolean)call(O+"EvilMatchUi","isIntro",get(room,"event"),0),"unverified subclass denied");
        wiring("communicationmod.observation.RunUi","EvilMatchUi.capture");orderedCalls("communicationmod.observation.RunUi","communicationmod.ChoiceScreenUtils.makeEventChoice",O+"EvilMatchUi.acceptedIntro");
    }
    void invokePrivate(Object target,String name)throws Exception {Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}
    void orderedCalls(String owner,String first,String second,String... onlyMethod)throws Exception {
        ClassPool scan=new ClassPool(true);scan.insertClassPath(compiled.toString());boolean found=false;
        for(CtBehavior b:scan.get(owner).getDeclaredBehaviors()){
            if(onlyMethod.length>0 && !b.getName().equals(onlyMethod[0]))continue;
            final List<String> calls=new ArrayList<>();b.instrument(new ExprEditor(){public void edit(MethodCall m){calls.add(m.getClassName()+"."+m.getMethodName());}});
            if(calls.contains(second)){expect(calls.indexOf(first)>=0 && calls.indexOf(first)<calls.indexOf(second),"acknowledgement precedes "+second);found=true;}
        }
        expect(found,"native dispatch acknowledgement path present");
    }
    void wiring(String owner,String expected)throws Exception {
        ClassPool scan=new ClassPool(true);scan.insertClassPath(compiled.toString());final List<String> calls=new ArrayList<>();for(CtBehavior b:scan.get(owner).getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall m){calls.add(m.getClassName()+"."+m.getMethodName());}});
        expect(calls.toString().contains(expected),"active provider calls "+expected);
    }
    CtClass make(String n,String parent,String... members)throws Exception {CtClass c=parent==null?p.makeClass(n):p.makeClass(n,p.get(parent));fixtures.add(c);for(String m:members)if(m.matches("(?s)^public (static )?[\\w.$]+ \\w+\\(.*"))c.addMethod(CtNewMethod.make(m,c));else c.addField(CtField.make(m,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));return c;}
    void start(String adapter)throws Exception {
        for(CtClass c:fixtures)bytes.put(c.getName(),c.toBytecode());
        loader=new ClassLoader(DownfallUiTest.class.getClassLoader()){
            protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(n)){
                    Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);
                    if(c==null&&b==null&&n.startsWith("communicationmod."))try{b=Files.readAllBytes(compiled.resolve(n.replace('.','/')+".class"));}catch(Exception e){throw new ClassNotFoundException(n,e);}
                    if(c==null&&b!=null)c=defineClass(n,b,0,b.length);
                    if(c==null){if(n.startsWith("com.megacrit.")||n.startsWith("downfall.")||n.startsWith("collector.")||n.startsWith("theHexaghost."))throw new ClassNotFoundException("Unfixtured game class: "+n);c=super.loadClass(n,resolve);}
                    if(resolve)resolveClass(c);return c;
                }
            }
        };
        System.setProperty("communicationmod.play_control","true");loader.loadClass(adapter);
    }
    void type(Object r,String name)throws Exception{set(get(r,"type"),"value",name);}
    Object newObject(String n)throws Exception{return loader.loadClass(n).newInstance();}
    Object call(String n,String method,Object... args)throws Exception{return invoke(loader.loadClass(n),method,args);}
    Object invoke(Object target,String method,Object... args)throws Exception {Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();for(Method m:c.getMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length){try{return m.invoke(target instanceof Class?null:target,args);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}}throw new NoSuchMethodException(method);}
    Field field(Class<?> c,String n)throws Exception{for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
    Object get(Object o,String n)throws Exception{Class<?> c=o instanceof String?loader.loadClass((String)o):o.getClass();return field(c,n).get(o instanceof String?null:o);}
    void set(Object o,String n,Object v)throws Exception{Class<?> c=o instanceof String?loader.loadClass((String)o):o.getClass();field(c,n).set(o instanceof String?null:o,v);}
    void execute(Object action)throws Exception{((Consumer<JsonObject>)field(action.getClass(),"execute").get(action)).accept(new JsonObject());}
    void reject(Object action,String why)throws Exception{try{execute(action);}catch(IllegalArgumentException|IllegalStateException e){assertions++;return;}throw new AssertionError(why);}
    void expect(boolean ok,String why){assertions++;check(ok,why);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
