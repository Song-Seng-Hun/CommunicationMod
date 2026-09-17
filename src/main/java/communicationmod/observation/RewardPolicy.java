package communicationmod.observation;
import java.util.*;
public final class RewardPolicy {
    private static final Set<String> STANDARD=new HashSet<>(Arrays.asList("GOLD","STOLEN_GOLD","CARD","RELIC","POTION","EMERALD_KEY","SAPPHIRE_KEY"));
    private RewardPolicy() { }
    public static boolean claimable(String type,boolean standardClass,boolean pending,boolean ignored,boolean potionSlot) {
        return standardClass && STANDARD.contains(type) && !pending && !ignored && (!"POTION".equals(type) || potionSlot);
    }
}
