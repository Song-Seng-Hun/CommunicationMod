package communicationmod.observation;

import java.util.*;

/** Current completed-frame UI values only. No cost evaluation or game dependency. */
public final class DisplayedCost {
    private final Map<Object,Entry> values=new WeakHashMap<>();
    private long frame,completed=-1;
    public void beginFrame(){frame++;completed=-1;}
    public void completeFrame(){completed=frame;}
    public void record(Object card,String fingerprint,String text){
        if(card==null || text==null || text.length()>64)return;
        if(values.size()>2048)values.clear();values.put(card,new Entry(frame,fingerprint,text));
    }
    public Map<String,Object> read(Object card,String fingerprint) {
        Map<String,Object> out=new LinkedHashMap<>();Entry value=values.get(card);
        boolean available=value!=null && value.frame==completed && completed==frame && Objects.equals(value.fingerprint,fingerprint);
        out.put("displayed_cost_complete",available);
        if(!available){out.put("displayed_cost_unavailable_reason","not_rendered_in_current_completed_frame_or_context_changed");return out;}
        out.put("displayed_cost_text",value.text);out.put("displayed_cost_source","native_rendered_cost_text");
        out.put("cost_components_complete",false); // Extra HP/resource/icon costs may have their own renderers.
        if(value.text.matches("[0-9]{1,8}")){out.put("displayed_cost",Integer.valueOf(value.text));out.put("displayed_cost_kind","energy");}
        else out.put("displayed_cost_kind",value.text.equals("X")?"x":"custom_or_unknown");
        return out;
    }
    private static final class Entry {
        final long frame;final String fingerprint,text;
        Entry(long frame,String fingerprint,String text){this.frame=frame;this.fingerprint=fingerprint;this.text=text;}
    }
}
