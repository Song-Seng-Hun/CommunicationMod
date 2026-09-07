import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/** Headless regression checks; run with the game and BaseMod jars on the classpath. */
public final class KeywordGuardTest {
    public static void main(String[] args) throws Exception {
        ArrayList<String> input = new ArrayList<>(Arrays.asList("block", null, "mod:keyword", null));
        Method sanitize = null;
        try {
            sanitize = Class.forName("communicationmod.patches.KeywordNullGuard")
                .getMethod("sanitize", ArrayList.class,
                    Class.forName("com.megacrit.cardcrawl.cards.AbstractCard", false,
                        KeywordGuardTest.class.getClassLoader()));
        } catch (ClassNotFoundException absentBeforeFix) {
            // Exercise the unguarded lookup that previously crashed in FakeKeywords.
        }
        ArrayList<?> result = sanitize == null ? input : (ArrayList<?>) sanitize.invoke(null, input, null);
        TreeMap<String, String> dictionary = new TreeMap<>();
        dictionary.put("block", "Block damage");
        try {
            for (Object keyword : result) dictionary.containsKey(keyword);
        } catch (NullPointerException crash) {
            throw new AssertionError("Malformed tooltip still crashes TreeMap lookup", crash);
        }
        check(result.equals(Arrays.asList("block", "mod:keyword")), "preserve order and unknown non-null keywords");
        check(input.size() == 4 && input.get(1) == null, "preserve original evidence");
        ArrayList<String> clean = new ArrayList<>(Arrays.asList("block", "block", ""));
        check(sanitize.invoke(null, clean, null) == clean, "valid path must not allocate or change input");
        for (int i = 0; i < 100; i++) sanitize.invoke(null, input, null);
        check(((ArrayList<?>) sanitize.invoke(null, null, null)).isEmpty(), "null list becomes empty");
        // Allocate a real card type without texture/localization constructors in this headless harness.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object allocator = singleton.get(null);
        Class<?> cardType = Class.forName("com.megacrit.cardcrawl.cards.red.Strike_Red");
        Object card = unsafeClass.getMethod("allocateInstance", Class.class).invoke(allocator, cardType);
        cardType.getField("cardID").set(card, "test:MalformedShopCard");
        cardType.getField("name").set(card, "Diagnostic test card");
        cardType.getField("rawDescription").set(card, "Original description for investigation");
        cardType.getField("keywords").set(card, input);
        result = (ArrayList<?>) sanitize.invoke(null, input, card);
        check(!result.contains(null), "card context must not break filtering");
        check(cardType.getField("keywords").get(card) == input, "keep original card keywords");
        System.out.println("PASS: null lookup prevented, valid keywords preserved, source untouched, repeated calls safe");
    }

    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
}
