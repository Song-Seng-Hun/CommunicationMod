import communicationmod.observation.EventReading;
import java.util.*;

/** JDK-only event conversation contract; no game or external AI is started. */
public final class EventReadingTest {
    private static int assertions;
    private static final List<EventReading.Option> OPTIONS = Arrays.asList(
        new EventReading.Option("[다가간다] 체력 5 회복", false),
        new EventReading.Option("[잠김] 열쇠 필요", true),
        new EventReading.Option("[떠난다]", false));
    public static void main(String[] args) {
        EventReading r = new EventReading(); Object page = new Object();
        check(!r.canChoose(), "no page is fail-closed");
        r.observe(page, "낡은", "KOR", OPTIONS, false, true);
        expect(r.phase(), "revealing");
        reject(() -> r.acknowledge(id(r), "아직 읽는 중"));
        reject(() -> r.choose(0));
        String partial = id(r);
        r.observe(page, "낡은 제단 앞에 도착했다.\n무엇을 할까?", "KOR", OPTIONS, true, true);
        expect(r.phase(), "discussion_required");
        reject(() -> r.acknowledge(partial, "오래된 본문"));
        reject(() -> r.acknowledge(id(r), " \n\t"));
        reject(() -> r.acknowledge(id(r), repeat('a', 4097)));
        reject(() -> r.choose(0));
        String full = id(r);
        r.acknowledge(full, "제단을 발견했네요. 회복하거나 그냥 떠날 수 있어요.");
        expect(r.phase(), "ready_to_choose");
        r.observe(page, "낡은 제단 앞에 도착했다.\n무엇을 할까?", "KOR", OPTIONS, true, true);
        expect(id(r), full); check(r.canChoose(), "unchanged frame preserves discussion");
        reject(() -> r.acknowledge(full, "중복"));
        reject(() -> r.choose(-1)); reject(() -> r.choose(2));
        r.choose(1); // Disabled options do not consume a choice index.
        expect(r.phase(), "awaiting_result"); reject(() -> r.choose(1));
        r.observe(page, "낡은 제단 앞에 도착했다.\n무엇을 할까?", "KOR", OPTIONS, true, true);
        expect(r.phase(), "awaiting_result");
        Map<?,?> last = (Map<?,?>)r.snapshot().get("last_discussion");
        expect(last.get("selected_option"), "[떠난다]");
        Object outcome = new Object(); r.newPage(outcome);
        expect(r.snapshot().get("body_text"), "");
        r.observe(outcome, "당신은 제단을 뒤로 하고 떠났다.", "KOR",
            Collections.singletonList(new EventReading.Option("[계속]", false)), true, true);
        expect(r.phase(), "discussion_required");
        expect(r.snapshot().get("page_role"), "after_choice");
        reject(() -> r.acknowledge(full, "이전 페이지 확인"));
        r.acknowledge(id(r), "제단을 건드리지 않고 떠났군요.");
        r.suspend(); expect(r.phase(), "hidden"); check(!r.canChoose(), "overlay blocks choices");
        r.observe(outcome, "당신은 제단을 뒤로 하고 떠났다.", "KOR",
            Collections.singletonList(new EventReading.Option("[계속]", false)), true, true);
        expect(r.phase(), "discussion_required");
        r.acknowledge(id(r), "이제 계속 가겠습니다.");
        r.observe(outcome, "당신은 제단을 뒤로 하고 떠났다.", "KOR",
            Collections.singletonList(new EventReading.Option("[계속]", true)), true, true);
        expect(r.phase(), "discussion_required"); check(!r.canChoose(), "changed options invalidate acknowledgement");
        r.acknowledge(id(r), "선택지가 잠겼네요."); reject(() -> r.choose(0));
        r.newPage(new Object()); check(!r.canChoose(), "identical text in new page still needs reading");
        r.observe(new Object(), repeat('x',8193), "ENG", OPTIONS, true, true);
        expect(r.phase(), "unavailable"); reject(() -> r.acknowledge(id(r), "truncated"));
        Object huge = new Object();
        r.observe(huge, repeat('x',8193), "ENG", OPTIONS, true, true);
        String budgetId = id(r);
        r.observe(huge, repeat('x',8193), "ENG", OPTIONS, true, true);
        expect(id(r), budgetId); // No unlimited readiness notifications for repeated overflow.
        expect(r.snapshot().get("unavailable_reason"), "observation_budget_exceeded");
        r.reset(); expect(r.phase(), "unavailable");
        check(r.snapshot().get("last_discussion") == null, "run reset clears retained discussion");
        // A dialog can update options without calling updateBodyText.
        page = new Object(); r.observe(page,"Same", "ENG",OPTIONS,true,true);
        r.acknowledge(id(r),"Read"); r.choose(0);
        r.observe(page,"Same", "ENG",Collections.singletonList(new EventReading.Option("Leave",false)),true,true);
        expect(r.phase(),"discussion_required"); expect(r.snapshot().get("page_role"),"after_choice");
        Map<String,Object> copy=r.snapshot(); ((List<?>)copy.get("options")).clear();
        check(((List<?>)r.snapshot().get("options")).size()==1,"snapshots detached");
        System.out.println("PASS: event reading/discussion/outcome gating ("+assertions+" assertions), JDK-only");
    }
    private static String id(EventReading r) { return (String)r.snapshot().get("reading_id"); }
    private static String repeat(char c,int n) { char[] chars=new char[n]; Arrays.fill(chars,c); return new String(chars); }
    private static void check(boolean ok,String why) { assertions++; if(!ok)throw new AssertionError(why); }
    private static void expect(Object actual,Object expected) { check(Objects.equals(actual,expected), "expected "+expected+" got "+actual); }
    private static void reject(Runnable action) {
        assertions++; try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid event action accepted");
    }
}
