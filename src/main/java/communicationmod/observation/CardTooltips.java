package communicationmod.observation;

import basemod.abstracts.AbstractCardModifier;
import basemod.abstracts.CustomCard;
import basemod.helpers.CardModifierManager;
import basemod.helpers.TooltipInfo;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.TipHelper;
import java.util.*;
import java.util.function.Supplier;

/** Game-thread binding to the standard hover-tooltip sources, never to a global text dump. */
public final class CardTooltips {
    private CardTooltips() { }

    public static void addTo(Map<String, Object> target, AbstractCard card, boolean visible) {
        Map<String, Supplier<List<Map<String, String>>>> providers = new LinkedHashMap<>();
        // Keep all getters behind the visibility gate, including modifier accessors.
        if (visible) {
            if (card instanceof CustomCard) {
                CustomCard custom = (CustomCard)card;
                providers.put("custom_top", () -> copy(custom.getCustomTooltipsTop()));
                providers.put("custom", () -> copy(custom.getCustomTooltips()));
            }
            providers.put("card_modifier", () -> modifierTips(card));
        }
        Map<String, Object> result = PublicTooltips.collect(visible, visible ? card.keywords : null,
            CardTooltips::keyword, providers);
        // StSLib adds icon/damage/block/power tooltips during rendering, outside these
        // standard sources. Do not call its render hooks or claim these were observed.
        if (visible && Loader.isModLoaded("stslib")) {
            result.put("tooltips_complete", false);
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>)result.get("tooltips_unavailable_reasons");
            reasons.add("stslib_render_extensions_not_evaluated");
        }
        result.put("tooltips_source", "card_keywords_and_standard_hover_providers");
        result.put("tooltips_rendering", "plain_text_icons_preserved");
        target.putAll(result);
    }

    private static Map<String, String> keyword(String key) {
        // Exact same key/body and patched title function as TipHelper.renderKeywords.
        // No namespace stripping, fuzzy lookup, parent-key substitution or translation.
        if (!GameDictionary.keywords.containsKey(key)) return null;
        return text(TipHelper.capitalize(key), GameDictionary.keywords.get(key));
    }

    private static List<Map<String, String>> modifierTips(AbstractCard card) {
        List<Map<String, String>> result = new ArrayList<>();
        int inspected = 0;
        for (AbstractCardModifier modifier : CardModifierManager.modifiers(card)) {
            if (++inspected > 32) throw new IllegalStateException("Modifier provider budget exceeded");
            if (modifier == null) { result.add(null); continue; }
            List<Map<String, String>> extra = copy(modifier.additionalTooltips(card));
            if (extra != null) for (Map<String, String> tip : extra) {
                result.add(tip);
                if (result.size() >= 33) return result;
            }
        }
        return result;
    }

    private static List<Map<String, String>> copy(List<TooltipInfo> input) {
        if (input == null) return null;
        List<Map<String, String>> result = new ArrayList<>();
        for (TooltipInfo tip : input) {
            result.add(tip == null ? null : text(tip.title, tip.description));
            if (result.size() >= 33) break; // One extra lets the pure projector report truncation.
        }
        return result;
    }

    private static Map<String, String> text(String title, String body) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("description", body);
        return result;
    }
}
