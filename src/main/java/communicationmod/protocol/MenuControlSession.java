package communicationmod.protocol;

import com.google.gson.*;
import java.util.*;
import java.util.function.Supplier;

/** Game-thread menu-only v2 session. Stable menus keep their ID until something changes. */
public final class MenuControlSession {
    private final ProtocolSession protocol=new ProtocolSession(e->System.err.println("[COMM-MENU] "+e));
    private final Supplier<JsonObject> observe;
    private final Supplier<List<ProtocolSession.Action>> actions;
    private boolean connected;
    private String lastView, published, awaitingChangeFrom;
    private int stableFrames;
    private final boolean playControl;

    public MenuControlSession(Supplier<JsonObject> observe,Supplier<List<ProtocolSession.Action>> actions) {
        this(observe,actions,false);
    }
    public MenuControlSession(Supplier<JsonObject> observe,Supplier<List<ProtocolSession.Action>> actions,boolean playControl) {
        this.observe=Objects.requireNonNull(observe);this.actions=Objects.requireNonNull(actions);
        this.playControl=playControl;
    }
    public boolean connected(){return connected;}

    public String receive(String line) {
        // Manual UI or offered-action changes must invalidate even between publication and dispatch.
        String before=null;
        if(connected) {
            JsonObject current=observe.get();
            List<ProtocolSession.Action> offered=actions.get();
            before=decisionKey(current,offered);
            if(awaitingChangeFrom!=null) {
                if(!before.equals(awaitingChangeFrom))reset();
            } else if(!before.equals(lastView))reset();
        }
        String response=protocol.receive(line);
        JsonObject reply=new JsonParser().parse(response).getAsJsonObject();
        String type=reply.get("type").getAsString();
        if(type.equals("hello")) {
            connected=true;reply.addProperty("mode",playControl?"play_control":"menu_control");
            reply.getAsJsonArray("capabilities").add("menu_navigation");
            if(playControl)reply.getAsJsonArray("capabilities").add("partial_run_control");
            response=reply.toString();
        } else if(type.equals("result")) {
            // A successful dispatch is not proof that the next screen has finished rendering.
            // Do not re-arm the exact same decision while its click/transition is still being
            // consumed; wait until the visible decision OR offered action set actually changes.
            awaitingChangeFrom="applied".equals(reply.has("status")?reply.get("status").getAsString():"")?before:null;
            lastView=null;published=null;stableFrames=0;
        }
        return response;
    }

    public String update(JsonObject runtime) {
        if(!connected)return null;
        JsonObject view=observe.get();
        List<ProtocolSession.Action> offered=actions.get();
        String key=decisionKey(view,offered);
        if(awaitingChangeFrom!=null) {
            if(key.equals(awaitingChangeFrom)) {
                if(!key.equals(lastView)){protocol.invalidate();lastView=key;}
                stableFrames=0;
                String next=key+"/awaiting-change";
                if(next.equals(published))return null;
                published=next;
                return protocol.publish(runtime,view,Collections.emptyList(),false,"unsupported_or_transition");
            }
            awaitingChangeFrom=null;lastView=null;published=null;stableFrames=0;
        }
        if(!key.equals(lastView)){protocol.invalidate();stableFrames=0;lastView=key;}
        boolean stable=view.getAsJsonObject("menu").get("stable").getAsBoolean();
        stableFrames=stable?Math.min(2,stableFrames+1):0;
        boolean ready=stableFrames>=2;
        String next=key+"/"+ready;
        if(next.equals(published))return null;
        published=next;
        return protocol.publish(runtime,view,ready?offered:Collections.emptyList(),ready,
            stable?"supported":"unsupported_or_transition");
    }
    private void reset(){protocol.invalidate();lastView=null;published=null;stableFrames=0;awaitingChangeFrom=null;}
    private String decisionKey(JsonObject view,List<ProtocolSession.Action> offered) {
        String viewKey;
        if(!playControl)viewKey=view.toString();
        else {
            JsonObject key=new JsonParser().parse(view.toString()).getAsJsonObject();
            if(key.has("game_state")) {
                JsonObject game=key.getAsJsonObject("game_state");
                if(game.has("narrative"))game.getAsJsonObject("narrative").remove("render_frame");
            }
            viewKey=key.toString();
        }
        return viewKey+"\nACTIONS="+ProtocolSession.actionKey(offered);
    }
}
