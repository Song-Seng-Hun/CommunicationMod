/** Fail closed until account submission blocking and save isolation are verified. */
public final class DevelopmentLaunchSafety {
    private DevelopmentLaunchSafety() { }

    public static void requireVerifiedRuntime() {
        throw new IllegalStateException("[COMM-SAFETY-BLOCK] Game launch is disabled: "
            + "Steam submission blocking and save/cloud isolation have not been verified. "
            + "Use headless verification or runtime-inventory.ps1; there is no override flag.");
    }
}
