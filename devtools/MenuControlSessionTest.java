import com.google.gson.*;
import communicationmod.protocol.ProtocolSession;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Pure real protocol/session checks: no native game classes. */
public final class MenuControlSessionTest {
    private static Object session;
    private static Method update, receive;
    private static JsonObject view;
    private static final AtomicInteger calls=new AtomicInteger();
    public static void main(String[] args)throws Exception {
        Class<?> type;
        try{type=Class.forName("communicationmod.protocol.MenuControlSession");}
        catch(ClassNotFoundException missing){throw new AssertionError("Missing live menu control session",missing);}
        view=menu("MAIN_MENU",true);
        Supplier<JsonObject> observe=()->view;
        Supplier<List<ProtocolSession.Action>> actions=()->Collections.singletonList(new ProtocolSession.Action(
            "menu.play","플레이",new JsonObject(),a->{if(!a.entrySet().isEmpty())throw new IllegalArgumentException();},a->calls.incrementAndGet()));
        session=type.getConstructor(Supplier.class,Supplier.class).newInstance(observe,actions);
        update=type.getMethod("update",JsonObject.class);receive=type.getMethod("receive",String.class);
        check(reply("ready").get("code").getAsString().equals("VERSION_MISMATCH"),"legacy blocked");
        JsonObject hello=reply("{\"type\":\"hello\",\"protocol_version\":2}");
        check(hello.get("mode").getAsString().equals("menu_control"),"explicit mode");
        JsonObject first=tick();check(!first.get("ready").getAsBoolean(),"first frame not ready");
        JsonObject state=tick();check(state.get("ready").getAsBoolean(),"second stable frame ready");
        check(state.getAsJsonArray("actions").get(0).getAsJsonObject().get("label").getAsString().equals("플레이"),"localized label");
        check(tick()==null,"unchanged frames retain state ID for a thinking client");
        JsonObject request=request(state,"one");
        request.getAsJsonObject("arguments").addProperty("unlisted",1);
        check(reply(request.toString()).get("code").getAsString().equals("INVALID_ARGUMENTS"),"extra arguments rejected");
        request.add("arguments",new JsonObject());
        check(reply(request.toString()).get("status").getAsString().equals("applied") && calls.get()==1,"one UI action");
        check(reply(request.toString()).get("code").getAsString().equals("DUPLICATE_REQUEST") && calls.get()==1,"duplicate cannot replay");
        tick();state=tick();
        request=request(state,"manual-change");view=menu("CHAR_SELECT",true);
        check(reply(request.toString()).get("code").getAsString().equals("STALE_STATE") && calls.get()==1,"manual change rechecked before command");
        tick();state=tick();
        view=menu("CHAR_SELECT",false);
        check(reply(request(state,"overlay").toString()).get("code").getAsString().equals("STALE_STATE"),"overlay invalidates before action");
        check(!tick().get("ready").getAsBoolean(),"overlay not ready");
        check(tick()==null,"blocked state not flooded");
        view=menu("UNSUPPORTED",false);check(tick().getAsJsonArray("actions").size()==0,"unsupported screen has no actions");
        check(calls.get()==1,"no accidental mutations");
        view=menu("MAIN_MENU",true);
        Supplier<List<ProtocolSession.Action>> failing=()->Collections.singletonList(new ProtocolSession.Action(
            "menu.play","Play",new JsonObject(),a->{},a->{calls.incrementAndGet();throw new IllegalStateException("partial UI failure fixture");}));
        session=type.getConstructor(Supplier.class,Supplier.class).newInstance(observe,failing);
        reply("{\"type\":\"hello\",\"protocol_version\":2}");tick();state=tick();request=request(state,"partial-failure");
        check(reply(request.toString()).get("status").getAsString().equals("failed"),"partial effect failure reported");
        check(reply(request.toString()).get("code").getAsString().equals("DUPLICATE_REQUEST") && calls.get()==2,"failed effect cannot replay");
        view=menu("COMBAT",true);JsonObject game=new JsonObject(),narrative=new JsonObject();
        narrative.addProperty("render_frame",1);game.add("narrative",narrative);view.add("game_state",game);
        session=type.getConstructor(Supplier.class,Supplier.class,boolean.class).newInstance(observe,actions,true);
        check(reply("{\"type\":\"hello\",\"protocol_version\":2}").get("mode").getAsString().equals("play_control"),"explicit play mode");
        tick();narrative.addProperty("render_frame",2);
        check(tick().get("ready").getAsBoolean(),"diagnostic render counters must not prevent a stable decision");
        narrative.addProperty("render_frame",3);check(tick()==null,"render counter alone retains decision state ID");
        System.out.println("PASS: menu v2 mode, two-frame stability, retained state IDs, localization, stale/manual/overlay/duplicate/argument guards");
    }
    private static JsonObject menu(String screen,boolean stable){JsonObject root=new JsonObject(),menu=new JsonObject();menu.addProperty("screen",screen);menu.addProperty("stable",stable);root.add("menu",menu);return root;}
    private static JsonObject tick()throws Exception{String value=(String)update.invoke(session,new JsonObject());return value==null?null:new JsonParser().parse(value).getAsJsonObject();}
    private static JsonObject reply(String line)throws Exception{return new JsonParser().parse((String)receive.invoke(session,line)).getAsJsonObject();}
    private static JsonObject request(JsonObject state,String id){JsonObject r=new JsonObject();r.addProperty("type","act");r.add("session_id",state.get("session_id"));r.add("state_id",state.get("state_id"));r.addProperty("request_id",id);r.addProperty("action_id","menu.play");r.add("arguments",new JsonObject());return r;}
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
}
