package communicationmod.observation;

import java.util.*;

/** One standard upgrade, never on the source. The adapter follows native UI preview semantics. */
public final class UpgradePreview<T> {
    public interface Adapter<T> {
        boolean visible(T card);
        String unavailableReason(T card);
        Map<String,Object> snapshot(T card);
        boolean canUpgrade(T card);
        T copy(T card);
        void upgrade(T copy);
    }
    private static final String[] COPY_FIELDS={"id","name","upgrades","misc","base_cost","base_damage","base_block","base_magic_number",
        "exhausts","ethereal","innate","self_retain","retain","target","description","displayed_values"};
    private final int limit;
    private final IdentityHashMap<T,Entry> cache=new IdentityHashMap<>();
    private final ArrayDeque<T> order=new ArrayDeque<>();
    private String context;
    private static final class Entry {
        final Map<String,Object> before,result;
        Entry(Map<String,Object> before,Map<String,Object> result){this.before=cloneMap(before);this.result=cloneMap(result);}
    }
    public UpgradePreview(int limit) {
        if(limit<1 || limit>1024)throw new IllegalArgumentException("Preview cache limit must be 1..1024");
        this.limit=limit;
    }
    public Map<String,Object> describe(T card,String decisionContext,Adapter<T> adapter) {
        if(!Objects.equals(context,decisionContext)){cache.clear();order.clear();context=decisionContext;}
        Map<String,Object> result=base();
        Map<String,Object> before=null;
        try {
            if(card==null || !adapter.visible(card))return unavailable("card_not_visible");
            String reason=adapter.unavailableReason(card);
            if(reason!=null)return unavailable(reason);
            before=adapter.snapshot(card);
            boolean canUpgrade=adapter.canUpgrade(card);
            result.put("can_upgrade",canUpgrade);result.put("from_upgrades",before.get("upgrades"));
            if(!canUpgrade){result.put("status","not_upgradable");return result;}
            Entry hit=cache.get(card);
            if(hit!=null && hit.before.equals(before))return cloneMap(hit.result);
            T copy=adapter.copy(card);
            if(copy==null || copy==card)result=unavailable("invalid_preview_copy");
            else {
                Map<String,Object> copied=adapter.snapshot(copy);
                boolean equivalent=true;
                for(String field:COPY_FIELDS)if(!Objects.equals(before.get(field),copied.get(field)))equivalent=false;
                if(!equivalent)result=unavailable("copy_does_not_preserve_current_card");
                else {
                    adapter.upgrade(copy);
                    Map<String,Object> after=adapter.snapshot(copy);
                    if(!before.equals(adapter.snapshot(card)))result=unavailable("preview_changed_source");
                    else {
                        List<String> changed=new ArrayList<>();Map<String,Object> numeric=new LinkedHashMap<>();
                        Set<String> fields=new TreeSet<>(before.keySet());fields.addAll(after.keySet());
                        for(String field:fields)if(!Objects.equals(before.get(field),after.get(field))) {
                            changed.add(field);Object from=before.get(field),to=after.get(field);
                            if(from instanceof Number && to instanceof Number) {
                                Map<String,Object> delta=new LinkedHashMap<>();delta.put("from",from);delta.put("to",to);
                                delta.put("delta",((Number)to).longValue()-((Number)from).longValue());numeric.put(field,delta);
                            }
                        }
                        if(changed.isEmpty())result=unavailable("upgrade_produced_no_observable_change");
                        else {
                            result.put("status","available");result.put("after",after);
                            result.put("can_upgrade_after",adapter.canUpgrade(copy));
                            result.put("changed_fields",changed);result.put("numeric_changes",numeric);
                        }
                    }
                }
            }
            remember(card,before,result);return cloneMap(result);
        } catch(RuntimeException | LinkageError failure) {
            // Never let a third-party card's preview failure disable the entire decision.
            result=unavailable("preview_failed:"+failure.getClass().getSimpleName());
            if(before!=null)remember(card,before,result);
            return cloneMap(result);
        }
    }
    private void remember(T card,Map<String,Object> before,Map<String,Object> result) {
        if(!cache.containsKey(card)) {
            if(cache.size()>=limit)cache.remove(order.removeFirst());
            order.addLast(card);
        }
        cache.put(card,new Entry(before,result));
    }
    public static Map<String,Object> unavailable(String reason) {
        Map<String,Object> result=base();result.put("status","unavailable");result.put("reason",reason);return result;
    }
    private static Map<String,Object> base() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("scope","next_standard_upgrade");
        result.put("steps",1);result.put("source","stat_equivalent_copy");
        result.put("event_effects_predicted",false);result.put("on_obtain_effects_predicted",false);return result;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> cloneMap(Map<String,Object> map){return (Map<String,Object>)cloneValue(map);}
    private static Object cloneValue(Object value) {
        if(value instanceof Map) {
            Map<String,Object> copy=new LinkedHashMap<>();
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet())copy.put((String)entry.getKey(),cloneValue(entry.getValue()));
            return copy;
        }
        if(value instanceof List) {
            List<Object> copy=new ArrayList<>();for(Object item:(List<?>)value)copy.add(cloneValue(item));return copy;
        }
        return value;
    }
}
