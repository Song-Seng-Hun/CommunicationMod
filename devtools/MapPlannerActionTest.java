import com.google.gson.*;
import communicationmod.protocol.ProtocolSession;
import java.lang.reflect.*;
import java.util.*;
import javassist.*;

/** Real planner + v2 session, isolated public-graph fixtures. No game/native code. */
public final class MapPlannerActionTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        fixture(p,bytes,"com.megacrit.cardcrawl.helpers.Hitbox",new String[]{"public float cX;","public float cY;"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.rooms.AbstractRoom",new String[0],new String[0]);
        CtClass boss=p.makeClass("com.megacrit.cardcrawl.rooms.MonsterRoomBoss",p.get("com.megacrit.cardcrawl.rooms.AbstractRoom"));
        boss.addConstructor(CtNewConstructor.defaultConstructor(boss));bytes.put(boss.getName(),boss.toBytecode());
        fixture(p,bytes,"com.megacrit.cardcrawl.map.MapEdge",new String[]{"public int dstX;","public int dstY;"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.map.MapRoomNode",new String[]{"public int x;","public int y;","public com.megacrit.cardcrawl.helpers.Hitbox hb=new com.megacrit.cardcrawl.helpers.Hitbox();","public java.util.ArrayList edges=new java.util.ArrayList();","public com.megacrit.cardcrawl.rooms.AbstractRoom room=new com.megacrit.cardcrawl.rooms.AbstractRoom();"},
            new String[]{"public boolean hasEdges(){return true;}","public java.util.ArrayList getEdges(){return edges;}","public com.megacrit.cardcrawl.rooms.AbstractRoom getRoom(){return room;}"});
        fixture(p,bytes,"com.megacrit.cardcrawl.map.DungeonMap",new String[]{"public com.megacrit.cardcrawl.helpers.Hitbox bossHb=new com.megacrit.cardcrawl.helpers.Hitbox();"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.screens.DungeonMapScreen",new String[]{"public boolean clicked;","public static float offsetY;"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.dungeons.AbstractDungeon",new String[]{"public static java.util.ArrayList map;","public static int actNum=1;","public static String id=\"Exordium\";","public static boolean firstRoomChosen=true;","public static com.megacrit.cardcrawl.map.MapRoomNode current;","public static com.megacrit.cardcrawl.screens.DungeonMapScreen dungeonMapScreen=new com.megacrit.cardcrawl.screens.DungeonMapScreen();"},
            new String[]{"public static com.megacrit.cardcrawl.map.MapRoomNode getCurrMapNode(){return current;}"});
        fixture(p,bytes,"com.megacrit.cardcrawl.core.Settings",new String[]{"public static int WIDTH=1920;","public static float scale=1;"},new String[0]);
        fixture(p,bytes,"communicationmod.map.MapDrawing",new String[]{"public static boolean blocked;"},new String[]{"public static boolean visible(){return true;}","public static boolean blocksNavigation(){return blocked;}","public static void close(){blocked=false;}"});
        fixture(p,bytes,"communicationmod.patches.MapRoomNodeHoverPatch",new String[]{"public static boolean doHover;"},new String[0]);
        fixture(p,bytes,"communicationmod.patches.DungeonMapPatch",new String[]{"public static boolean doBossHover;"},new String[0]);
        fixture(p,bytes,"communicationmod.compat.DownfallMapCoordinates",new String[0],new String[]{"public static boolean isSupported(){return true;}","public static int bossUiRow(com.megacrit.cardcrawl.map.MapRoomNode node){return node.y;}"});
        ClassPool actual=new ClassPool(true);actual.insertClassPath(args[0]);
        bytes.put("communicationmod.map.MapPlanner",actual.get("communicationmod.map.MapPlanner").toBytecode());
        ClassLoader loader=new ClassLoader(MapPlannerActionTest.class.getClassLoader()) {
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    Class<?> found=findLoadedClass(name);byte[] b=bytes.get(name);
                    if(found==null&&b!=null)found=defineClass(name,b,0,b.length);
                    if(found==null){if(name.startsWith("com.megacrit."))throw new ClassNotFoundException("Unstubbed game type: "+name);return super.loadClass(name,resolve);}
                    if(resolve)resolveClass(found);return found;
                }
            }
        };
        Class<?> node=loader.loadClass("com.megacrit.cardcrawl.map.MapRoomNode"),edge=loader.loadClass("com.megacrit.cardcrawl.map.MapEdge");
        Object a=node.newInstance(),b=node.newInstance(),e=edge.newInstance();
        node.getField("x").setInt(a,1);node.getField("y").setInt(a,14);node.getField("x").setInt(b,2);node.getField("y").setInt(b,13);
        edge.getField("dstX").setInt(e,2);edge.getField("dstY").setInt(e,13);((List<Object>)node.getField("edges").get(a)).add(e);
        Class<?> dungeon=loader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon");
        Object graph=new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(a,b))));dungeon.getField("map").set(null,graph);dungeon.getField("current").set(null,a);
        Class<?> planner=loader.loadClass("communicationmod.map.MapPlanner");Method observe=planner.getMethod("observation"),actions=planner.getMethod("actions");
        String prior=System.getProperty("communicationmod.play_control");System.setProperty("communicationmod.play_control","true");
        try {
            Map<String,Object> initial=(Map<String,Object>)observe.invoke(null);
            ProtocolSession session=new ProtocolSession(failure->{throw new AssertionError(failure);});
            JsonObject hello=parse(session.receive("{\"type\":\"hello\",\"protocol_version\":2}"));
            JsonObject published=parse(session.publish(new JsonObject(),new JsonObject(),(List<ProtocolSession.Action>)actions.invoke(null),true,"supported"));
            JsonObject request=new JsonObject();request.addProperty("type","act");request.addProperty("session_id",hello.get("session_id").getAsString());request.add("state_id",published.get("state_id"));request.addProperty("request_id","plan-1");request.addProperty("action_id","run.map.plan");
            JsonObject arguments=new JsonObject();arguments.addProperty("map_id",(String)initial.get("map_id"));arguments.addProperty("revision",(Number)initial.get("revision"));
            JsonArray nodes=new JsonArray();nodes.add("1,14");nodes.add("2,13");arguments.add("nodes",nodes);request.add("arguments",arguments);
            JsonObject result=parse(session.receive(request.toString()));check(result.get("status").getAsString().equals("applied"),"actual v2 plan action applies");
            Map<String,Object> planned=(Map<String,Object>)observe.invoke(null);check(planned.get("route").equals(Arrays.asList("1,14","2,13")),"semantic route set");
            check(planned.get("current_node").equals("1,14")&&planned.get("next_planned_node").equals("2,13"),"cheap current/next readout");
            check(dungeon.getField("current").get(null)==a&&dungeon.getField("map").get(null)==graph,"planning did not move player or replace map");
            check(!loader.loadClass("communicationmod.patches.MapRoomNodeHoverPatch").getField("doHover").getBoolean(null),"planning did not queue travel");
            // Fresh protocol state, but stale annotation revision: must reject without mutation.
            published=parse(session.publish(new JsonObject(),new JsonObject(),(List<ProtocolSession.Action>)actions.invoke(null),true,"supported"));request.add("state_id",published.get("state_id"));request.addProperty("request_id","stale-plan");
            result=parse(session.receive(request.toString()));check(result.toString().contains("INVALID_ARGUMENTS"),"stale plan revision rejected");
            Class<?> ui=loader.loadClass("communicationmod.map.MapDrawing");ui.getField("blocked").setBoolean(null,true);
            check(((List<?>)actions.invoke(null)).isEmpty(),"no MCP overwrite while human edits");
            ui.getField("blocked").setBoolean(null,false);
            dungeon.getField("current").set(null,b);Map<String,Object> moved=(Map<String,Object>)observe.invoke(null);
            check(moved.get("status").equals("route_complete")&&moved.get("next_planned_node")==null,"actual movement updates plan progress separately");
            check(moved.get("revision").equals(planned.get("revision")),"movement does not rewrite annotation revision");
        } finally {if(prior==null)System.clearProperty("communicationmod.play_control");else System.setProperty("communicationmod.play_control",prior);}
        System.out.println("PASS: real v2 route action, no movement, stale revision and human ownership guards, actual-position readout (fixtures only)");
    }
    private static JsonObject parse(String text){return new JsonParser().parse(text).getAsJsonObject();}
    private static void fixture(ClassPool p,Map<String,byte[]> bytes,String name,String[] fields,String[] methods)throws Exception {
        CtClass type=p.makeClass(name);type.addConstructor(CtNewConstructor.defaultConstructor(type));for(String field:fields)type.addField(CtField.make(field,type));for(String method:methods)type.addMethod(CtNewMethod.make(method,type));bytes.put(name,type.toBytecode());
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
