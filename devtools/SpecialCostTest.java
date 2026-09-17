import java.lang.reflect.*;
import java.util.*;
public final class SpecialCostTest {
    public static void main(String[] args)throws Exception {
        Class<?> type;try{type=Class.forName("communicationmod.observation.SpecialCost");}catch(ClassNotFoundException missing){throw new AssertionError("Missing native special cost projection",missing);}
        Method project=type.getMethod("describe",String.class,int.class,int.class,boolean.class,int.class,int.class,int.class,boolean.class,boolean.class);
        Map<?,?> reserves=call(project,"3",3,3,false,5,1,0,false,true);
        check("energy_or_reserves".equals(reserves.get("displayed_cost_kind")),"mixed reserves not mislabeled energy");
        check(Boolean.TRUE.equals(reserves.get("cost_components_complete")),"audited native components covered");
        Map<?,?> finger=call(project,"2",2,2,false,5,9,0,true,true);
        check("reserves".equals(finger.get("displayed_cost_kind")),"reserve-only cost");
        check(((Map<?,?>)((List<?>)finger.get("cost_components")).get(0)).get("resource").equals("reserves"),"reserve-only component");
        Map<?,?> pyre=call(project,"0",1,1,true,0,3,1,false,true);
        check(((List<?>)pyre.get("cost_components")).size()==2,"free play still has Pyre sacrifice");
        Map<?,?> x=call(project,"X",-1,-1,true,4,2,0,false,true);
        check(Integer.valueOf(6).equals(x.get("x_resource_budget")),"free X still gets reserve budget");
        check(Boolean.FALSE.equals(call(project,"?",1,1,false,0,2,0,false,true).get("cost_components_complete")),"unknown text not invented");
        Map<?,?> hiddenX=call(project,"?",-1,-1,false,4,2,0,false,true);
        check("custom_or_unknown".equals(hiddenX.get("displayed_cost_kind")) && !hiddenX.containsKey("x_resource_budget"),"unknown rendered text must not reveal underlying X cost");
        check(Boolean.FALSE.equals(call(project,"2",2,2,false,0,2,0,false,false).get("cost_components_complete")),"unknown provider stays incomplete");
        check("custom_or_unknown".equals(call(project,"2",2,2,false,0,2,0,false,false).get("displayed_cost_kind")),"numeric alternate display is not called energy");
        System.out.println("PASS: energy/reserve-only/mixed/free/X/Pyre and unknown-provider cost semantics");
    }
    static Map<?,?> call(Method m,Object... args)throws Exception{return (Map<?,?>)m.invoke(null,args);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
