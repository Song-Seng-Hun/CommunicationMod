package communicationmod.observation;

import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import java.util.*;

/** Existing potion hover text only. Never recomputes potency, descriptions or outcomes. */
public final class PotionTooltips {
    private PotionTooltips() { }

    public static void addTo(Map<String,Object> out,AbstractPotion potion) {
        out.putAll(PublicTooltips.collect(true,Collections.emptyList(),key->null,
            Collections.singletonMap("potion_cache",()->cachedTips(potion))));
        out.put("tooltips_source","native_cached_potion_tips");
    }

    private static List<Map<String,String>> cachedTips(AbstractPotion potion) {
        List<Map<String,String>> tips=new ArrayList<>();
        if(potion.tips==null){tips.add(null);return tips;}
        // One overflow sentinel lets the shared 32-tip bound report omitted entries.
        for(int i=0;i<Math.min(potion.tips.size(),33);i++) {
            PowerTip tip=potion.tips.get(i);if(tip==null){tips.add(null);continue;}
            Map<String,String> row=new LinkedHashMap<>();
            row.put("title",tip.header);row.put("description",tip.body);tips.add(row);
        }
        return tips;
    }
}
