package communicationmod.compat;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.megacrit.cardcrawl.map.MapRoomNode;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;

/** Game-thread-only optional adapter. No symbolic dependency on Downfall classes.
 * Uses the same helpers injected into the installed UI, not a guessed reversed Y.
 */
public final class DownfallMapCoordinates {
    private static boolean resolved;
    private static Method firstNode;
    private static Method bossNode;
    private static String failure;

    private DownfallMapCoordinates() {}

    public static boolean isSupported() {
        resolve();
        return failure == null;
    }

    public static String getUnsupportedReason() {
        resolve();
        return failure;
    }

    public static int firstUiRow(MapRoomNode node) {
        resolve();
        return coordinate(firstNode, node);
    }

    public static int bossUiRow(MapRoomNode node) {
        resolve();
        return coordinate(bossNode, node);
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            if (!Loader.isModLoaded("EvilWithin")) return;
            ClassLoader loader = DownfallMapCoordinates.class.getClassLoader();
            firstNode = Class.forName("downfall.patches.ui.map.FlipMap$FirstRoom", false, loader)
                .getMethod("isValidFirstNode", MapRoomNode.class);
            bossNode = Class.forName("downfall.patches.ui.map.FlipMap$BossStuff", false, loader)
                .getMethod("compatibleGetARealY", MapRoomNode.class);
            if (firstNode.getReturnType() != int.class || bossNode.getReturnType() != int.class)
                throw new NoSuchMethodException("Unexpected Downfall map coordinate signature");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            fail(unavailable);
        }
    }

    private static int coordinate(Method method, MapRoomNode node) {
        if (failure != null || node == null) return Integer.MIN_VALUE;
        if (method == null) return node.y; // Only when Downfall is not loaded.
        try {
            return (Integer) method.invoke(null, node);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            fail(unavailable);
            return Integer.MIN_VALUE;
        }
    }

    private static void fail(Throwable cause) {
        if (failure != null) return;
        failure = "DOWNFALL_MAP_ADAPTER_UNAVAILABLE";
        LogManager.getLogger(DownfallMapCoordinates.class).warn(
            "[COMM-SUPPORT] Downfall map choices disabled; UI coordinate adapter unavailable", cause);
    }
}
