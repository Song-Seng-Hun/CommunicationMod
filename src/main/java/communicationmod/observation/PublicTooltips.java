package communicationmod.observation;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Bounded, detached projection of UI tooltip text. Never scans a whole dictionary. */
public final class PublicTooltips {
    private static final int MAX_TIPS = 32;
    private static final int MAX_TEXT = 32768;
    private final List<Map<String, Object>> tips = new ArrayList<>();
    private final Set<String> reasons = new LinkedHashSet<>();
    private final Set<String> unresolvedKeywords = new LinkedHashSet<>();
    private int entries;
    private int remaining = MAX_TEXT;
    private boolean truncated;

    private PublicTooltips() { }

    public static Map<String, Object> collect(boolean visible, List<String> keywords,
            Function<String, Map<String, String>> dictionary,
            Map<String, Supplier<List<Map<String, String>>>> providers) {
        PublicTooltips result = new PublicTooltips();
        if (!visible) {
            result.reasons.add("card_text_not_visible");
            return result.snapshot();
        }
        if (keywords == null) result.reasons.add("keyword_list_unavailable");
        else for (String key : keywords) {
            if (!result.reserve()) break;
            if (key == null || key.isEmpty() || key.length() > 1024) {
                result.reasons.add("invalid_keyword"); continue;
            }
            try {
                Map<String, String> tip = dictionary.apply(key);
                if (tip == null) result.unresolvedKeywords.add(key);
                result.add("keyword", key, tip);
            } catch (RuntimeException | LinkageError unavailable) {
                result.unresolvedKeywords.add(key);
                result.reasons.add("keyword_lookup_failed");
            }
        }
        for (Map.Entry<String, Supplier<List<Map<String, String>>>> provider : providers.entrySet()) {
            // Do not call more extension getters after the observation budget is exhausted.
            if (result.entries >= MAX_TIPS || result.remaining <= 0) {
                result.truncated = true; break;
            }
            try {
                List<Map<String, String>> extra = provider.getValue().get();
                if (extra == null) continue;
                for (Map<String, String> tip : extra) {
                    if (!result.reserve()) break;
                    result.add(provider.getKey(), null, tip);
                }
            } catch (RuntimeException | LinkageError unavailable) {
                result.reasons.add("tooltip_provider_failed:" + provider.getKey());
            }
        }
        return result.snapshot();
    }

    private boolean reserve() {
        if (entries >= MAX_TIPS || remaining <= 0) { truncated = true; return false; }
        entries++;
        return true;
    }

    private void add(String source, String key, Map<String, String> input) {
        if (input == null) { reasons.add("tooltip_unavailable:" + source); return; }
        Map<String, Object> title = PublicDescription.format(input.get("title"), ignored -> null);
        Map<String, Object> body = PublicDescription.format(input.get("description"), ignored -> null);
        Map<String, Object> tip = new LinkedHashMap<>(body);
        tip.put("source", source);
        if (key != null) tip.put("keyword", key);
        String titleText = (String)title.get("description");
        String bodyText = (String)body.get("description");
        int titleLimit = Math.min(1024, remaining);
        boolean shortened = titleText.length() > titleLimit;
        titleText = titleText.substring(0, Math.min(titleText.length(), titleLimit));
        remaining -= titleText.length();
        shortened |= bodyText.length() > remaining;
        bodyText = bodyText.substring(0, Math.min(bodyText.length(), remaining));
        remaining -= bodyText.length();
        tip.put("title", titleText);
        tip.put("description", bodyText);
        boolean cut = shortened || Boolean.TRUE.equals(title.get("description_truncated"))
            || Boolean.TRUE.equals(body.get("description_truncated"));
        boolean complete = !cut && Boolean.TRUE.equals(title.get("description_complete"))
            && Boolean.TRUE.equals(body.get("description_complete"));
        tip.put("description_complete", complete);
        tip.put("description_truncated", cut);
        if (!complete) reasons.add("tooltip_text_incomplete");
        truncated |= cut;
        tips.add(tip);
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tooltips", tips);
        result.put("tooltips_complete", reasons.isEmpty() && !truncated);
        result.put("tooltips_truncated", truncated);
        result.put("tooltips_unavailable_reasons", new ArrayList<>(reasons));
        result.put("unresolved_keywords", new ArrayList<>(unresolvedKeywords));
        return result;
    }
}
