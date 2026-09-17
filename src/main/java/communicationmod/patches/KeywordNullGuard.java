package communicationmod.patches;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Keeps the original card data intact for diagnosis; filters only the render input. */
public final class KeywordNullGuard {
    private static final Logger LOG = LogManager.getLogger(KeywordNullGuard.class);
    private static final long INTERVAL = TimeUnit.SECONDS.toNanos(60);
    private static boolean diagnosticFailureReported;
    private static final Map<String, Incident> INCIDENTS = new LinkedHashMap<String, Incident>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Incident> eldest) {
            return size() > 128;
        }
    };

    private KeywordNullGuard() {}

    public static ArrayList<String> sanitize(ArrayList<String> keywords, AbstractCard card) {
        if (keywords != null && !keywords.contains(null)) return keywords;
        ArrayList<String> safe = new ArrayList<>();
        if (keywords != null) {
            for (String keyword : keywords) if (keyword != null) safe.add(keyword);
        }
        // Diagnostics must not turn a prevented tooltip failure into a new crash.
        try {
            report(keywords, card);
        } catch (RuntimeException diagnosticFailure) {
            if (!diagnosticFailureReported) {
                diagnosticFailureReported = true;
                LOG.warn("[COMM-KEYWORD-DIAGNOSTIC] Null keywords removed, but detailed logging failed",
                    diagnosticFailure);
            }
        }
        return safe;
    }

    private static synchronized void report(ArrayList<String> keywords, AbstractCard card) {
        String cardId = card == null ? "<no-card>" : card.cardID;
        String room = AbstractDungeon.getCurrMapNode() == null || AbstractDungeon.getCurrRoom() == null
            ? "<no-room>" : AbstractDungeon.getCurrRoom().getClass().getName();
        String key = Settings.seed + "|" + AbstractDungeon.floorNum + "|" + cardId + "|"
            + (card == null ? "" : card.rawDescription) + "|" + keywords;
        long now = System.nanoTime();
        Incident incident = INCIDENTS.get(key);
        if (incident == null) {
            incident = new Incident(now);
            INCIDENTS.put(key, incident);
        } else {
            incident.count++;
            if (now - incident.lastLog < INTERVAL) return;
            incident.lastLog = now;
        }
        String source = "<unknown>";
        try {
            if (card != null) {
                java.security.CodeSource codeSource = card.getClass().getProtectionDomain().getCodeSource();
                if (codeSource != null && codeSource.getLocation() != null) {
                    source = codeSource.getLocation().toString();
                }
            }
        } catch (SecurityException unavailable) {
            source = "<access-denied>";
        }
        LOG.warn("[COMM-KEYWORD-GUARD] Prevented null-keyword tooltip crash; occurrences=" + incident.count
            + "; cardId=" + cardId + "; name=" + (card == null ? "" : card.name)
            + "; cardClass=" + (card == null ? "" : card.getClass().getName()) + "; source=" + source
            + "; upgrades=" + (card == null ? 0 : card.timesUpgraded)
            + "; rawDescription=" + (card == null ? "" : card.rawDescription)
            + "; cardKeywords=" + (card == null ? "" : card.keywords)
            + "; renderKeywords=" + keywords + "; language=" + Settings.language
            + "; seed=" + Settings.seed + "; floor=" + AbstractDungeon.floorNum
            + "; room=" + room + "; screen=" + AbstractDungeon.screen
            + "; player=" + (AbstractDungeon.player == null ? "<none>" : AbstractDungeon.player.chosenClass)
            + "; original data preserved; repeated incidents throttled to once per 60 seconds",
            new Throwable("Detection stack only: this is NOT the origin of the invalid keyword"));
    }

    private static final class Incident {
        private long lastLog;
        private long count = 1;
        private Incident(long now) { lastLog = now; }
    }
}
