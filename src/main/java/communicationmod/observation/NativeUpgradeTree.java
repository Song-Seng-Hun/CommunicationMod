package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import java.util.*;

/** Optional StSLib tree observation. Only called while the native grid is waiting for a choice. */
public final class NativeUpgradeTree {
    private NativeUpgradeTree() { }
    public static Map<String,Object> capture(AbstractCard source) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("scope","native_multi_upgrade_tree");
        result.put("source","game_ui_preview");result.put("event_effects_predicted",false);
        result.put("choice_required",true);
        try {
            Class<?> tree=Class.forName("com.evacipated.cardcrawl.mod.stslib.ui.MultiUpgradeTree",false,NativeUpgradeTree.class.getClassLoader());
            Object main=tree.getField("mainCard").get(null);
            if(!(main instanceof AbstractCard))throw new IllegalStateException("missing_main_card");
            Map<String,Object> before=CardUpgradeObservation.snapshot(source),mainView=CardUpgradeObservation.snapshot((AbstractCard)main);
            for(String field:Arrays.asList("id","name","upgrades","misc","base_cost","base_damage","base_block","base_magic_number"))
                if(!Objects.equals(before.get(field),mainView.get(field)))throw new IllegalStateException("tree_source_mismatch");
            Object graph=tree.getField("cardGraph").get(null);
            List<?> vertices=list(field(graph,"vertices")),cards=list(tree.getField("cardList").get(null));
            List<?> taken=list(tree.getField("takenList").get(null)),locked=list(tree.getField("lockedList").get(null));
            List<Object> nodes=new ArrayList<>();
            for(Object vertex:vertices) {
                if(nodes.size()>=64)break;
                Object value=field(vertex,"card");
                if(!(value instanceof AbstractCard))throw new IllegalStateException("unsupported_tree_vertex");
                Map<String,Object> node=new LinkedHashMap<>();node.put("upgrade_index",field(vertex,"index"));
                node.put("is_base",value==main);node.put("taken",taken.contains(value));node.put("locked",locked.contains(value));
                node.put("ui_selectable",value!=main && cards.contains(value) && !taken.contains(value) && !locked.contains(value));
                node.put("strict_dependencies",field(vertex,"strict"));
                node.put("parents",indices(field(vertex,"parents")));node.put("exclusions",indices(field(vertex,"exclusions")));
                node.put("card",CardUpgradeObservation.snapshot((AbstractCard)value));nodes.add(node);
            }
            result.put("status","displayed");result.put("source_uuid",source.uuid==null?null:source.uuid.toString());
            result.put("before",before);result.put("nodes",nodes);result.put("node_count",vertices.size());
            result.put("complete",nodes.size()==vertices.size());
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            result.put("status","unavailable");result.put("reason","native_upgrade_tree_unavailable");
        }
        return result;
    }
    private static Object field(Object owner,String name)throws ReflectiveOperationException {
        if(owner==null)throw new IllegalStateException("missing_tree_field_owner");
        return owner.getClass().getField(name).get(owner);
    }
    private static List<?> list(Object value) {
        if(!(value instanceof List))throw new IllegalStateException("unsupported_tree_list");return (List<?>)value;
    }
    private static List<Integer> indices(Object vertices)throws ReflectiveOperationException {
        List<?> values=list(vertices);if(values.size()>64)throw new IllegalStateException("tree_edge_budget_exceeded");
        List<Integer> result=new ArrayList<>();
        for(Object vertex:values){Object index=field(vertex,"index");if(!(index instanceof Integer))throw new IllegalStateException("invalid_tree_index");result.add((Integer)index);}
        return result;
    }
}
