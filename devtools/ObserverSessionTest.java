import com.google.gson.*;
import java.lang.reflect.*;

/** Runs without game/native classes. */
public final class ObserverSessionTest {
    public static void main(String[] args) throws Exception {
        Class<?> type;
        try { type = Class.forName("communicationmod.protocol.ObserverSession"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing passive live v2 session", e); }
        Object session = type.getConstructor().newInstance();
        Method receive = type.getMethod("receive", String.class);
        Method publish = type.getMethod("publish", JsonObject.class, JsonObject.class);
        check(reply(receive,session,"ready").get("code").getAsString().equals("VERSION_MISMATCH"),"legacy refused");
        check(reply(receive,session,"{\"type\":\"get_state\"}").get("code").getAsString().equals("HANDSHAKE_REQUIRED"),"handshake required");
        JsonObject hello = reply(receive,session,"{\"type\":\"hello\",\"protocol_version\":2}");
        check(hello.get("type").getAsString().equals("hello"),"hello accepted");
        JsonObject view = new JsonParser().parse("{\"available_commands\":[\"play\"],\"ready_for_command\":true,\"combat_decision\":{\"ready\":true},\"game_state\":{\"current_action\":\"PrivateAction\",\"combat_state\":{\"hand\":[{\"name\":\"방어\",\"description\":\"방어도를 5 얻습니다.\"}],\"monsters\":[{\"move_id\":2,\"last_move_id\":1,\"intent\":\"ATTACK\"}]}}}").getAsJsonObject();
        JsonObject state = new JsonParser().parse((String)publish.invoke(session,new JsonObject(),view)).getAsJsonObject();
        check(!state.get("ready").getAsBoolean() && state.getAsJsonArray("actions").size()==0,"passive mode cannot offer actions");
        check(state.get("support").getAsString().equals("observation_only"),"partial support explicit");
        JsonObject output=state.getAsJsonObject("observation");
        check(!output.has("available_commands") && !output.has("ready_for_command"),"no legacy affordances");
        check(!output.getAsJsonObject("game_state").has("current_action"),"no private action names");
        JsonObject monster=output.getAsJsonObject("game_state").getAsJsonObject("combat_state").getAsJsonArray("monsters").get(0).getAsJsonObject();
        check(!monster.has("move_id") && !monster.has("last_move_id") && monster.has("intent"),"only public monster intent");
        check(state.toString().contains("방어도를 5"),"localized text retained");
        check(view.has("available_commands"),"caller input unchanged");
        JsonObject hidden=new JsonParser().parse("{\"monsters\":[{\"intent\":\"UNKNOWN\",\"move_base_damage\":30,\"move_adjusted_damage\":40,\"move_hits\":2},{\"intent\":\"ATTACK_BUFF\",\"move_base_damage\":4,\"move_adjusted_damage\":5,\"move_hits\":3}]}").getAsJsonObject();
        JsonObject filtered=new JsonParser().parse((String)publish.invoke(session,new JsonObject(),hidden)).getAsJsonObject();
        JsonArray enemies=filtered.getAsJsonObject("observation").getAsJsonArray("monsters");
        check(!enemies.get(0).getAsJsonObject().has("move_adjusted_damage") && !enemies.get(0).getAsJsonObject().has("move_hits"),"hidden intent damage withheld");
        check(!enemies.get(1).getAsJsonObject().has("move_base_damage") && enemies.get(1).getAsJsonObject().get("move_adjusted_damage").getAsInt()==5,"UI damage retained, internal base damage removed");
        state = new JsonParser().parse((String)publish.invoke(session,new JsonObject(),view)).getAsJsonObject();
        JsonObject request=new JsonObject(); request.addProperty("type","act"); request.addProperty("session_id",hello.get("session_id").getAsString());
        request.addProperty("request_id","one"); request.addProperty("state_id",state.get("state_id").getAsLong()); request.addProperty("action_id","end_turn"); request.add("arguments",new JsonObject());
        check(reply(receive,session,request.toString()).get("code").getAsString().equals("NOT_READY"),"mutation refused");
        check(reply(receive,session,"{\"type\":\"get_state\"}").equals(state),"latest state round trip");
        System.out.println("PASS: 14 passive v2 handshake/privacy/mutation/localization checks");
    }
    private static JsonObject reply(Method m,Object s,String line)throws Exception{return new JsonParser().parse((String)m.invoke(s,line)).getAsJsonObject();}
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
}
