import java.lang.reflect.*;
import java.util.*;

/** Executes the real pure gate using only the JDK, never game/Steam classes. */
public final class CombatDecisionTest {
    private static Class<?> type;
    private static Object gate;
    private static int checks;
    public static void main(String[] args) throws Exception {
        try { type=Class.forName("communicationmod.observation.CombatDecision"); }
        catch(ClassNotFoundException e) { throw new AssertionError("Missing combat decision gate: partial draws are not protected",e); }
        gate=type.getConstructor().newInstance();
        frame("play","A", "pending_actions"); check(!ready(),"partial first card must not be playable");
        frame("play","A,B", "pending_actions"); check(!ready(),"partial two cards must not be playable");
        frame("play","A,B,C", null); check(!ready(),"one apparently idle frame is insufficient");
        // Reading state repeatedly is NOT a completed frame.
        state(); state(); check(!ready(),"observation calls cannot advance stability");
        frame("play","A,B,C", null); check(ready(),"whole settled hand is ready");
        String id=issue(); check(id!=null,"stable state receives a decision token");
        expect(issue(),id);
        reject(() -> claim(id,"play","A,B,C,D",null));
        check(!ready(),"dispatch mismatch invalidates the published decision");
        frame("play","A,B,C,D",null); frame("play","A,B,C,D",null);
        String next=issue(); check(!next.equals(id),"new hand has new token");
        reject(() -> claim(id,"play","A,B,C,D",null));
        // Rejecting an old token must not destroy the valid current reading.
        claim(next,"play","A,B,C,D",null); check(!ready(),"claim consumes before dispatch");
        reject(() -> claim(next,"play","A,B,C,D",null));
        frame("play","A,B,C,D",null); frame("play","A,B,C,D",null);
        check(!ready(),"unchanged frame cannot replay consumed/failed dispatch");
        frame("play","A,B,C,D","pending_actions");
        frame("play","A,C,D",null); frame("play","A,C,D",null);
        check(ready(),"after action resolution a new full-hand decision is allowed");
        String beforeDraw=issue(); reject(() -> claim(beforeDraw,"play","A,C,D","drawing"));
        check(!ready(),"work that starts after publication blocks dispatch");
        frame("selection","grid:card1,card2:selected0",null);
        frame("selection","grid:card1,card2:selected0",null);
        check(ready(),"forced selection need not drain paused combat queue");
        String selection=issue();
        reject(() -> claim(selection,"play","grid:card1,card2:selected0",null));
        frame("selection","grid:card1,card2:selected0",null);
        frame("selection","grid:card1,card2:selected0",null);
        String refreshedSelection=issue(); claim(refreshedSelection,"selection","grid:card1,card2:selected0",null);
        frame("selection","grid:card1,card2:selected1",null);
        frame("selection","grid:card1,card2:selected1",null);
        check(ready(),"multi-selection advances after actual selection state change");
        for(String reason:Arrays.asList("current_action","pre_turn","monster_queue","end_turn","moving_hand","popup","fade","unknown_screen")) {
            frame("play","A,B,C",reason); check(!ready(),"blocked: "+reason);
        }
        frame("play","duplicate-uuid","invalid_hand"); check(!ready(),"ambiguous identity fails closed");
        call("reset",new Class<?>[]{}); check(!ready(),"new run cannot inherit a decision");
        System.out.println("PASS: "+checks+" combat stability/partial-hand/stale/duplicate/selection assertions (JDK-only)");
    }
    private static Object call(String method,Class<?>[] types,Object... args) throws Exception {
        try { return type.getMethod(method,types).invoke(gate,args); }
        catch(InvocationTargetException e) { if(e.getCause() instanceof IllegalArgumentException)throw (IllegalArgumentException)e.getCause(); throw e; }
    }
    private static void frame(String mode,String key,String reason) throws Exception { call("completeFrame",new Class<?>[]{String.class,String.class,String.class},mode,key,reason); }
    private static Map<?,?> state() throws Exception { return (Map<?,?>)call("snapshot",new Class<?>[]{}); }
    private static boolean ready() throws Exception { return Boolean.TRUE.equals(state().get("ready")); }
    private static String issue() throws Exception { return (String)call("issue",new Class<?>[]{}); }
    private static void claim(String id,String mode,String key,String reason) throws Exception { call("claim",new Class<?>[]{String.class,String.class,String.class,String.class},id,mode,key,reason); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Attempt action) throws Exception { checks++; try{action.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Premature/stale action accepted"); }
    private static void check(boolean ok,String msg) { checks++; if(!ok)throw new AssertionError(msg); }
    private static void expect(Object actual,Object expected) { check(Objects.equals(actual,expected),"unstable token"); }
}
