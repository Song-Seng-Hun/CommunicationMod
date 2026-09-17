import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/** Pure payload tests: no game objects, native code, or Steam initialization. */
public final class PublicTooltipsTest {
    private static Method collect;
    private static int checks;
    public static void main(String[] args) throws Exception {
        try {
            collect = Class.forName("communicationmod.observation.PublicTooltips").getMethod(
                "collect", boolean.class, List.class, Function.class, Map.class);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Player-visible keyword/tooltip projection is missing", missing);
        }
        List<String> keys = Arrays.asList("방어", "guardian:보석", "방어");
        List<String> original = new ArrayList<>(keys);
        Function<String, Map<String, String>> dictionary = key -> tip(key,
            key.equals("방어") ? "이번 턴에 받는 #y피해를 줄입니다." : "카드에 장착합니다.");
        Map<String, Supplier<List<Map<String, String>>>> providers = new LinkedHashMap<>();
        Map<String, String> custom = tip("추가 효과", "[E] NL 이번 전투 동안 적용됩니다.");
        providers.put("custom_top", () -> Collections.singletonList(custom));
        Map<?, ?> result = read(true, keys, dictionary, providers);
        List<?> tips = (List<?>)result.get("tooltips");
        equal(tips.size(), 4); // Preserve duplicates/order just as the UI list does.
        equal(((Map<?, ?>)tips.get(0)).get("description"), "이번 턴에 받는 피해를 줄입니다.");
        equal(((Map<?, ?>)tips.get(1)).get("keyword"), "guardian:보석");
        equal(((Map<?, ?>)tips.get(3)).get("description"), "[E]\n이번 전투 동안 적용됩니다.");
        equal(result.get("tooltips_complete"), true);
        equal(keys, original);
        equal(custom.get("description"), "[E] NL 이번 전투 동안 적용됩니다.");

        int[] calls = {0};
        providers.put("secret", () -> { calls[0]++; throw new AssertionError("hidden getter called"); });
        result = read(false, keys, key -> { calls[0]++; return tip("secret", "secret"); }, providers);
        equal(result.get("tooltips"), Collections.emptyList());
        equal(result.get("tooltips_complete"), false);
        equal(calls[0], 0);
        providers.remove("secret");
        result = read(true, Arrays.asList(null, "missing", "bad", "방어"), key -> {
            if (key.equals("missing")) return null;
            if (key.equals("bad")) throw new IllegalArgumentException("private diagnostic");
            return dictionary.apply(key);
        }, providers);
        equal(result.get("tooltips_complete"), false);
        equal(((List<?>)result.get("tooltips")).size(), 2);
        equal(result.get("unresolved_keywords"), Arrays.asList("missing", "bad"));
        equal(result.toString().contains("private diagnostic"), false);

        providers.clear();
        providers.put("broken", () -> { throw new IllegalStateException("private state"); });
        providers.put("valid", () -> Arrays.asList(null, tip("", "Headerless text"), tip("Missing", null)));
        providers.put("none", () -> null); // Standard BaseMod getter returns null for no extra tips.
        result = read(true, Collections.emptyList(), dictionary, providers);
        equal(result.get("tooltips_complete"), false);
        tips = (List<?>)result.get("tooltips");
        equal(tips.size(), 2);
        equal(((Map<?, ?>)tips.get(0)).get("title"), "");
        equal(((Map<?, ?>)tips.get(1)).get("description_complete"), false);
        equal(result.toString().contains("private state"), false);
        providers.clear();
        providers.put("missing_dependency", () -> { throw new NoClassDefFoundError("private path"); });
        providers.put("valid", () -> Collections.singletonList(tip("Valid", "Still available")));
        result = read(true, Arrays.asList("unlinked"), key -> { throw new NoSuchMethodError("old mod"); }, providers);
        equal(result.get("tooltips_complete"), false);
        equal(((List<?>)result.get("tooltips")).size(), 1);
        equal(result.toString().contains("private path"), false);
        result = read(true, null, dictionary, Collections.emptyMap());
        equal(result.get("tooltips_complete"), false);
        result = read(true, Collections.emptyList(), dictionary, Collections.emptyMap());
        equal(result.get("tooltips_complete"), true);

        calls[0] = 0;
        result = read(true, Collections.nCopies(1000, "방어"), key -> {
            calls[0]++; return dictionary.apply(key);
        }, Collections.emptyMap());
        equal(((List<?>)result.get("tooltips")).size() <= 32, true);
        equal(calls[0] <= 32, true);
        equal(result.get("tooltips_truncated"), true);
        equal(result.get("tooltips_complete"), false);
        result = read(true, Arrays.asList("large"), key -> tip("x", String.join("", Collections.nCopies(10000,"가"))),
            Collections.emptyMap());
        equal(result.get("tooltips_truncated"), true);
        equal(result.get("tooltips_complete"), false);

        // Dictionary is read anew each snapshot: no stale language cache.
        equal(firstDescription(read(true, Arrays.asList("block"), key -> tip("Block", "Prevent damage."),
            Collections.emptyMap())), "Prevent damage.");
        equal(firstDescription(read(true, Arrays.asList("block"), key -> tip("방어", "피해를 막습니다."),
            Collections.emptyMap())), "피해를 막습니다.");
        System.out.println("PASS: " + checks + " public-tooltip assertions (JDK-only)");
    }
    private static Map<?, ?> read(boolean visible, List<String> keys,
            Function<String, Map<String, String>> dictionary,
            Map<String, Supplier<List<Map<String, String>>>> providers) throws Exception {
        return (Map<?, ?>)collect.invoke(null, visible, keys, dictionary, providers);
    }
    private static String firstDescription(Map<?, ?> result) {
        return (String)((Map<?, ?>)((List<?>)result.get("tooltips")).get(0)).get("description");
    }
    private static Map<String, String> tip(String title, String body) {
        Map<String, String> tip = new LinkedHashMap<>();
        tip.put("title", title); tip.put("description", body); return tip;
    }
    private static void equal(Object actual, Object expected) {
        checks++;
        if (!Objects.equals(actual, expected)) throw new AssertionError("expected <"+expected+"> but was <"+actual+">");
    }
}
