import java.lang.reflect.*;
import java.util.*;
import javassist.*;

/** Real adapter and installed helper bodies; field-only dependencies in a parentless loader. */
public final class MapCoordinatesTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        ClassPool source=new ClassPool(true);for(String arg:args)source.insertClassPath(arg);
        ClassPool fixtures=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        add(fixtures,bytes,"com.megacrit.cardcrawl.map.MapRoomNode",new String[]{"public int y;"},new String[0]);
        add(fixtures,bytes,"com.megacrit.cardcrawl.dungeons.AbstractDungeon",new String[]{"public static String id=\"Exordium\";"},new String[0]);
        add(fixtures,bytes,"com.evacipated.cardcrawl.modthespire.Loader",new String[]{"public static boolean enabled=true;"},
            new String[]{"public static boolean isModLoaded(String id){return enabled && id.equals(\"EvilWithin\");}"});
        add(fixtures,bytes,"downfall.patches.EvilModeCharacterSelect",new String[]{"public static boolean evilMode;"},new String[0]);
        add(fixtures,bytes,"downfall.patches.ui.map.FlipMap$MapFlipper",new String[]{"public static int startY=12;"},new String[0]);
        add(fixtures,bytes,"downfall.patches.ui.map.FlipMap",new String[]{"public static java.util.HashSet excluded=new java.util.HashSet();"},
            new String[]{"public static java.util.HashSet access$000(){return excluded;}"});
        CtClass logger=fixtures.makeInterface("org.apache.logging.log4j.Logger");
        logger.addMethod(CtNewMethod.abstractMethod(CtClass.voidType,"warn",new CtClass[]{fixtures.get("java.lang.String"),fixtures.get("java.lang.Throwable")},new CtClass[0],logger));
        bytes.put(logger.getName(),logger.toBytecode());
        CtClass quiet=fixtures.makeClass("QuietMapLogger");quiet.addInterface(logger);
        quiet.addConstructor(CtNewConstructor.defaultConstructor(quiet));quiet.addMethod(CtNewMethod.make("public void warn(String message,Throwable cause){}",quiet));
        bytes.put(quiet.getName(),quiet.toBytecode());
        add(fixtures,bytes,"org.apache.logging.log4j.LogManager",new String[0],
            new String[]{"public static org.apache.logging.log4j.Logger getLogger(Class type){return new QuietMapLogger();}"});
        for(String name:new String[]{"FirstRoom","BossStuff"}) {
            String type="downfall.patches.ui.map.FlipMap$"+name;
            CtClass copy=fixtures.makeClass(type),original=source.get(type);
            String method=name.equals("FirstRoom")?"isValidFirstNode":"compatibleGetARealY";
            copy.addMethod(CtNewMethod.copy(original.getDeclaredMethod(method),copy,null));
            bytes.put(type,copy.toBytecode());
        }
        bytes.put("communicationmod.compat.DownfallMapCoordinates",source.get("communicationmod.compat.DownfallMapCoordinates").toBytecode());
        ClassLoader loader=loader(bytes);Class<?> adapter=loader.loadClass("communicationmod.compat.DownfallMapCoordinates");
        Class<?> nodeType=loader.loadClass("com.megacrit.cardcrawl.map.MapRoomNode");Object node=nodeType.newInstance();
        Field y=nodeType.getField("y");Method first=adapter.getMethod("firstUiRow",nodeType),boss=adapter.getMethod("bossUiRow",nodeType);
        check(adapter.getMethod("isSupported").invoke(null).equals(true),"installed signatures resolve");
        y.setInt(node,0);equal(first.invoke(null,node),0);y.setInt(node,14);equal(boss.invoke(null,node),14);
        loader.loadClass("downfall.patches.EvilModeCharacterSelect").getField("evilMode").setBoolean(null,true);
        y.setInt(node,12);equal(first.invoke(null,node),0);equal(boss.invoke(null,node),0);
        y.setInt(node,0);equal(first.invoke(null,node),1);equal(boss.invoke(null,node),14);
        Field act=loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon").getField("id");
        act.set(null,"TheEnding");equal(boss.invoke(null,node),2);
        act.set(null,"fixture:excluded");
        ((Set)loader.loadClass("downfall.patches.ui.map.FlipMap").getField("excluded").get(null)).add("fixture:excluded");
        equal(first.invoke(null,node),0);equal(boss.invoke(null,node),0);
        y.setInt(node,14);equal(boss.invoke(null,node),14);
        equal(first.invoke(null,new Object[]{null}),Integer.MIN_VALUE);
        // A fresh loader represents base-only startup, not a hot-unloaded mod.
        ClassLoader base=loader(bytes);base.loadClass("com.evacipated.cardcrawl.modthespire.Loader").getField("enabled").setBoolean(null,false);
        Class<?> baseNode=base.loadClass("com.megacrit.cardcrawl.map.MapRoomNode");Object plain=baseNode.newInstance();baseNode.getField("y").setInt(plain,7);
        Class<?> baseAdapter=base.loadClass("communicationmod.compat.DownfallMapCoordinates");
        equal(baseAdapter.getMethod("firstUiRow",baseNode).invoke(null,plain),7);
        equal(baseAdapter.getMethod("bossUiRow",baseNode).invoke(null,plain),7);
        // Loaded Downfall with a missing helper must fail closed, not use vanilla Y.
        bytes.remove("downfall.patches.ui.map.FlipMap$BossStuff");
        ClassLoader missing=loader(bytes);Class<?> broken=missing.loadClass("communicationmod.compat.DownfallMapCoordinates");
        equal(broken.getMethod("isSupported").invoke(null),false);
        equal(broken.getMethod("getUnsupportedReason").invoke(null),"DOWNFALL_MAP_ADAPTER_UNAVAILABLE");
        Class<?> brokenNode=missing.loadClass("com.megacrit.cardcrawl.map.MapRoomNode");Object last=brokenNode.newInstance();brokenNode.getField("y").setInt(last,14);
        equal(broken.getMethod("bossUiRow",brokenNode).invoke(null,last),Integer.MIN_VALUE);
        equal(broken.getMethod("firstUiRow",brokenNode).invoke(null,last),Integer.MIN_VALUE);
        System.out.println("PASS: actual installed helper bodies via optional adapter; normal/reverse/startY/ending/excluded/base-only/missing-helper fixtures (not gameplay)");
    }
    private static ClassLoader loader(Map<String,byte[]> input) {
        Map<String,byte[]> bytes=new HashMap<>(input);
        return new ClassLoader(null){protected Class<?> findClass(String name)throws ClassNotFoundException {
            byte[] data=bytes.get(name);if(data==null)throw new ClassNotFoundException(name);return defineClass(name,data,0,data.length);
        }};
    }
    private static void add(ClassPool pool,Map<String,byte[]> bytes,String name,String[] fields,String[] methods)throws Exception {
        CtClass type=pool.makeClass(name);type.addConstructor(CtNewConstructor.defaultConstructor(type));
        for(String field:fields)type.addField(CtField.make(field,type));
        for(String method:methods)type.addMethod(CtNewMethod.make(method,type));bytes.put(name,type.toBytecode());
    }
    private static void equal(Object actual,Object expected){check(Objects.equals(actual,expected),"expected "+expected+", got "+actual);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
