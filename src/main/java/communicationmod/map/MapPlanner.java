package communicationmod.map;

import com.google.gson.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.*;
import com.megacrit.cardcrawl.map.*;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import communicationmod.compat.*;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Public map graph and annotation protocol. Planning never invokes room selection. */
public final class MapPlanner {
    static final MapAnnotations INK=new MapAnnotations();
    private static Object graph;
    private static int serial;
    private static final Map<String,MapRoomNode> nodes=new LinkedHashMap<>();
    private static final Map<String,float[]> positions=new HashMap<>();
    private MapPlanner(){}

    public static void reset() {graph=null;nodes.clear();positions.clear();INK.reset("closed-"+(++serial));MapDrawing.close();}
    public static void sync() {
        if(AbstractDungeon.map==null || graph==AbstractDungeon.map)return;
        graph=AbstractDungeon.map;nodes.clear();positions.clear();
        INK.reset("map-"+(++serial)+"-act-"+AbstractDungeon.actNum);MapDrawing.close();
        for(List<MapRoomNode> row:AbstractDungeon.map)for(MapRoomNode node:row)
            if(node!=null&&node.hasEdges()&&node.getRoom()!=null)nodes.put(id(node),node);
    }
    static String id(MapRoomNode node){return node.x+","+node.y;}
    static boolean known(String id){return "boss".equals(id)||nodes.containsKey(id);}
    static boolean connected(String from,String to) {
        MapRoomNode a=nodes.get(from);if(a==null)return false;
        if("boss".equals(to))return DownfallMapCoordinates.isSupported() && MapChoicePolicy.bossAvailable(
            DownfallMapCoordinates.bossUiRow(a),TheEnding.ID.equals(AbstractDungeon.id));
        MapRoomNode b=nodes.get(to);if(b==null)return false;
        // Public drawn edges only. Do not speculate about future Flight/boot/teleport charges.
        for(MapEdge edge:a.getEdges())if(edge!=null&&edge.dstX==b.x&&edge.dstY==b.y)return true;
        return false;
    }
    public static void noteNode(MapRoomNode node) {
        sync();if(nodes.get(id(node))==node)remember(id(node),node.hb.cX,node.hb.cY);
    }
    public static void noteBoss(DungeonMap map) {sync();remember("boss",map.bossHb.cX,map.bossHb.cY);}
    private static void remember(String id,float x,float y) {positions.put(id,MapAnnotations.toMap(x,y,Settings.WIDTH,Settings.scale,DungeonMapScreen.offsetY));}
    static float[] position(String id) {
        float[] p=positions.get(id);return p==null?null:MapAnnotations.toScreen(p[0],p[1],Settings.WIDTH,Settings.scale,DungeonMapScreen.offsetY);
    }
    static String nearest(float x,float y) {
        String best=null;float distance=38*Settings.scale;distance*=distance;
        for(String id:positions.keySet()) {
            float[] p=position(id);float dx=x-p[0],dy=y-p[1],d=dx*dx+dy*dy;
            if(d<distance){best=id;distance=d;}
        }
        return best;
    }
    static void humanNode(String node) {
        if(node==null)return;
        List<String> route=new ArrayList<>(INK.route());int existing=route.indexOf(node);
        if(existing>=0)route=new ArrayList<>(route.subList(0,existing+1));else route.add(node);
        INK.setRoute(route,"human",INK.revision(),MapPlanner::known,MapPlanner::connected);
    }
    public static Map<String,Object> observation() {
        sync();Map<String,Object> out=new LinkedHashMap<>();
        if(graph==null){out.put("status","unavailable");return out;}
        String current=null;MapRoomNode node=AbstractDungeon.getCurrMapNode();
        if(node!=null)current=nodes.containsKey(id(node))?id(node):node.getRoom() instanceof MonsterRoomBoss?"boss":null;
        List<String> route=INK.route();String next=INK.next(current),status=route.isEmpty()?"unplanned":"off_route";
        if(route.contains(current))status=next==null?"route_complete":"on_route";
        else if(!route.isEmpty()&&current!=null&&connected(current,route.get(0))){next=route.get(0);status="approaching_route";}
        else if(!route.isEmpty()&&!AbstractDungeon.firstRoomChosen)status="before_start";
        out.put("map_id",INK.mapId());out.put("revision",INK.revision());out.put("route_author",INK.routeAuthor());
        out.put("route",route);out.put("route_count",route.size());out.put("planned_start",route.isEmpty()?null:route.get(0));
        out.put("current_node",current);out.put("next_planned_node",next);out.put("status",status);
        out.put("stroke_count",INK.strokeCount());out.put("editing",MapDrawing.blocksNavigation());
        out.put("scope","annotation_only_session");out.put("edge_policy","drawn_edges_only");
        return out;
    }
    public static List<ProtocolSession.Action> actions() {
        sync();if(!editable())return Collections.emptyList();
        final String map=INK.mapId();final long revision=INK.revision();
        JsonObject schema=new JsonObject();schema.addProperty("type","object");schema.addProperty("additionalProperties",false);
        JsonObject properties=new JsonObject(),mapField=new JsonObject(),revField=new JsonObject(),routeField=new JsonObject(),item=new JsonObject();
        mapField.addProperty("type","string");mapField.addProperty("const",map);
        revField.addProperty("type","integer");revField.addProperty("const",revision);
        item.addProperty("type","string");item.addProperty("description","Map node_id, e.g. 2,13; boss is the boss icon");
        routeField.addProperty("type","array");routeField.addProperty("maxItems",MapAnnotations.MAX_ROUTE);routeField.add("items",item);
        properties.add("map_id",mapField);properties.add("revision",revField);properties.add("nodes",routeField);schema.add("properties",properties);
        JsonArray required=new JsonArray();for(String key:new String[]{"map_id","revision","nodes"})required.add(key);schema.add("required",required);
        return Collections.singletonList(new ProtocolSession.Action("run.map.plan","Plan route only / 경로 표시 (이동하지 않음)",schema,
            args->routeArguments(args,map,revision),args->INK.setRoute(routeArguments(args,map,revision),"mcp",revision,MapPlanner::known,MapPlanner::connected)));
    }
    private static boolean editable() {
        return Boolean.getBoolean("communicationmod.play_control")&&MapDrawing.visible()&&!MapDrawing.blocksNavigation()
            &&!AbstractDungeon.dungeonMapScreen.clicked&&!communicationmod.patches.MapRoomNodeHoverPatch.doHover
            &&!communicationmod.patches.DungeonMapPatch.doBossHover;
    }
    private static List<String> routeArguments(JsonObject args,String map,long revision) {
        sync();if(!editable()||!map.equals(INK.mapId())||revision!=INK.revision())throw new IllegalArgumentException("Map/editor changed");
        if(args.entrySet().size()!=3 || !args.has("map_id")||!args.has("revision")||!args.has("nodes")
            || !args.get("map_id").isJsonPrimitive()||!args.get("map_id").getAsJsonPrimitive().isString()
            || !map.equals(args.get("map_id").getAsString())||!args.get("revision").isJsonPrimitive()
            || !args.get("revision").getAsJsonPrimitive().isNumber()||!Long.toString(revision).equals(args.get("revision").getAsString())
            ||!args.get("nodes").isJsonArray())throw new IllegalArgumentException("Expected current map_id, revision and nodes");
        JsonArray values=args.getAsJsonArray("nodes");if(values.size()>MapAnnotations.MAX_ROUTE)throw new IllegalArgumentException("Route too long");
        List<String> route=new ArrayList<>();for(JsonElement value:values) {
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Node IDs must be strings");
            route.add(value.getAsString());
        }
        return INK.validateRoute(route,revision,MapPlanner::known,MapPlanner::connected);
    }
}
