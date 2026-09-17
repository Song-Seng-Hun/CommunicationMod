import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Headless checks of the outgoing card-list policy; no game RNG or graphics. */
public final class DrawPileVisibilityTest {
    private static Method project;

    public static void main(String[] args) throws Exception {
        try {
            project = Class.forName("communicationmod.DrawPileVisibility")
                .getMethod("orderForPlayer", List.class, boolean.class);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Draw pile visibility policy is missing", missing);
        }
        List<HashMap<String, Object>> cards = Arrays.asList(
            card("Strike_R", "b", 0, 1), card("Defend_R", "c", 0, 1),
            card("Strike_R", "a", 1, 0), card("Strike_R", "d", 0, 1),
            // Same UUID can occur on stat-equivalent copies; differing visible fields
            // must not retain their original relative positions via a stable sort.
            card("Strike_R", "b", 0, 0));
        List<?> canonical = output(cards, false);
        permutations(new ArrayList<>(cards), 0, canonical);
        check(output(Collections.<HashMap<String, Object>>emptyList(), false).isEmpty(), "empty pile");
        check(output(cards.subList(0, 1), false).equals(cards.subList(0, 1)), "singleton pile");
        check(output(cards, true).equals(cards), "Frozen Eye preserves bottom-to-top order");
        check(output(cards, false).equals(canonical), "relic removal hides order immediately");
        check(output(cards, false).equals(output(cards, false)), "repeated states are stable");
        System.out.println("PASS: 120 permutations, contents, duplicate identities, source preservation, visible/hidden branches, empty/singleton");
    }

    private static void permutations(List<HashMap<String, Object>> cards, int index, List<?> canonical) throws Exception {
        if (index == cards.size()) {
            List<HashMap<String, Object>> before = new ArrayList<>();
            for (HashMap<String, Object> card : cards) before.add(new HashMap<>(card));
            List<?> hidden = output(cards, false);
            check(hidden.equals(canonical), "hidden output changes when only real draw order changes");
            check(hidden.size() == cards.size() && hidden.containsAll(cards), "card composition changed");
            check(output(cards, true).equals(cards), "visible order changed");
            check(cards.equals(before), "live list or card fields mutated");
            check(hidden != cards, "outgoing list must be a copy");
            return;
        }
        for (int next = index; next < cards.size(); next++) {
            Collections.swap(cards, index, next);
            permutations(cards, index + 1, canonical);
            Collections.swap(cards, index, next);
        }
    }

    private static List<?> output(List<HashMap<String, Object>> cards, boolean visible) throws Exception {
        return (List<?>) project.invoke(null, cards, visible);
    }

    private static HashMap<String, Object> card(String id, String uuid, int upgrades, int cost) {
        HashMap<String, Object> card = new HashMap<>();
        card.put("id", id);
        card.put("uuid", uuid);
        card.put("upgrades", upgrades);
        card.put("cost", cost);
        card.put("name", "Test card");
        return card;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
