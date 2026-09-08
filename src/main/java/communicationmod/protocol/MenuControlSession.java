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
    private String lastView, published;
    private int stableFrames;

    public MenuControlSession(Supplier<JsonObject> observe,Supplier<List<ProtocolSession.Action>> actions) {
        this.observe=Objects.requireNonNull(observe);this.actions=Objects.requireNonNull(actions);
    }
    public boolean connected(){return connected;}

    public String receive(String line) {
        // Manual UI changes must invalidate even between publication and dispatch.
        if(connected && !observe.get().toString().equals(lastView))reset();
        String response=protocol.receive(line);
        JsonObject reply=new JsonParser().parse(response).getAsJsonObject();
        String type=reply.get("type").getAsString();
        if(type.equals("hello")) {
            connected=true;reply.addProperty("mode","menu_control");
            reply.getAsJsonArray("capabilities").add("menu_navigation");
            response=reply.toString();
        } else if(type.equals("result")) {
            // An action result is not proof that the next screen has finished rendering.
            lastView=null;published=null;stableFrames=0;
        }
        return response;
    }

    public String update(JsonObject runtime) {
        if(!connected)return null;
        JsonObject view=observe.get();String key=view.toString();
        boolean stable=view.getAsJsonObject("menu").get("stable").getAsBoolean();
        if(!key.equals(lastView)){protocol.invalidate();stableFrames=0;lastView=key;}
        stableFrames=stable?Math.min(2,stableFrames+1):0;
        boolean ready=stableFrames>=2;
        String next=key+"/"+ready;
        if(next.equals(published))return null;
        published=next;
        return protocol.publish(runtime,view,ready?actions.get():Collections.emptyList(),ready,
            stable?"supported":"unsupported_or_transition");
    }
    private void reset(){protocol.invalidate();lastView=null;published=null;stableFrames=0;}
}
