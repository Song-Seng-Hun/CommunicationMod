package communicationmod;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/** Projects a serialized draw pile without changing game state or consuming RNG. */
public final class DrawPileVisibility {
    private static final Gson GSON = new Gson();
    private static final Comparator<HashMap<String, Object>> DISPLAY_ORDER =
        Comparator.comparing((HashMap<String, Object> card) -> String.valueOf(card.get("id")))
            // Include every serialized field: even copies sharing an ID/UUID must
            // not leak their original relative order through stable-sort ties.
            .thenComparing(card -> GSON.toJson(new TreeMap<>(card)));

    private DrawPileVisibility() { }

    public static ArrayList<HashMap<String, Object>> orderForPlayer(
            List<HashMap<String, Object>> cards, boolean orderVisible) {
        ArrayList<HashMap<String, Object>> result = new ArrayList<>(cards);
        if (!orderVisible) {
            result.sort(DISPLAY_ORDER);
        }
        return result;
    }
}
