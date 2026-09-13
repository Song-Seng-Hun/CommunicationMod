import java.util.*;
import java.lang.reflect.*;

public final class MechanicsCompletenessTest {
    static Method apply;
    public static void main(String[] args)throws Exception {
        try {apply=Class.forName("communicationmod.observation.MechanicsCompleteness").getMethod("addTo",Map.class);}
        catch(ClassNotFoundException missing){throw new AssertionError("Missing recursive mechanics information completeness");}
        Map<String,Object> out=base();apply.invoke(null,out);
        check(Boolean.TRUE.equals(out.get("character_specific_complete")),"complete supported public panels");
        out=base();Map<String,Object> card=new LinkedHashMap<>();card.put("displayed_cost_complete",false);
        out.put("collection",Collections.singletonMap("cards",Arrays.asList(card)));apply.invoke(null,out);
        check(Boolean.TRUE.equals(out.get("panel_bindings_complete")),"binding success independent of card data");
        check(Boolean.FALSE.equals(out.get("information_complete")) && Boolean.FALSE.equals(out.get("character_specific_complete")),"nested incompleteness propagated");
        check(((List<?>)out.get("information_issues")).contains("collection.cards[0].displayed_cost_complete"),"exact nested issue path");
        for(String key:Arrays.asList("cards_complete","description_complete","spells_complete","encode_sequence_complete")) {
            out=base();out.put(key,false);apply.invoke(null,out);check(Boolean.FALSE.equals(out.get("information_complete")),key);
        }
        out=base();out.put("champ_stance",Collections.singletonMap("abilities_unavailable_reason","native_binding_unavailable"));apply.invoke(null,out);
        check(Boolean.FALSE.equals(out.get("panel_bindings_complete")),"nested native binding failure");
        out=base();out.put("card",Collections.singletonMap("upgrade_preview",Collections.singletonMap("status","unavailable")));apply.invoke(null,out);
        check(Boolean.FALSE.equals(out.get("information_complete")),"unavailable status propagated");
        out=base();out.put("description_truncated",true);apply.invoke(null,out);check(Boolean.FALSE.equals(out.get("information_complete")),"truncation propagated");
        out=base();out.put("character_supported",false);apply.invoke(null,out);check(Boolean.FALSE.equals(out.get("character_specific_complete")),"unsupported not blanket complete");
        out=base();for(int i=0;i<100;i++)out.put("field"+i+"_complete",false);apply.invoke(null,out);
        check(((List<?>)out.get("information_issues")).size()==64 && out.get("information_issue_count").equals(100),"issue paths bounded without hiding issue count");
        System.out.println("PASS: mechanics binding/data completeness, nested paths, missing and bounded diagnostics");
    }
    static Map<String,Object> base(){Map<String,Object> out=new LinkedHashMap<>();out.put("character_supported",true);return out;}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
