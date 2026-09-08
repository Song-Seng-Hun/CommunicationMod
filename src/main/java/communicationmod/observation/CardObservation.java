package communicationmod.observation;

import basemod.BaseMod;
import basemod.abstracts.DynamicVariable;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DescriptionLine;
import com.megacrit.cardcrawl.core.Settings;
import java.util.ArrayList;
import java.util.Map;

/**
 * Binding for the installed prepatched Downfall/BaseMod renderer, inspected with javap.
 * RenderCustomDynamicVariable.Inner and its CN counterpart select value when modified,
 * otherwise modifiedBaseValue (which includes BaseMod's base-stat modifier policy).
 * initializeDescription already applies CardModifierOnCreateDescription to the UI cache.
 * Reading that cache avoids replaying description hooks or recomputing game statistics.
 * Extension value getters have the same read-only contract expected by the renderer.
 * Custom render hooks, dynamic-text refresh, alternate costs and upgrade-popup previews
 * are outside this bounded observation. No rendering/initialization methods are invoked.
 */
public final class CardObservation {
    private CardObservation() { }

    public static void addTo(Map<String, Object> target, AbstractCard card) {
        boolean visible = card.isSeen && !card.isLocked && !card.isFlipped;
        ArrayList<String> lines = new ArrayList<>();
        int length = 0;
        if (visible && card.description != null) {
            for (DescriptionLine line : card.description) {
                String text = line == null ? null : line.text;
                lines.add(text);
                length += text == null ? 0 : Math.min(text.length(), 8193);
                if (length > 8192 || lines.size() >= 256) break;
            }
        }
        Map<String, Object> observation = PublicDescription.formatLines(lines, visible,
            Settings.lineBreakViaCharacter,
            key -> displayedValue(card, key));
        if (card.description != null && card.description.size() > lines.size() && visible) {
            observation.put("description_complete", false);
            observation.put("description_truncated", true);
        }
        observation.put("description_source", "cached_ui_lines");
        observation.put("description_rendering", "plain_text_snapshot_icons_preserved");
        // getCost/freeToPlay contain Downfall and StSLib hooks. Do not label the old
        // costForTurn field as the effective displayed cost or invoke unaudited hooks.
        observation.put("displayed_cost_complete", false);
        observation.put("displayed_cost_unavailable_reason", "patched_cost_rendering_not_evaluated");
        target.putAll(observation);
    }

    private static Integer displayedValue(AbstractCard card, String key) {
        DynamicVariable variable = BaseMod.cardDynamicVariableMap.get(key);
        if (variable == null) return null;
        return variable.isModified(card) ? variable.value(card) : variable.modifiedBaseValue(card);
    }
}
