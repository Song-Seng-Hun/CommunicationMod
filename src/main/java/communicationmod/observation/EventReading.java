package communicationmod.observation;

import java.util.*;

/** Game-thread-confined conversation gate. Accepts rendered text, never a future event script. */
public final class EventReading {
    private Object page;
    private String readingId = UUID.randomUUID().toString();
    private String body = "", language = "unknown", commentary;
    private List<Option> options = Collections.emptyList();
    private boolean complete, visible, supported, afterChoice;
    private Integer selected;
    private Map<String,Object> lastDiscussion;
    private String unavailableReason = "no_rendered_page";

    public void reset() {
        page = null; body = ""; language = "unknown"; options = Collections.emptyList();
        complete = visible = supported = afterChoice = false;
        selected = null; lastDiscussion = null; unavailableReason = "no_rendered_page"; invalidate();
    }

    /** Called immediately at body-generation change, before the new body renders. */
    public void newPage(Object token) {
        if (page == token) return;
        afterChoice = selected != null;
        page = token; body = ""; options = Collections.emptyList();
        language = "unknown"; complete = visible = supported = false;
        selected = null; unavailableReason = "waiting_for_render"; invalidate();
    }

    public void observe(Object token, String text, String textLanguage, List<Option> shown,
                        boolean fullyRendered, boolean displayed) {
        newPage(token);
        boolean withinBudget = text != null && text.length() <= 8192 && shown != null && shown.size() <= 64;
        int size = 0;
        if (withinBudget) for (Option option : shown) {
            if (option == null || option.text == null || option.text.length() > 8192) { withinBudget = false; break; }
            size += option.text.length();
            if (size > 131072) { withinBudget = false; break; }
        }
        if (!withinBudget) {
            unavailable("observation_budget_exceeded"); return;
        }
        String lang = textLanguage == null ? "unknown" : textLanguage;
        boolean changed = !body.equals(text) || !language.equals(lang) || !options.equals(shown);
        if (changed || complete != fullyRendered || visible != displayed || !supported) {
            if (changed && selected != null) { afterChoice = true; selected = null; }
            invalidate();
        }
        body = text; language = lang; options = new ArrayList<>(shown);
        supported = token != null; complete = fullyRendered && !text.trim().isEmpty(); visible = displayed;
        unavailableReason = supported ? null : "no_rendered_page";
    }

    public void unavailable(String reason) {
        if (supported || !Objects.equals(unavailableReason, reason)) invalidate();
        body = ""; options = Collections.emptyList(); supported = complete = visible = false;
        unavailableReason = reason;
    }

    public void suspend() {
        if (visible) invalidate();
        visible = false;
    }

    public String phase() {
        if (!supported) return "unavailable";
        if (!visible) return "hidden";
        if (selected != null) return "awaiting_result";
        if (!complete) return "revealing";
        return commentary == null ? "discussion_required" : "ready_to_choose";
    }

    public boolean canChoose() { return "ready_to_choose".equals(phase()) && option(0) != null; }
    public String readingId() { return readingId; }

    public void validateAcknowledgement(String id, String spokenCommentary) {
        if (!"discussion_required".equals(phase()) || !readingId.equals(id))
            throw new IllegalArgumentException("Event reading is incomplete, hidden, changed or already acknowledged");
        if (spokenCommentary == null || spokenCommentary.trim().isEmpty() || spokenCommentary.length() > 4096)
            throw new IllegalArgumentException("Nonempty commentary of at most 4096 characters required");
    }

    /** Client confirms it presented this commentary; this cannot prove comprehension or delivery. */
    public void acknowledge(String id, String spokenCommentary) {
        validateAcknowledgement(id, spokenCommentary);
        commentary = spokenCommentary;
        lastDiscussion = new LinkedHashMap<>();
        lastDiscussion.put("reading_id", readingId); lastDiscussion.put("body_text", body);
        lastDiscussion.put("text_language", language); lastDiscussion.put("options", optionMaps());
        lastDiscussion.put("commentary", commentary); lastDiscussion.put("page_role", afterChoice ? "after_choice" : "before_choice");
    }

    public void validateChoice(int index) {
        if (!canChoose() || option(index) == null) throw new IllegalArgumentException("Read and discuss the current page before choosing an enabled option");
    }

    /** Consume BEFORE UI mutation. A failed dispatch must remain blocked until a new page is observed. */
    public void choose(int index) {
        validateChoice(index);
        selected = index;
        lastDiscussion.put("selected_choice_index", index);
        lastDiscussion.put("selected_option", option(index).text);
    }

    private Option option(int index) {
        int enabled = 0;
        for (Option option : options) if (!option.disabled && enabled++ == index) return option;
        return null;
    }

    public Map<String,Object> snapshot() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("reading_id", readingId); result.put("phase", phase());
        result.put("unavailable_reason", unavailableReason);
        result.put("page_role", afterChoice ? "after_choice" : "before_choice");
        result.put("body_text", visible ? body : ""); result.put("text_language", language);
        result.put("text_complete", visible && supported && complete);
        result.put("can_choose", canChoose()); result.put("options", visible ? optionMaps() : new ArrayList<>());
        result.put("commentary", commentary); result.put("last_discussion", detach(lastDiscussion));
        return result;
    }

    private List<Map<String,Object>> optionMaps() {
        List<Map<String,Object>> result = new ArrayList<>(); int index = 0;
        for (Option option : options) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("text", option.text); row.put("disabled", option.disabled);
            if (!option.disabled) row.put("choice_index", index++);
            result.add(row);
        }
        return result;
    }

    private static Object detach(Object value) {
        if (value instanceof Map) {
            Map<Object,Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet()) copy.put(entry.getKey(), detach(entry.getValue()));
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>)value) copy.add(detach(item));
            return copy;
        }
        return value;
    }
    private void invalidate() { readingId = UUID.randomUUID().toString(); commentary = null; }

    public static final class Option {
        public final String text;
        public final boolean disabled;
        public Option(String text, boolean disabled) { this.text = text; this.disabled = disabled; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Option)) return false;
            Option option = (Option)other;
            return disabled == option.disabled && Objects.equals(text, option.text);
        }
        @Override public int hashCode() { return Objects.hash(text, disabled); }
    }
}
