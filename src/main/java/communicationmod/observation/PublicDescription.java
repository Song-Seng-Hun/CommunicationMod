package communicationmod.observation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain text projection only. Icons remain symbolic; no font/layout or game dependencies. */
public final class PublicDescription {
    private static final int MAX_TEXT = 8192;
    private static final int MAX_VARIABLES = 128;
    // Raw/custom-variable syntax only; CN cached base tokens need separate handling.
    private static final Pattern VARIABLE = Pattern.compile("!([^!\\s]+)!|\\$([^$\\s]+)\\$\\$");

    private PublicDescription() { }

    /**
     * CN caches lose the delimiters around damage and add delimiters to B/M.
     * Until the full cached tokenizer is supported, do not mislabel these as decoded
     * display text or guess whether an arbitrary letter D represents damage.
     */
    public static Map<String, Object> formatLines(List<String> lines, boolean visible,
            boolean lineBreakViaCharacter, Function<String, Integer> resolver) {
        if (!lineBreakViaCharacter) return formatLines(lines, visible, resolver);
        Map<String, Object> result = format(null, resolver);
        result.put("description_unavailable_reason", "cn_cached_encoding_unsupported");
        return result;
    }

    /** Read existing UI lines, retaining line boundaries in all languages. Never rebuild them. */
    public static Map<String, Object> formatLines(List<String> lines, boolean visible,
                                                 Function<String, Integer> resolver) {
        if (!visible || lines == null || lines.isEmpty()) return format(null, resolver);
        StringBuilder text = new StringBuilder();
        boolean missing = false;
        for (String line : lines) {
            if (line == null) { missing = true; continue; }
            if (text.length() > 0) text.append('\n');
            text.append(line, 0, Math.min(line.length(), MAX_TEXT + 1));
            if (text.length() > MAX_TEXT) break;
        }
        Map<String, Object> result = format(text.toString(), resolver);
        if (missing) result.put("description_complete", false);
        return result;
    }

    /** Completeness concerns available text and variable resolution, not pixel-perfect rendering. */
    public static Map<String, Object> format(String source, Function<String, Integer> resolver) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> values = new LinkedHashMap<>();
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        boolean truncated = source != null && source.length() > MAX_TEXT;
        String text = source == null ? "" : source.substring(0, Math.min(source.length(), MAX_TEXT));
        Matcher matcher = VARIABLE.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (!values.containsKey(key) && !unresolved.contains(key)) {
                Integer value = null;
                if (values.size() + unresolved.size() < MAX_VARIABLES) {
                    try { value = resolver.apply(key); }
                    catch (RuntimeException unavailable) { /* Preserve the token, never invent zero. */ }
                }
                if (value == null) unresolved.add(key);
                else values.put(key, value);
            }
            String replacement = values.containsKey(key) ? values.get(key).toString() : matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        text = output.toString().replaceAll("#[rgbypw]", "")
            .replaceAll("(?<!\\S)NL(?!\\S)", "\n")
            .replaceAll("[ \\t]*\\n[ \\t]*", "\n").trim();
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
            truncated = true;
        }
        result.put("description", text);
        result.put("description_complete", source != null && !truncated && unresolved.isEmpty());
        result.put("description_truncated", truncated);
        result.put("unresolved_variables", new ArrayList<>(unresolved));
        result.put("displayed_values", values);
        return result;
    }
}
