import java.lang.reflect.*;
import java.util.*;
public final class DisplayedCostTest {
    public static void main(String[] args)throws Exception {
        Class<?> type;try{type=Class.forName("communicationmod.observation.DisplayedCost");}catch(ClassNotFoundException e){throw new AssertionError("Missing rendered cost cache",e);}
        Object costs=type.newInstance(),card=new Object();Method begin=type.getMethod("beginFrame"),complete=type.getMethod("completeFrame"),record=type.getMethod("record",Object.class,String.class,String.class),read=type.getMethod("read",Object.class,String.class);
        check(Boolean.FALSE.equals(map(read,costs,card,"a").get("displayed_cost_complete")),"no renderer data stays unknown");
        begin.invoke(costs);record.invoke(costs,card,"a","0");
        check(Boolean.FALSE.equals(map(read,costs,card,"a").get("displayed_cost_complete")),"unfinished render is not readable");
        complete.invoke(costs);Map<?,?> zero=map(read,costs,card,"a");
        check(zero.get("displayed_cost").equals(0) && zero.get("displayed_cost_text").equals("0"),"free displayed cost");
        check(Boolean.FALSE.equals(map(read,costs,card,"b").get("displayed_cost_complete")),"changed context invalidates cost");
        begin.invoke(costs);complete.invoke(costs);
        check(Boolean.FALSE.equals(map(read,costs,card,"a").get("displayed_cost_complete")),"not rendered this frame stays unknown");
        for(String text:new String[]{"X","?","2"}){begin.invoke(costs);record.invoke(costs,card,"a",text);complete.invoke(costs);Map<?,?> value=map(read,costs,card,"a");check(value.get("displayed_cost_text").equals(text),"verbatim display "+text);if(!text.equals("2"))check(!value.containsKey("displayed_cost"),"no invented numeric cost");}
        check(Boolean.FALSE.equals(map(read,costs,new Object(),"a").get("displayed_cost_complete")),"detached upgrade copy cannot reuse live cost");
        System.out.println("PASS: current-frame rendered costs, free/X/unknown text, stale context and preview-copy isolation");
    }
    static Map<?,?> map(Method read,Object owner,Object card,String key)throws Exception{return (Map<?,?>)read.invoke(owner,card,key);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
