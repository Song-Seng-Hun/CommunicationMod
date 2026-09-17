import java.lang.reflect.*;
import java.util.*;

/** JDK-only: models render callbacks, never constructs game/UI objects. */
public final class DialogueHistoryTest {
    private static Class<?> type;
    private static Object history;
    private static int checks;
    public static void main(String[] args) throws Exception {
        try { type = Class.forName("communicationmod.observation.DialogueHistory"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Rendered dialogue history is missing", missing); }
        history = type.getConstructor().newInstance();
        Object bubble = new Object();
        String session = (String)snapshot().get("session_id");
        frame();
        equal(events().size(), 0); // Construction/queued text is not an input at all.
        observe(bubble, "안녕", "neow", "니오우", "KOR");
        equal(events().size(), 0); // Partial frame is not published.
        finish();
        equal(events().size(), 1);
        Map<?, ?> first = events().get(0);
        equal(first.get("text"), "안녕");
        equal(first.get("speaker_name"), "니오우");
        equal(first.get("currently_displayed"), true);
        Method visibleText;
        try { visibleText=type.getMethod("visibleText",Object.class); }
        catch(NoSuchMethodException missing) { throw new AssertionError("Current event generation cannot be queried safely",missing); }
        equal(visibleText.invoke(history,bubble),"안녕");
        equal(visibleText.invoke(history,new Object()),""); // New body must not fall back to a previous page.
        Method due;
        try { due=type.getMethod("notificationDue",long.class); }
        catch(NoSuchMethodException missing) { throw new AssertionError("Dialogue notifications are not rate limited",missing); }
        equal(due.invoke(history,0L),true);
        equal(due.invoke(history,10L),false);
        Object id = first.get("id");
        frame(); observe(bubble, "안녕 여행자", "neow", "니오우", "KOR"); finish();
        equal(events().size(), 1);
        equal(events().get(0).get("id"), id);
        equal(events().get(0).get("text"), "안녕 여행자");
        equal(due.invoke(history,100000000L),false);
        equal(due.invoke(history,250000000L),true);
        equal(due.invoke(history,500000000L),false);
        equal(first.get("text"), "안녕"); // Detached snapshots.
        frame(); observe(bubble, "여행자", "neow", "니오우", "KOR"); finish();
        equal(events().get(0).get("text"), "안녕 여행자"); // Fade does not destroy fuller observed text.
        equal(events().get(0).get("visible_text"), "여행자");
        frame(); finish();
        equal(events().get(0).get("currently_displayed"), false);
        equal(events().get(0).get("visible_text"), "");
        equal(visibleText.invoke(history,bubble),"");
        frame(); observe(new Object(), "안녕 여행자", "unknown", null, "KOR"); finish();
        equal(events().size(), 2); // A separate utterance is not deduplicated by text.
        equal(events().get(1).get("speaker_name"), null);
        equal(Objects.equals(events().get(1).get("id"), id), false);
        frame(); observe(new Object(), "", "unknown", null, "KOR"); finish();
        equal(events().size(), 2);
        frame(); observe(new Object(), "failed observer frame", "unknown", null, "ENG");
        try { call("abortFrame"); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Failed frame cannot be discarded safely", missing); }
        finish(); equal(events().size(), 2);
        frame(); observe(new Object(), "behind final fade", "unknown", null, "ENG");
        try { type.getMethod("completeFrame",boolean.class).invoke(history,false); }
        catch(NoSuchMethodException missing) { throw new AssertionError("Final overlay cannot discard frame text",missing); }
        equal(events().size(),2);
        frame(); observe(new Object(), "not actually completed", "unknown", null, "ENG");
        frame(); finish(); // Aborted rendering cannot leak a partial frame.
        equal(events().size(), 2);
        frame(); observe(new Object(), String.join("", Collections.nCopies(9000, "가")), "merchant", null, "KOR"); finish();
        equal(events().get(2).get("text_truncated"), true);
        equal(((String)events().get(2).get("text")).length() <= 8192, true);
        for (int i=0; i<90; i++) {
            frame(); observe(new Object(), "말 " + i, "unknown", null, "KOR"); finish();
        }
        equal(events().size() <= 64, true);
        equal(((Number)snapshot().get("dropped_entries")).longValue() > 0, true);
        call("reset");
        equal(events().size(), 0);
        equal(Objects.equals(session, snapshot().get("session_id")), false);
        System.out.println("PASS: " + checks + " dialogue-history assertions (JDK-only)");
    }
    private static void frame() throws Exception { call("beginFrame"); }
    private static void finish() throws Exception { call("completeFrame"); }
    private static void call(String name) throws Exception { type.getMethod(name).invoke(history); }
    private static void observe(Object owner, String text, String kind, String name, String language) throws Exception {
        Map<String,String> context = new LinkedHashMap<>(); context.put("room_type", "EventRoom");
        type.getMethod("observe", Object.class, String.class, String.class, String.class, String.class, Map.class)
            .invoke(history, owner, text, kind, name, language, context);
        context.put("room_type", "mutated");
    }
    private static Map<?, ?> snapshot() throws Exception { return (Map<?, ?>)type.getMethod("snapshot").invoke(history); }
    @SuppressWarnings("unchecked") private static List<Map<?, ?>> events() throws Exception {
        return (List<Map<?, ?>>)snapshot().get("entries");
    }
    private static void equal(Object actual, Object expected) {
        checks++; if (!Objects.equals(actual,expected)) throw new AssertionError("expected <"+expected+"> but was <"+actual+">");
    }
}
