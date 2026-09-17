package communicationmod.safety;

/** Temporary non-bypassable hold, NOT an implementation of Steam isolation. */
public final class AutomationSafety {
    public static final String REASON = "[COMM-SAFETY-BLOCK] Automation disabled until Steam "
        + "submission blocking and save/cloud isolation are verified.";

    private AutomationSafety() { }

    public static boolean isAutomationAllowed() {
        return false;
    }

    public static void requireAutomationAllowed() {
        if (!isAutomationAllowed()) throw new IllegalStateException(REASON);
    }
}
