package communicationmod.observation;

/** Bounds for native UI modes. Room type and unclaimed rewards do not hide a visible proceed button. */
public final class RunUiPolicy {
    private RunUiPolicy() { }
    public static boolean cardReward(boolean nativeScreen,boolean touch,boolean voting,int count) {
        return nativeScreen && !touch && !voting && count>0 && count<=128;
    }
    public static boolean proceed(boolean visible,boolean pendingClick,boolean animating) {
        return visible && !pendingClick && !animating;
    }
    public static boolean potionUse(boolean occupied,boolean canUse,boolean requiresTarget,boolean inCombat,boolean liveTarget) {
        return occupied && canUse && (!requiresTarget || inCombat && liveTarget);
    }
}
