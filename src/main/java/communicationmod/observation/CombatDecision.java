package communicationmod.observation;

import java.util.*;

/** Single-threaded, JDK-only gate. A read/validation call can invalidate, never establish stability. */
public final class CombatDecision {
    private String mode = "none", key, reason = "no_completed_frame", issued;
    private int settledFrames;
    private boolean consumed;

    public void reset() {
        mode = "none"; key = null; reason = "no_completed_frame";
        issued = null; settledFrames = 0; consumed = false;
    }

    public void completeFrame(String nextMode, String nextKey, String blockedReason) {
        boolean same = matches(nextMode, nextKey, blockedReason);
        refresh(nextMode, nextKey, blockedReason);
        if (reason == null && !consumed) settledFrames = same ? Math.min(2, settledFrames + 1) : 1;
    }

    public void refresh(String nextMode, String nextKey, String blockedReason) {
        if (nextKey == null || nextKey.length() > 262144) blockedReason = "invalid_snapshot";
        if (!"play".equals(nextMode) && !"selection".equals(nextMode)) blockedReason = "not_a_decision_screen";
        if (!matches(nextMode, nextKey, blockedReason)) {
            mode = nextMode; key = nextKey; reason = blockedReason;
            settledFrames = 0; issued = null; consumed = false;
        }
    }

    public boolean ready() { return reason == null && settledFrames >= 2 && !consumed; }
    public boolean handComplete() { return ready() && "play".equals(mode); }

    /** Issue only when an observation is constructed, not as a side effect of command validation. */
    public String issue() {
        if (!ready()) return null;
        if (issued == null) issued = UUID.randomUUID().toString();
        return issued;
    }

    public void validate(String expected, String liveMode, String liveKey, String blockedReason) {
        refresh(liveMode, liveKey, blockedReason);
        if (!ready() || issued == null || !issued.equals(expected))
            throw new IllegalArgumentException("COMBAT_NOT_READY_OR_STALE: request a new stable decision");
    }

    public void claim(String expected, String liveMode, String liveKey, String blockedReason) {
        validate(expected, liveMode, liveKey, blockedReason);
        consumed = true; // Even a failing dispatcher cannot replay this observation.
    }

    public Map<String,Object> snapshot() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("ready", ready()); result.put("hand_complete", handComplete());
        result.put("decision_id", ready() ? issued : null);
        result.put("mode", mode); result.put("settled_frames", settledFrames);
        result.put("reason", consumed ? "awaiting_resolution" : reason != null ? reason : settledFrames < 2 ? "settling" : null);
        return result;
    }

    private boolean matches(String nextMode, String nextKey, String nextReason) {
        return Objects.equals(mode, nextMode) && Objects.equals(key, nextKey) && Objects.equals(reason, nextReason);
    }
}
