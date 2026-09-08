package communicationmod.observation;

/** Conservative known-screen policy; unknown mod overlays need an explicit adapter. */
public final class DialogueVisibility {
    private DialogueVisibility() { }
    public static boolean allows(String screen, boolean screenUp, boolean popup, float globalFade, float dungeonFade) {
        return !popup && globalFade == 0f && dungeonFade == 0f
            && ("SHOP".equals(screen) || ("NONE".equals(screen) && !screenUp));
    }
}
