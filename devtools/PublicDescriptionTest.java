import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/** JDK-only tests: no game classes, fixtures, native libraries or initialization. */
public final class PublicDescriptionTest {
    private static Method format;
    private static int checks;

    public static void main(String[] args) throws Exception {
        try {
            format = Class.forName("communicationmod.observation.PublicDescription")
                .getMethod("format", String.class, Function.class);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Public description formatter is missing", missing);
        }
        Map<String, Integer> values = new HashMap<>();
        values.put("D", 12); values.put("B", 0); values.put("M", -2);
        values.put("GuardianSecondM", 7);
        Map<?, ?> result = render("피해 !D!, 방어 !B! NL 마법 !M! / !GuardianSecondM!", values::get);
        equal(result.get("description"), "피해 12, 방어 0\n마법 -2 / 7");
        equal(result.get("description_complete"), true);
        equal(result.get("displayed_values"), values);
        result = render("!missing! !missing! !D!", values::get);
        equal(result.get("description"), "!missing! !missing! 12");
        equal(result.get("description_complete"), false);
        equal(result.get("unresolved_variables"), Arrays.asList("missing"));
        equal(((Map<?, ?>)result.get("displayed_values")).containsKey("missing"), false);
        result = render("!broken!", key -> { throw new IllegalStateException("unavailable"); });
        equal(result.get("description"), "!broken!");
        equal(result.get("description_complete"), false);
        equal(render("#rRed #gGreen #bBlue #yGold NL ONLY NLword [E]", values::get).get("description"),
            "Red Green Blue Gold\nONLY NLword [E]");
        equal(render("", values::get).get("description_complete"), true);
        equal(render(null, values::get).get("description_complete"), false);
        equal(render(null, values::get).get("description"), "");
        int[] calls = {0};
        result = render("!D! !D!", key -> { calls[0]++; return 5; });
        equal(calls[0], 1);
        equal(result.get("description"), "5 5");
        result = render(String.join("", Collections.nCopies(9000, "a")), values::get);
        equal(result.get("description_complete"), false);
        equal(result.get("description_truncated"), true);
        equal(((String) result.get("description")).length() <= 8192, true);
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 140; i++) many.append("!v").append(i).append("! ");
        calls[0] = 0;
        result = render(many.toString(), key -> { calls[0]++; return 1; });
        equal(calls[0] <= 128, true);
        equal(result.get("description_complete"), false);
        Method lines;
        try {
            lines = format.getDeclaringClass().getMethod("formatLines", List.class, boolean.class, Function.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Cached visible description projection is missing", missing);
        }
        Function<String, Integer> resolver = values::get;
        result = (Map<?, ?>) lines.invoke(null, Arrays.asList("Modified text !D!", "추가 효과"), true, resolver);
        equal(result.get("description"), "Modified text 12\n추가 효과");
        result = (Map<?, ?>) lines.invoke(null, Arrays.asList("secret !D!"), false, resolver);
        equal(result.get("description"), "");
        equal(result.get("description_complete"), false);
        equal(((Map<?, ?>)result.get("displayed_values")).isEmpty(), true);
        result = (Map<?, ?>) lines.invoke(null, Collections.emptyList(), true, resolver);
        equal(result.get("description_complete"), false);
        result = (Map<?, ?>) lines.invoke(null, Arrays.asList("known", null), true, resolver);
        equal(result.get("description_complete"), false);
        result = render("造成$D$$伤害，$GuardianSecondM$$。$unknown$$", values::get);
        equal(result.get("description"), "造成12伤害，7。$unknown$$");
        equal(result.get("description_complete"), false);
        equal(result.get("unresolved_variables"), Arrays.asList("unknown"));
        // Actual initializeDescriptionCN cache encodings, transcribed from javap:
        // !D! -> " D" (1237/1249); !B!/!M! -> space + token + "!".
        // Never create AbstractCard/DescriptionLine fixtures: those can initialize game code.
        Method languageLines = null;
        try {
            languageLines = format.getDeclaringClass().getMethod("formatLines", List.class,
                boolean.class, boolean.class, Function.class);
        } catch (NoSuchMethodException missing) { /* Exercise old behavior for RED. */ }
        for (String cached : Arrays.asList("造成 D点伤害。", "获得 !B!!点格挡。",
                "抽 !M!!张牌。", " D", "字母D与 D文字", " !B!! !M!! $GuardianSecondM$$")) {
            final int[] resolved = {0};
            Function<String, Integer> guarded = key -> { resolved[0]++; return values.get(key); };
            result = (Map<?, ?>) (languageLines == null
                ? lines.invoke(null, Arrays.asList(cached), true, guarded)
                : languageLines.invoke(null, Arrays.asList(cached), true, true, guarded));
            equal(result.get("description_complete"), false);
            equal(result.get("description"), "");
            equal(result.get("description_unavailable_reason"), "cn_cached_encoding_unsupported");
            equal(((Map<?, ?>)result.get("displayed_values")).isEmpty(), true);
            equal(resolved[0], 0);
        }
        if (languageLines == null) throw new AssertionError("Language-aware cache binding missing");
        result = (Map<?, ?>)languageLines.invoke(null, Arrays.asList("D !B!!"), true, false, resolver);
        equal(result.get("description"), "D 0!"); // Ordinary punctuation and letter D unchanged.
        if (args.length > 0 && args[0].equals("--draw")) checkEnhancedDraw();
        System.out.println("PASS: " + checks + " public-description assertions (JDK-only)");
    }

    private static Map<?, ?> render(String text, Function<String, Integer> values) throws Exception {
        return (Map<?, ?>) format.invoke(null, text, values);
    }

    private static void equal(Object actual, Object expected) {
        checks++;
        if (!Objects.equals(actual, expected))
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }

    private static void checkEnhancedDraw() throws Exception {
        Method order = Class.forName("communicationmod.DrawPileVisibility")
            .getMethod("orderForPlayer", List.class, boolean.class);
        List<HashMap<String, Object>> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            HashMap<String, Object> card = new HashMap<>();
            card.put("id", "same"); card.put("uuid", "same");
            final int value = i;
            for (Map.Entry<?, ?> entry : render("!D! !unknown!", key -> key.equals("D") ? value : null).entrySet())
                card.put((String)entry.getKey(), entry.getValue());
            cards.add(card);
        }
        Object canonical = order.invoke(null, cards, false);
        permute(cards, 0, order, canonical);
    }

    private static void permute(List<HashMap<String, Object>> cards, int at, Method order,
                                Object canonical) throws Exception {
        if (at == cards.size()) {
            String before = cards.toString();
            equal(order.invoke(null, cards, false), canonical);
            equal(order.invoke(null, cards, true), cards);
            equal(cards.toString(), before);
            return;
        }
        for (int i = at; i < cards.size(); i++) {
            Collections.swap(cards, at, i);
            permute(cards, at + 1, order, canonical);
            Collections.swap(cards, at, i);
        }
    }
}
