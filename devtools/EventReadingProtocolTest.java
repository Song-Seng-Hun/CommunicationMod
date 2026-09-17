import communicationmod.observation.EventReading;
import communicationmod.protocol.EventReadingActions;
import communicationmod.protocol.ProtocolSession;
import com.google.gson.*;
import java.util.*;

/** Demonstrates a client reading and commenting on BOTH pages, without a game or an AI service. */
public final class EventReadingProtocolTest {
    private static final Gson GSON = new Gson();
    private static int checks;
    public static void main(String[] args) {
        EventReading reading = new EventReading();
        ProtocolSession session = new ProtocolSession(e -> { throw new AssertionError(e); });
        session.receive("{\"type\":\"hello\",\"protocol_version\":2}");
        Object page = new Object();
        List<EventReading.Option> options = Collections.singletonList(new EventReading.Option("[손을 뻗는다] 체력 5 회복", false));
        reading.observe(page,"빛나는", "KOR", options, false,true);
        check(EventReadingActions.offer(reading, () -> {}).isEmpty(),"no acknowledgement during reveal");
        reading.observe(page,"빛나는 샘을 발견했다.\n맑은 물이 흐르고 있다.","KOR",options,true,true);
        JsonObject state = publish(session,reading);
        check(state.getAsJsonArray("actions").size()==1,"only discussion is offered");
        String stale = request(state,"ack","acknowledge_event_reading",reading.readingId()," ");
        expect(parse(session.receive(stale)).get("code").getAsString(),"INVALID_ARGUMENTS");
        String discussion = "맑은 샘을 발견했네요. 물에 손을 대면 체력을 회복할 수 있어요.";
        System.out.println("FIXTURE narrator: "+state.getAsJsonObject("observation").getAsJsonObject("event_reading").get("body_text").getAsString());
        System.out.println("FIXTURE options: "+state.getAsJsonObject("observation").getAsJsonObject("event_reading").get("options"));
        System.out.println("FIXTURE commentary: "+discussion);
        String ack = request(state,"ack","acknowledge_event_reading",reading.readingId(),discussion);
        expect(parse(session.receive(ack)).get("status").getAsString(),"applied");
        expect(parse(session.receive(ack)).get("code").getAsString(),"DUPLICATE_REQUEST");
        state = publish(session,reading);
        check(state.getAsJsonArray("actions").size()==1,"choice replaces discussion after acknowledgement");
        JsonObject choose=base(state,"choose","event_choice_0"); choose.add("arguments",new JsonObject());
        expect(parse(session.receive(choose.toString())).get("status").getAsString(),"applied");
        expect(reading.phase(),"awaiting_result");
        reading.observe(new Object(),"차가운 물이 피로를 씻어냈다.\n당신은 다시 길을 나선다.","KOR",options,true,true);
        state=publish(session,reading);
        expect(reading.phase(),"discussion_required");
        JsonObject forbidden=base(state,"skip-outcome","event_choice_0"); forbidden.add("arguments",new JsonObject());
        expect(parse(session.receive(forbidden.toString())).get("code").getAsString(),"UNKNOWN_ACTION");
        System.out.println("FIXTURE outcome: "+reading.snapshot().get("body_text"));
        String resultComment = "물이 피로를 씻어줬군요. 이제 다음 길로 갈 수 있겠어요.";
        System.out.println("FIXTURE commentary: "+resultComment);
        expect(parse(session.receive(request(state,"outcome","acknowledge_event_reading",reading.readingId(),resultComment))).get("status").getAsString(),"applied");
        // Revalidate at dispatch, even when the protocol state has not yet been invalidated by a live adapter.
        reading.observe(new Object(),"A new page", "ENG",options,true,true);
        state=publish(session,reading);
        String oldId=reading.readingId(); reading.suspend();
        expect(parse(session.receive(request(state,"hidden","acknowledge_event_reading",oldId,"Read"))).get("code").getAsString(),"INVALID_ARGUMENTS");
        reading.observe(new Object(),"Another", "ENG",options,true,true);
        List<ProtocolSession.Action> offered=EventReadingActions.offer(reading, reading::suspend);
        state=parse(session.publish(new JsonObject(),new JsonObject(),offered,true,"supported"));
        expect(parse(session.receive(request(state,"refresh","acknowledge_event_reading",reading.readingId(),"Read"))).get("code").getAsString(),"INVALID_ARGUMENTS");
        System.out.println("PASS: event v2 read/comment/choose/result fixture ("+checks+" assertions); not live gameplay or generated AI reasoning");
    }
    private static JsonObject publish(ProtocolSession s,EventReading r) {
        List<ProtocolSession.Action> actions=new ArrayList<>(EventReadingActions.offer(r,()->{}));
        if(r.canChoose()) actions.add(new ProtocolSession.Action("event_choice_0","[손을 뻗는다]",new JsonObject(),
            a -> r.validateChoice(0),a -> r.choose(0)));
        JsonObject observation=new JsonObject(); observation.add("event_reading",GSON.toJsonTree(r.snapshot()));
        JsonObject runtime=new JsonObject(); runtime.addProperty("kind","event_fixture_only");
        return parse(s.publish(runtime,observation,actions,true,"supported"));
    }
    private static String request(JsonObject state,String request,String action,String reading,String commentary) {
        JsonObject obj=base(state,request,action), args=new JsonObject();
        args.addProperty("reading_id",reading); args.addProperty("commentary",commentary); obj.add("arguments",args); return obj.toString();
    }
    private static JsonObject base(JsonObject state,String request,String action) {
        JsonObject obj=new JsonObject(); obj.addProperty("type","act"); obj.add("session_id",state.get("session_id"));
        obj.add("state_id",state.get("state_id")); obj.addProperty("request_id",request); obj.addProperty("action_id",action); return obj;
    }
    private static JsonObject parse(String s) { return new JsonParser().parse(s).getAsJsonObject(); }
    private static void check(boolean ok,String why) { checks++; if(!ok)throw new AssertionError(why); }
    private static void expect(String actual,String expected) { check(actual.equals(expected),actual+" != "+expected); }
}
