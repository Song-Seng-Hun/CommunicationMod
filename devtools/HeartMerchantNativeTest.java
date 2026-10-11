import javassist.*;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Missing Heart entry/native binding regression: existing UI groups never exercise HeartShopRoom. */
public final class HeartMerchantNativeTest {
    static final String O="communicationmod.observation.", D="com.megacrit.cardcrawl.dungeons.AbstractDungeon",
        H="com.megacrit.cardcrawl.helpers.Hitbox", I="com.megacrit.cardcrawl.helpers.input.InputHelper",
        ROOM="downfall.rooms.HeartShopRoom", HEART="downfall.util.HeartMerchant";
    final ClassPool p=new ClassPool(true), nativePool=new ClassPool(true);
    final Map<String,byte[]> bytes=new HashMap<>();
    final List<CtClass> fixtures=new ArrayList<>();
    ClassLoader loader;
    int assertions;
    public static void main(String[] args)throws Exception {
        HeartMerchantNativeTest t=new HeartMerchantNativeTest();t.run(args);
        System.out.println("PASS: Heart merchant production/native entry ("+t.assertions+" assertions); no game initialization");
    }
    void run(String[] args)throws Exception {
        p.insertClassPath(args[0]);nativePool.insertClassPath(args[1]);nativePool.insertClassPath(args[2]);
        make("com.megacrit.cardcrawl.core.Settings",null,"public static boolean isTouchScreen;","public static boolean isControllerMode;","public static float scale=1f;");
        make(I,null,"public static boolean justClickedLeft;","public static boolean justClickedRight;","public static boolean justReleasedClickLeft;");
        // Real hitbox refresh can erase synthetic hover; the merchant body must reapply the scoped target.
        make(H,null,"public boolean hovered;","public boolean clicked;","public boolean clickStarted;","public boolean justHovered;","public float cX;","public float cY;","public void update(){hovered=false;}");
        make("com.badlogic.gdx.graphics.Color",null,"public float a;","public static com.badlogic.gdx.graphics.Color WHITE=new com.badlogic.gdx.graphics.Color();","public static com.badlogic.gdx.graphics.Color VIOLET=new com.badlogic.gdx.graphics.Color();");
        CtClass graphics=p.makeInterface("com.badlogic.gdx.Graphics");graphics.addMethod(CtNewMethod.abstractMethod(CtClass.floatType,"getDeltaTime",new CtClass[0],new CtClass[0],graphics));fixtures.add(graphics);
        make("fixture.Graphics",null,"public float getDeltaTime(){return 0f;}");p.get("fixture.Graphics").addInterface(graphics);
        make("com.badlogic.gdx.Gdx",null,"public static com.badlogic.gdx.Graphics graphics=new fixture.Graphics();");
        make("com.megacrit.cardcrawl.helpers.controller.CInputAction",null,"public boolean isJustPressed(){return false;}");
        make("com.megacrit.cardcrawl.helpers.controller.CInputActionSet",null,"public static com.megacrit.cardcrawl.helpers.controller.CInputAction select=new com.megacrit.cardcrawl.helpers.controller.CInputAction();");
        make("downfall.vfx.CustomAnimatedNPC",null,"public boolean highlighted;","public void update(){}","public void changeBorderColor(com.badlogic.gdx.graphics.Color c){}");
        make("com.megacrit.cardcrawl.core.CardCrawlGame",null,"public static boolean isPopupOpen;");
        make("com.megacrit.cardcrawl.characters.AbstractPlayer",null,"public boolean isDead;","public boolean isDraggingCard;","public boolean viewingRelics;");
        make("com.megacrit.cardcrawl.ui.buttons.ProceedButton",null,"public String label=\"Next\";","public "+H+" hb=new "+H+"();","public void setLabel(String s){label=s;}","public void show(){}");
        make("com.megacrit.cardcrawl.core.OverlayMenu",null,"public com.megacrit.cardcrawl.ui.buttons.ProceedButton proceedButton=new com.megacrit.cardcrawl.ui.buttons.ProceedButton();");
        make("com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase",null,"public static com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase COMBAT=new com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase();","public static com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase COMPLETE=new com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase();");
        make("com.megacrit.cardcrawl.rooms.AbstractRoom",null,"public com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase phase=com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase.COMPLETE;","public java.util.ArrayList rewards=new java.util.ArrayList();","public boolean rewardAllowed=true;");
        make("com.megacrit.cardcrawl.shop.Merchant",null,"public static String[] NAMES=new String[]{\"Shop\"};","public "+H+" hb=new "+H+"();");
        make("com.megacrit.cardcrawl.rooms.ShopRoom","com.megacrit.cardcrawl.rooms.AbstractRoom","public com.megacrit.cardcrawl.shop.Merchant merchant;");
        make(D+"$CurrentScreen",null,"public static "+D+"$CurrentScreen NONE=new "+D+"$CurrentScreen();","public static "+D+"$CurrentScreen SHOP=new "+D+"$CurrentScreen();");
        make("com.megacrit.cardcrawl.shop.ShopScreen",null,"public int opens;","public void open(){opens++;"+D+".isScreenUp=true;"+D+".screen="+D+"$CurrentScreen.SHOP;}");
        make(D,null,"public static "+D+"$CurrentScreen screen="+D+"$CurrentScreen.NONE;","public static boolean isScreenUp;","public static boolean isFadingIn;","public static boolean isFadingOut;","public static com.badlogic.gdx.graphics.Color fadeColor=new com.badlogic.gdx.graphics.Color();","public static com.megacrit.cardcrawl.characters.AbstractPlayer player=new com.megacrit.cardcrawl.characters.AbstractPlayer();","public static com.megacrit.cardcrawl.core.OverlayMenu overlayMenu=new com.megacrit.cardcrawl.core.OverlayMenu();","public static com.megacrit.cardcrawl.shop.ShopScreen shopScreen=new com.megacrit.cardcrawl.shop.ShopScreen();","public static com.megacrit.cardcrawl.rooms.AbstractRoom room;","public static com.megacrit.cardcrawl.rooms.AbstractRoom getCurrRoom(){return room;}");
        make(HEART,null,"public static String[] NAMES=new String[]{\"Heart Shop\",\"Welcome\"};","public "+H+" hb=new "+H+"();","public downfall.vfx.CustomAnimatedNPC anim=new downfall.vfx.CustomAnimatedNPC();","private float speechTimer=100f;","private boolean saidWelcome;","private int shopScreen=1;","private java.util.ArrayList idleMessages=new java.util.ArrayList();","private void welcomeSfx(){}","private void playMiscSfx(){}");
        CtClass heart=p.get(HEART);heart.addMethod(CtNewMethod.copy(nativePool.get(HEART).getDeclaredMethod("update"),heart,null));
        // Use the same production raw hook that the copied-runtime builder installs.
        try{Class.forName("communicationmod.patches.MerchantPatch").getMethod("Raw",CtBehavior.class,boolean.class).invoke(null,heart.getDeclaredMethod("update"),false);}
        catch(NoSuchMethodException baseline){ /* Baseline lacks this binding; capture must still fail first. */ }
        make(ROOM,"com.megacrit.cardcrawl.rooms.ShopRoom","public "+HEART+" heartMerchant=new "+HEART+"();","public boolean heartMerchantShown=true;");
        make("fixture.UnknownHeart",HEART);make("fixture.UnknownRoom",ROOM);
        make("communicationmod.GameStateConverter",null,"public static String removeTextFormatting(String s){return s;}");
        make(O+"CombatObservation",null,"public static boolean inCombat(){return false;}","public static void claim(String a,String b){}");
        make(O+"PotionUi",null,"public static boolean idle(){return true;}");
        for(String n:new String[]{O+"RoomUi",O+"NativeUiInput","communicationmod.patches.MerchantPatch"})bytes.put(n,Files.readAllBytes(Paths.get(args[0],n.replace('.','/')+".class")));
        Path protocols=Paths.get(args[0],"communicationmod/protocol");try(java.util.stream.Stream<Path> files=Files.list(protocols)){files.filter(f->f.getFileName().toString().startsWith("ProtocolSession")).forEach(f->{try{bytes.put("communicationmod.protocol."+f.getFileName().toString().replace(".class",""),Files.readAllBytes(f));}catch(Exception e){throw new RuntimeException(e);}});}
        for(CtClass c:fixtures)bytes.put(c.getName(),c.toBytecode());
        loader=new ClassLoader(HeartMerchantNativeTest.class.getClassLoader()) {protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);if(c==null&&b!=null)c=defineClass(n,b,0,b.length);if(c==null)c=super.loadClass(n,resolve);if(resolve)resolveClass(c);return c;}}};
        System.setProperty("communicationmod.play_control","true");
        Object room=obj(ROOM);set(D,"room",room);Object merchant=get(room,"heartMerchant"),box=get(merchant,"hb");
        Object entry=entry();check(entry!=null,"HeartShopRoom with null base merchant offers native entry");
        set(room,"heartMerchant",obj(HEART));reject(entry,"replaced heart identity rejected");set(room,"heartMerchant",merchant);
        set(merchant,"hb",obj(H));reject(entry,"replaced native hitbox rejected");set(merchant,"hb",box);
        Object shop=get(D,"shopScreen");set(D,"shopScreen",obj("com.megacrit.cardcrawl.shop.ShopScreen"));reject(entry,"replaced native shop screen rejected");set(D,"shopScreen",shop);
        set(room,"heartMerchantShown",false);check(entry()==null,"hidden heart withheld");reject(entry,"hidden stale heart rejected");set(room,"heartMerchantShown",true);
        set(room,"phase",get("com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase","COMBAT"));check(entry()==null,"combat heart withheld");set(room,"phase",get("com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase","COMPLETE"));
        set(box,"clickStarted",true);check(entry()==null,"human hitbox pending withheld");reject(entry,"pending stale hitbox rejected");set(box,"clickStarted",false);
        set(room,"heartMerchant",obj("fixture.UnknownHeart"));check(entry()==null,"unknown HeartMerchant subclass withheld");set(room,"heartMerchant",merchant);
        set(D,"room",obj("fixture.UnknownRoom"));check(entry()==null,"unknown HeartShopRoom subclass withheld");set(D,"room",room);
        set(merchant,"shopScreen",2);check(entry()==null,"nonstandard heart shop withheld");set(merchant,"shopScreen",1);
        set(I,"justClickedLeft",true);check(entry()==null,"human mouse input withheld");set(I,"justClickedLeft",false);
        ((List)get(room,"rewards")).add("native reward");execute(entry);
        check(((Integer)get(get(D,"shopScreen"),"opens"))==1,"installed native body opens shop exactly once");check(((List)get(room,"rewards")).isEmpty()&&!((Boolean)get(room,"rewardAllowed")),"native room side effects preserved");
        check(!((Boolean)get(I,"justClickedLeft"))&&!((Boolean)get(box,"clicked"))&&!((Boolean)get(box,"hovered")),"scoped input restored");reject(entry,"opened screen makes entry stale");
        set(D,"isScreenUp",false);set(D,"screen",get(D+"$CurrentScreen","NONE"));set(D,"room",obj("com.megacrit.cardcrawl.rooms.ShopRoom"));set(get(D,"room"),"merchant",obj("com.megacrit.cardcrawl.shop.Merchant"));
        Object base=entry();check(base!=null,"base merchant entry preserved");execute(base);check((Boolean)get("communicationmod.patches.MerchantPatch","visitMerchant"),"base merchant queues its established native hook");
        System.clearProperty("communicationmod.play_control");
    }
    void make(String name,String parent,String... members)throws Exception {CtClass c=parent==null?p.makeClass(name):p.makeClass(name,p.get(parent));fixtures.add(c);for(String s:members){if(s.matches("(?s)^(public|private) (static )?[\\w.$]+ \\w+\\(.*"))c.addMethod(CtNewMethod.make(s,c));else c.addField(CtField.make(s,c));}c.addConstructor(CtNewConstructor.defaultConstructor(c));}
    Object obj(String n)throws Exception{return loader.loadClass(n).newInstance();}
    Field field(Object o,String n)throws Exception{Class<?> c=o instanceof String?loader.loadClass((String)o):o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
    Object get(Object o,String n)throws Exception{return field(o,n).get(o instanceof String?null:o);}
    void set(Object o,String n,Object v)throws Exception{field(o,n).set(o instanceof String?null:o,v);}
    Object entry()throws Exception{List<?> actions=(List<?>)loader.loadClass(O+"RoomUi").getMethod("capture",JsonObject.class,JsonObject.class,String.class).invoke(obj(O+"RoomUi"),new JsonObject(),new JsonObject(),"SHOP_ROOM");for(Object a:actions)if(get(a,"id").equals("run.shop.enter"))return a;return null;}
    void execute(Object a)throws Exception{((java.util.function.Consumer<JsonObject>)get(a,"execute")).accept(new JsonObject());}
    void reject(Object a,String label)throws Exception{try{execute(a);throw new AssertionError(label);}catch(IllegalArgumentException expected){assertions++;}}
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);assertions++;}
}
