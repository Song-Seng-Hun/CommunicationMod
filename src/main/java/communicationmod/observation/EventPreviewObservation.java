package communicationmod.observation;

import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import java.lang.reflect.Field;
import java.util.*;

/** Existing hover previews only. Never constructs relics, invokes renderers or predicts rewards. */
public final class EventPreviewObservation {
    private EventPreviewObservation() { }
    public static void addRelicTo(Map<String,Object> option,LargeDialogOptionButton button) {
        if(button==null)return;
        String type=button.getClass().getName();
        boolean guardian="guardian.ui.RelicPreviewButton".equals(type);
        if(!guardian && button.getClass()!=LargeDialogOptionButton.class)return;
        try {
            Field field=(guardian?button.getClass():LargeDialogOptionButton.class).getDeclaredField(guardian?"r":"relicToPreview");
            field.setAccessible(true);Object value=field.get(button);if(value==null)return;
            AbstractRelic relic=(AbstractRelic)value;
            Map<String,Object> preview=new LinkedHashMap<>();preview.put("id",relic.relicId);preview.put("name",relic.name);
            preview.putAll(PublicTooltips.collect(true,Collections.emptyList(),key->null,
                Collections.singletonMap("event_relic",()->cachedTips(relic))));
            option.put("relic_preview",preview);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            option.put("relic_preview_unavailable_reason","native_binding_unavailable");
        }
    }
    private static List<Map<String,String>> cachedTips(AbstractRelic relic) {
        List<Map<String,String>> tips=new ArrayList<>();
        if(relic.tips==null){tips.add(null);return tips;}
        // One overflow sentinel lets PublicTooltips mark its existing 32-tip bound incomplete.
        for(int i=0;i<Math.min(relic.tips.size(),33);i++) {
            PowerTip tip=relic.tips.get(i);if(tip==null){tips.add(null);continue;}
            Map<String,String> row=new LinkedHashMap<>();row.put("title",tip.header);row.put("description",tip.body);tips.add(row);
        }
        return tips;
    }
}
