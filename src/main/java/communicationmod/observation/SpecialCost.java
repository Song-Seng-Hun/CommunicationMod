package communicationmod.observation;

import java.util.*;

/** Pinned payment components, not a simulation of card effects or spending hooks. */
public final class SpecialCost {
    private SpecialCost() { }
    public static Map<String,Object> describe(String text,int baseCost,int cost,boolean freeOnce,int reserves,int energy,int pyre,boolean reserveOnly,boolean audited) {
        Map<String,Object> out=new LinkedHashMap<>();List<Object> components=new ArrayList<>();
        boolean numeric=text!=null && text.matches("[0-9]{1,8}");boolean x="X".equals(text);
        String resource=!audited?"unresolved":reserveOnly?"reserves":reserves>0?"energy_or_reserves":"energy";
        Map<String,Object> primary=new LinkedHashMap<>();primary.put("resource",resource);
        if(numeric)primary.put("displayed_amount",Integer.valueOf(text));
        if(x){primary.put("kind","x");out.put("x_resource_budget",Math.max(0,energy)+Math.max(0,reserves));
            out.put("x_resource_budget_complete",audited);
            primary.put("reserve_payment",freeOnce?"not_spent_free_to_play_once":"all_reserves");
            out.put("x_resource_budget_scope","energy_plus_reserves_before_card_effect_bonuses");}
        else primary.put("kind",numeric?"fixed":"unknown");
        if(!reserveOnly && reserves>0 && !x)primary.put("payment_policy","energy_first_reserves_cover_shortfall");
        primary.put("free_to_play_once",freeOnce);components.add(primary);
        if(pyre>0){Map<String,Object> extra=new LinkedHashMap<>();extra.put("resource","other_hand_card");extra.put("amount",pyre);
            extra.put("payment","pyre_sacrifice_exhaust");extra.put("requires_selection",true);extra.put("waived_by_free_energy",false);components.add(extra);}
        out.put("cost_components",components);out.put("cost_components_complete",audited && (numeric || "X".equals(text)));
        out.put("cost_components_scope","pinned_native_payment_components_not_card_effects");
        out.put("available_energy",energy);out.put("available_reserves",reserves);
        out.put("displayed_cost_kind",!audited?"custom_or_unknown":x?"x":numeric?resource:"custom_or_unknown");
        if(!audited)out.put("cost_components_unavailable_reason","unknown_cost_provider_or_native_binding");
        return out;
    }
}
