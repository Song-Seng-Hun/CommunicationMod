package communicationmod.observation;

import java.util.*;

/** Game-independent bounded history. Only completed render observations may enter this API. */
public final class DialogueHistory {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_TEXT = 8192;
    private final Map<Object, Entry> owners = new WeakHashMap<>();
    private final LinkedHashMap<Object, Pending> pending = new LinkedHashMap<>();
    private final Deque<Entry> entries = new ArrayDeque<>();
    private String session = UUID.randomUUID().toString();
    private long frame, publishedFrame, sequence, dropped, revision;
    private long notifiedRevision, notifiedAt;
    private boolean notified;
    private boolean collecting;

    public void beginFrame() {
        pending.clear(); frame++; collecting = true;
    }

    public void observe(Object owner, String text, String kind, String name,
            String language, Map<String, String> context) {
        if (!collecting || owner == null || text == null || text.trim().isEmpty()) return;
        if (!pending.containsKey(owner) && pending.size() >= MAX_ENTRIES) { dropped++; return; }
        Pending next = new Pending();
        next.text = text.substring(0, Math.min(text.length(), MAX_TEXT));
        next.truncated = text.length() > MAX_TEXT;
        next.kind = bound(kind == null ? "unknown" : kind, 128);
        next.name = bound(name, 256);
        next.language = bound(language, 32);
        next.context = new LinkedHashMap<>();
        if (context != null) for (Map.Entry<String,String> item : context.entrySet()) {
            if (next.context.size() >= 16) break;
            next.context.put(bound(item.getKey(), 128), bound(item.getValue(), 256));
        }
        pending.put(owner, next);
    }

    public boolean completeFrame() {
        if (!collecting) return false;
        boolean changed = false;
        Set<Entry> visible = Collections.newSetFromMap(new IdentityHashMap<Entry, Boolean>());
        for (Map.Entry<Object, Pending> item : pending.entrySet()) {
            Pending next = item.getValue();
            Entry entry = owners.get(item.getKey());
            if (entry == null) {
                entry = new Entry(); entry.id = ++sequence;
                entry.kind = next.kind; entry.name = next.name; entry.language = next.language;
                entry.context = next.context;
                owners.put(item.getKey(), entry); entries.addLast(entry);
                if (entries.size() > MAX_ENTRIES) {
                    Entry evicted = entries.removeFirst();
                    owners.values().removeIf(value -> value == evicted);
                    dropped++;
                }
                changed = true;
            }
            changed |= entry.lastFrame != publishedFrame || !next.text.equals(entry.visibleText);
            // Preserve the fullest actually rendered snapshot when words begin fading.
            if (next.text.length() >= entry.text.length()) entry.text = next.text;
            entry.visibleText = next.text; entry.truncated |= next.truncated;
            entry.lastFrame = frame; visible.add(entry);
        }
        for (Entry entry : entries) {
            if (entry.lastFrame == publishedFrame && !visible.contains(entry)) changed = true;
        }
        publishedFrame = frame; pending.clear(); collecting = false;
        if (changed) revision++;
        return changed;
    }
    public boolean completeFrame(boolean visible) {
        if (!visible) pending.clear();
        return completeFrame();
    }
    public boolean notificationDue(long nowNanos) {
        if (revision == notifiedRevision || (notified && nowNanos - notifiedAt < 250000000L)) return false;
        notified = true; notifiedAt = nowNanos; notifiedRevision = revision;
        return true;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.id); row.put("text", entry.text);
            row.put("speaker_type", entry.kind); row.put("speaker_name", entry.name);
            row.put("text_language", entry.language);
            row.put("context", new LinkedHashMap<>(entry.context));
            row.put("currently_displayed", entry.lastFrame == publishedFrame);
            row.put("visible_text", entry.lastFrame == publishedFrame ? entry.visibleText : "");
            row.put("text_truncated", entry.truncated);
            copy.add(row);
        }
        result.put("session_id", session); result.put("revision", revision);
        result.put("render_frame", publishedFrame); result.put("entries", copy);
        result.put("dropped_entries", dropped);
        return result;
    }
    public String visibleText(Object owner) {
        Entry entry = owners.get(owner);
        return entry != null && entry.lastFrame == publishedFrame ? entry.visibleText : "";
    }

    public void reset() {
        owners.clear(); pending.clear(); entries.clear(); session = UUID.randomUUID().toString();
        frame = publishedFrame = sequence = dropped = revision = 0; collecting = false;
        notifiedRevision = notifiedAt = 0; notified = false;
    }
    public void abortFrame() { pending.clear(); collecting = false; }

    private static String bound(String value, int limit) {
        return value == null ? null : value.substring(0, Math.min(value.length(), limit));
    }
    private static final class Pending {
        String text, kind, name, language;
        boolean truncated;
        Map<String, String> context;
    }
    private static final class Entry {
        long id, lastFrame = -1;
        String text = "", visibleText = "", kind, name, language;
        boolean truncated;
        Map<String, String> context;
    }
}
