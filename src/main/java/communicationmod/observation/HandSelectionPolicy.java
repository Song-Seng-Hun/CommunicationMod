package communicationmod.observation;

/** Counts only; actual button visibility and game decision guards remain mandatory. */
public final class HandSelectionPolicy {
    private HandSelectionPolicy() { }
    public static boolean canSelect(int max,int selected) {
        return max>0 && selected>=0 && (selected<max || max==1 && selected==1);
    }
    public static boolean canConfirm(int max,int selected,boolean upTo,boolean zero) {
        return max>=0 && selected>=0 && selected<=max
            && (selected==0 ? zero : upTo || selected==max);
    }
}
