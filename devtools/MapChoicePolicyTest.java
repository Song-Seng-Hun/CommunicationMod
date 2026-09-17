import communicationmod.compat.MapChoicePolicy;
import java.util.*;

/** Pure graph fixtures: no game, native code or character state synthesized. */
public final class MapChoicePolicyTest {
    public static void main(String[] args) {
        List<List<Integer>> rows = Arrays.asList(Arrays.asList(0, 1), Arrays.asList(7), Arrays.asList(14, 15));
        List<Integer> reversed = MapChoicePolicy.choices(rows, false, null,
            n -> n >= 14 ? 0 : 1, n -> n != 15, (a,b) -> false, (a,b) -> false);
        equal(reversed, Arrays.asList(14));
        equal(MapChoicePolicy.choices(rows, false, null, n -> n < 2 ? 0 : 1,
            n -> true, (a,b) -> false, (a,b) -> false), Arrays.asList(0,1));
        // Connection functions belong to the game; Flight/boot charges are not guessed.
        equal(MapChoicePolicy.choices(rows, true, 7, n -> 99, n -> n != 15,
            (a,b) -> b == 0, (a,b) -> b == 1), Arrays.asList(0,1));
        equal(MapChoicePolicy.choices(rows, true, 7, n -> 99, n -> true,
            (a,b) -> b == 0, (a,b) -> false), Arrays.asList(0));
        equal(MapChoicePolicy.choices(rows, true, null, n -> 0, n -> true,
            (a,b) -> true, (a,b) -> true), Collections.emptyList());
        equal(MapChoicePolicy.choices(Collections.<List<Integer>>emptyList(), false, null,
            n -> 0, n -> true, (a,b) -> true, (a,b) -> true), Collections.emptyList());
        equal(MapChoicePolicy.bossAvailable(14, false), true);
        equal(MapChoicePolicy.bossAvailable(2, true), true);
        equal(MapChoicePolicy.bossAvailable(2, false), false);
        equal(MapChoicePolicy.bossAvailable(Integer.MIN_VALUE, true), false);
        equal(rows, Arrays.asList(Arrays.asList(0,1), Arrays.asList(7), Arrays.asList(14,15)));
        System.out.println("PASS: supplied reverse/base UI coordinates, normal/flight connections, ending boss, unavailable state, no map mutation");
    }
    private static void equal(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
