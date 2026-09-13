import java.lang.reflect.*;
import java.util.*;
import javassist.*;

/** Executes the real input hook against isolated field-only UI fixtures, never a game. */
public final class MapInputTest {
    public static void main(String[] args) throws Exception {
        ClassPool pool=new ClassPool(true);
        pool.insertClassPath(args[0]);
        Map<String,byte[]> bytes=new HashMap<>();
        fixture(pool,bytes,"com.megacrit.cardcrawl.helpers.Hitbox",
            new String[]{"public boolean hovered;"},new String[0]);
        fixture(pool,bytes,"com.megacrit.cardcrawl.map.DungeonMap",
            new String[]{"public com.megacrit.cardcrawl.helpers.Hitbox bossHb=new com.megacrit.cardcrawl.helpers.Hitbox();"},new String[0]);
        fixture(pool,bytes,"com.megacrit.cardcrawl.helpers.input.InputHelper",
            new String[]{"public static boolean justClickedLeft;"},new String[0]);
        fixture(pool,bytes,"com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen",
            new String[]{"public static final com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen MAP=new com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen();"},new String[0]);
        fixture(pool,bytes,"com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase",
            new String[]{"public static final com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase COMPLETE=new com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase();"},new String[0]);
        fixture(pool,bytes,"com.megacrit.cardcrawl.rooms.AbstractRoom",
            new String[]{"public com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase phase=com.megacrit.cardcrawl.rooms.AbstractRoom$RoomPhase.COMPLETE;"},new String[0]);
        fixture(pool,bytes,"com.megacrit.cardcrawl.dungeons.AbstractDungeon",
            new String[]{"public static com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen screen;",
                "public static com.megacrit.cardcrawl.rooms.AbstractRoom room=new com.megacrit.cardcrawl.rooms.AbstractRoom();"},
            new String[]{"public static com.megacrit.cardcrawl.rooms.AbstractRoom getCurrRoom(){return room;}"});
        fixture(pool,bytes,"communicationmod.ChoiceScreenUtils",new String[]{"public static boolean boss;"},
            new String[]{"public static boolean bossNodeAvailable(){return boss;}"});
        bytes.put("communicationmod.patches.DungeonMapPatch",pool.get("communicationmod.patches.DungeonMapPatch").toBytecode());
        ClassLoader loader=new ClassLoader(null) {
            protected Class<?> findClass(String name)throws ClassNotFoundException {
                byte[] data=bytes.get(name);if(data==null)throw new ClassNotFoundException(name);
                return defineClass(name,data,0,data.length);
            }
        };
        Class<?> patch=loader.loadClass("communicationmod.patches.DungeonMapPatch");
        Class<?> dungeon=loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon");
        Class<?> screen=loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon$CurrentScreen");
        Class<?> choices=loader.loadClass("communicationmod.ChoiceScreenUtils");
        Class<?> input=loader.loadClass("com.megacrit.cardcrawl.helpers.input.InputHelper");
        Class<?> mapType=loader.loadClass("com.megacrit.cardcrawl.map.DungeonMap");
        Object map=mapType.newInstance(),hb=mapType.getField("bossHb").get(map);
        Field pending=patch.getField("doBossHover"),click=input.getField("justClickedLeft"),hover=hb.getClass().getField("hovered");
        Method apply=patch.getMethod("Insert",mapType);
        choices.getField("boss").setBoolean(null,true);
        pending.setBoolean(null,true);
        apply.invoke(null,map);
        check(!pending.getBoolean(null)&&!click.getBoolean(null)&&!hover.getBoolean(hb),"stale boss input must be discarded outside MAP");
        dungeon.getField("screen").set(null,screen.getField("MAP").get(null));
        choices.getField("boss").setBoolean(null,false);
        pending.setBoolean(null,true);apply.invoke(null,map);
        check(!pending.getBoolean(null)&&!click.getBoolean(null),"unavailable boss must not click");
        choices.getField("boss").setBoolean(null,true);
        Object room=dungeon.getField("room").get(null);
        Field phase=room.getClass().getField("phase");Object complete=phase.get(room);
        phase.set(room,null);pending.setBoolean(null,true);apply.invoke(null,map);
        check(!pending.getBoolean(null)&&!click.getBoolean(null),"incomplete room must not click");
        phase.set(room,complete);pending.setBoolean(null,true);apply.invoke(null,map);
        check(!pending.getBoolean(null)&&click.getBoolean(null)&&hover.getBoolean(hb),"eligible boss injects native click once");
        click.setBoolean(null,false);hover.setBoolean(hb,false);apply.invoke(null,map);
        check(!click.getBoolean(null)&&!hover.getBoolean(hb),"consumed input must not replay");
        click.setBoolean(null,true);apply.invoke(null,map);
        check(click.getBoolean(null),"idle hook must preserve human input");
        System.out.println("PASS: real boss hook, stale screen/eligibility/room guards, one-shot click, human input preserved (UI fixtures only)");
    }
    private static void fixture(ClassPool pool,Map<String,byte[]> bytes,String name,String[] fields,String[] methods)throws Exception {
        CtClass type=pool.makeClass(name);
        type.addConstructor(CtNewConstructor.defaultConstructor(type));
        for(String field:fields)type.addField(CtField.make(field,type));
        for(String method:methods)type.addMethod(CtNewMethod.make(method,type));
        bytes.put(name,type.toBytecode());
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
