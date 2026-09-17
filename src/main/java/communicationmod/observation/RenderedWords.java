package communicationmod.observation;

import java.util.*;

/** Text already submitted by word renderers; no unrevealed sentence/queue is accepted. */
public final class RenderedWords {
    private final StringBuilder text = new StringBuilder();
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private int previousLine;
    private boolean truncated;

    public void append(Object identity, String word, int line, float alpha, float scale, boolean characterBreak) {
        if (word == null || !(alpha > 0) || !(scale > 0) || Float.isInfinite(alpha) || Float.isInfinite(scale)) return;
        if (seen.contains(identity)) return;
        if (seen.size() >= 512 || text.length() > 8192) { truncated = true; return; }
        seen.add(identity);
        if (text.length() > 0) {
            if (line != previousLine) text.append('\n');
            else if (!characterBreak && !word.isEmpty() && !Character.isWhitespace(text.charAt(text.length()-1))
                && !Character.isWhitespace(word.charAt(0))) text.append(' ');
        }
        previousLine = line;
        int count = Math.min(word.length(), 8193 - Math.min(text.length(), 8193));
        text.append(word, 0, count);
        truncated |= count < word.length() || text.length() > 8192;
    }
    public String text() { return text.toString(); }
    public boolean truncated() { return truncated; }
    public boolean isComplete(boolean textDone, int expectedWords) {
        return textDone && expectedWords > 0 && seen.size() == expectedWords && !truncated && text.length() > 0;
    }
}
