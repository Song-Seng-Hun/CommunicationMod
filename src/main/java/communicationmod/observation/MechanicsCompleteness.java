package communicationmod.observation;

import java.util.*;

/** Completeness of already-projected public panels, never a read of native internals. */
public final class MechanicsCompleteness {
    private MechanicsCompleteness() { }
    public static void addTo(Map<String,Object> out) {
        boolean bindings=out.keySet().stream().noneMatch(k->k.endsWith("_unavailable_reason"));
        Object stance=out.get("champ_stance");
        if(stance instanceof Map && ((Map<?,?>)stance).containsKey("abilities_unavailable_reason"))bindings=false;
        List<String> issues=new ArrayList<>();
        int count=inspect(out,"",issues);
        out.put("panel_bindings_complete",bindings);
        out.put("information_complete",count==0);
        out.put("information_issues",issues);
        out.put("information_issue_count",count);
        out.put("audited_panels_complete",bindings && count==0);
        out.put("character_specific_complete",Boolean.TRUE.equals(out.get("character_supported")) && bindings && count==0);
    }
    private static int inspect(Object value,String path,List<String> issues) {
        int count=0;
        if(value instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
            String key=String.valueOf(entry.getKey()),child=path.isEmpty()?key:path+"."+key;Object item=entry.getValue();
            boolean incomplete=(key.endsWith("_complete") && Boolean.FALSE.equals(item))
                || (key.endsWith("_unavailable_reason") && item!=null)
                || ((key.equals("truncated") || key.endsWith("_truncated")) && Boolean.TRUE.equals(item))
                || (key.equals("status") && "unavailable".equals(item));
            if(incomplete){count++;if(issues.size()<64)issues.add(child);}
            count+=inspect(item,child,issues);
        }
        else if(value instanceof List){List<?> items=(List<?>)value;for(int i=0;i<items.size();i++)count+=inspect(items.get(i),path+"["+i+"]",issues);}
        return count;
    }
}
