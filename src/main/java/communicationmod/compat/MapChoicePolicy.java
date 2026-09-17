package communicationmod.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Pure map selection, with coordinate and connection rules supplied by the live UI. */
public final class MapChoicePolicy {
    private MapChoicePolicy() {}
    public static <N> ArrayList<N> choices(List<? extends List<N>> rows, boolean firstChosen, N current,
            ToIntFunction<N> firstUiRow, Predicate<N> viable, BiPredicate<N, N> connected,
            BiPredicate<N, N> winged) {
        ArrayList<N> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return result;
        if (!firstChosen) {
            for (List<N> row : rows) for (N node : row)
                if (viable.test(node) && firstUiRow.applyAsInt(node) == 0) result.add(node);
        } else if (current != null) {
            for (List<N> row : rows) for (N node : row)
                if (viable.test(node) && (connected.test(current, node) || winged.test(current, node))) result.add(node);
        }
        return result;
    }
    public static boolean bossAvailable(int uiRow, boolean ending) {
        return uiRow == 14 || (ending && uiRow == 2);
    }
}
