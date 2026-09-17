package communicationmod.observation;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import java.util.*;

/** Game-thread-only allowlisted render binding. Never reads a message scanner, queue or catalogue. */
public final class DialogueObservation {
    private static final DialogueHistory HISTORY = new DialogueHistory();
    private static final EventReading READING = new EventReading();
    private static final Map<Object, Source> SOURCES = new WeakHashMap<>();
    private static final Deque<Source> ORIGINS = new ArrayDeque<>();
    private static final Deque<Rendered> RENDERS = new ArrayDeque<>();
    private static final Set<String> ISSUES = new LinkedHashSet<>();
    private static Object player;
    private static AbstractRoom room;
    private static long visit;
    private static Object bodyToken;
    private static float frameFade;
    private static Rendered pendingEvent, committedEvent;
    private static String notifiedReading;

    private DialogueObservation() { }

    public static void beginFrame(float globalFade) {
        if (AbstractDungeon.player != player) {
            reset(); player = AbstractDungeon.player;
        }
        refreshRoom();
        frameFade = globalFade;
        ORIGINS.clear(); RENDERS.clear(); pendingEvent = null; HISTORY.beginFrame();
    }

    public static void completeFrame(float globalFade) {
        HISTORY.completeFrame(visible(globalFade));
        committedEvent = pendingEvent;
        frameFade = globalFade;
        syncEventReading();
        String readingState = READING.readingId() + ":" + READING.phase();
        if (!readingState.equals(notifiedReading)) {
            // Reveal text is already rate-limited by HISTORY. Notify readiness boundaries promptly.
            if (!"revealing".equals(READING.phase())) communicationmod.CommunicationMod.mustSendGameState = true;
            notifiedReading = readingState;
        }
        if (HISTORY.notificationDue(System.nanoTime())) communicationmod.CommunicationMod.mustSendGameState = true;
        ORIGINS.clear(); RENDERS.clear();
    }

    public static void reset() {
        HISTORY.reset(); SOURCES.clear(); ORIGINS.clear(); RENDERS.clear(); ISSUES.clear();
        READING.reset(); pendingEvent = committedEvent = null; notifiedReading = null;
        player = null; room = null; bodyToken = null; visit = 0;
    }
    public static void failed(Throwable error) {
        HISTORY.abortFrame(); ORIGINS.clear(); RENDERS.clear();
        pendingEvent = committedEvent = null; READING.suspend();
        if (ISSUES.add("observer_error")) {
            System.err.println("[COMM-DIALOGUE] Observation failed; frame discarded; repeated errors suppressed until run reset.");
            error.printStackTrace(System.err);
        }
    }

    public static void enterOrigin(String kind, String name) { ORIGINS.push(new Source(kind, name)); }
    public static void enterActor(AbstractCreature actor) {
        enterOrigin(actor == null ? "unknown" : actor.isPlayer ? "player"
            : actor instanceof AbstractMonster ? "enemy" : "unknown", actor == null ? null : actor.name);
    }
    public static void leaveOrigin() { if (!ORIGINS.isEmpty()) ORIGINS.pop(); }

    /** Attach origin identity only. The constructor's complete message is deliberately not accepted. */
    public static void created(Object owner) {
        Source origin = ORIGINS.peek();
        putSource(owner, origin == null ? new Source("unknown", null) : new Source(origin.kind, origin.name));
    }
    public static void newBody(Object owner) {
        refreshRoom();
        Source source = new Source("event", null);
        putSource(owner, source); bodyToken = source.token;
        READING.newPage(bodyToken); pendingEvent = committedEvent = null;
    }
    private static void putSource(Object owner, Source source) {
        if (SOURCES.size() >= 256) { SOURCES.clear(); ISSUES.add("source_registry_evicted"); }
        SOURCES.put(owner, source);
    }

    public static void enterRender(Object owner, String channel) {
        refreshRoom();
        Source source = SOURCES.get(owner);
        if (source == null) { created(owner); source = SOURCES.get(owner); }
        RENDERS.push(new Rendered(source, channel));
    }

    /** Called only AFTER the corresponding word render returns normally. */
    public static void word(Object identity, String text, int line, float alpha, float scale) {
        Rendered render = RENDERS.peek();
        if (render == null || !visible(frameFade)) return;
        render.words.append(identity, text, line, alpha, scale, Settings.lineBreakViaCharacter);
        if (render.words.truncated()) ISSUES.add("rendered_word_budget_exceeded");
    }

    public static void finishRender() {
        Rendered render = RENDERS.peek();
        if (render == null) return;
        Map<String,String> context = situation();
        context.put("channel", render.channel);
        HISTORY.observe(render.source.token, render.words.text(), render.source.kind,
            render.source.name, Settings.language == null ? "unknown" : Settings.language.name(), context);
    }
    public static void leaveRender() { if (!RENDERS.isEmpty()) RENDERS.pop(); }

    /** Reads only completion metadata, not the remaining scanner or full message. */
    public static void finishEventRender(boolean shown, boolean textDone, int wordCount) {
        Rendered render = RENDERS.peek();
        if (render == null || !shown || render.words.text().isEmpty()) return;
        finishRender();
        bodyToken = render.source.token;
        render.complete = render.words.isComplete(textDone, wordCount);
        render.options = eventOptions();
        render.language = Settings.language == null ? "unknown" : Settings.language.name();
        pendingEvent = render;
    }

    public static Map<String,Object> eventReading() {
        syncEventReading();
        Map<String,Object> result = READING.snapshot();
        result.put("support_status", "partial");
        result.put("completion_basis", "text_done_and_all_words_rendered_without_truncation");
        return result;
    }

    public static boolean inEventContext() {
        if (!communicationmod.CommandExecutor.isInDungeon()) return false;
        AbstractRoom current = currentRoom();
        return current != null && (current.phase == AbstractRoom.RoomPhase.EVENT
            || current.phase == AbstractRoom.RoomPhase.COMPLETE && current.event != null);
    }

    /** Legacy raw input is not a bypass. The v2 acknowledgement is deliberately not a text command. */
    public static boolean allowsEventCommand(String command) {
        if (!inEventContext()) return true;
        if ("state".equals(command) || "wait".equals(command)) return true;
        syncEventReading();
        return "choose".equals(command) && !AbstractDungeon.isScreenUp && READING.canChoose();
    }

    public static void claimEventChoice(int choice) {
        syncEventReading();
        if (!inEventContext() || AbstractDungeon.isScreenUp) throw new IllegalArgumentException("No readable event page");
        READING.choose(choice);
        communicationmod.CommunicationMod.mustSendGameState = true;
    }

    /** For the future live v2 screen adapter; current global automation hold still applies. */
    public static List<communicationmod.protocol.ProtocolSession.Action> eventReadingActions() {
        syncEventReading();
        return communicationmod.protocol.EventReadingActions.offer(READING, DialogueObservation::syncEventReading);
    }

    private static void syncEventReading() {
        refreshRoom();
        if (!inEventContext() || committedEvent == null || committedEvent.source.token != bodyToken
            || !visible(frameFade) || AbstractDungeon.player == null || AbstractDungeon.player.isDead
            || communicationmod.ChoiceScreenUtils.getEventDialogType() == communicationmod.ChoiceScreenUtils.EventDialogType.NONE) {
            READING.suspend(); return;
        }
        // A button changed during update, after the published render: wait for another completed frame.
        if (!committedEvent.options.equals(eventOptions())) { READING.suspend(); return; }
        if (committedEvent.words.truncated()) {
            READING.newPage(bodyToken); READING.unavailable("rendered_text_truncated"); return;
        }
        READING.observe(bodyToken, committedEvent.words.text(), committedEvent.language,
            committedEvent.options, committedEvent.complete, true);
    }

    private static List<EventReading.Option> eventOptions() {
        List<EventReading.Option> result = new ArrayList<>();
        for (com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton button : communicationmod.ChoiceScreenUtils.getEventButtons()) {
            result.add(new EventReading.Option(button.msg == null ? null
                : communicationmod.GameStateConverter.removeTextFormatting(button.msg), button.isDisabled));
            if (result.size() > 64) break; // The model marks overflow unavailable, never ready.
        }
        return result;
    }

    public static Map<String,Object> snapshot() {
        Map<String,Object> result = HISTORY.snapshot();
        result.put("situation", situation());
        result.put("support_status", "partial");
        result.put("visibility_basis", "completed_word_render_positive_alpha_and_scale");
        List<String> limitations = new ArrayList<>(ISSUES);
        limitations.add("custom_renderers_not_verified");
        limitations.add("pixel_occlusion_not_verified");
        result.put("unavailable_reasons", limitations);
        return result;
    }

    /** Do not expose the full future text from UpdateBodyTextPatch's pre-reveal cache. */
    public static String eventBody() {
        if (currentRoom() != room) return "";
        return HISTORY.visibleText(bodyToken);
    }

    private static Map<String,String> situation() {
        Map<String,String> result = new LinkedHashMap<>();
        AbstractRoom current = currentRoom();
        result.put("room_type", current == null ? "none" : current.getClass().getSimpleName());
        result.put("room_phase", current == null || current.phase == null ? "none" : current.phase.name());
        result.put("screen", AbstractDungeon.screen == null ? "none" : AbstractDungeon.screen.name());
        result.put("floor", Integer.toString(AbstractDungeon.floorNum));
        result.put("act", Integer.toString(AbstractDungeon.actNum));
        result.put("turn", Integer.toString(com.megacrit.cardcrawl.actions.GameActionManager.turn));
        result.put("room_visit", Long.toString(visit));
        return result;
    }
    private static AbstractRoom currentRoom() {
        com.megacrit.cardcrawl.map.MapRoomNode node = AbstractDungeon.getCurrMapNode();
        return node == null ? null : node.getRoom();
    }
    private static void refreshRoom() {
        AbstractRoom current = currentRoom();
        if (current != room) {
            room = current; bodyToken = null; visit++;
            READING.reset(); pendingEvent = committedEvent = null;
        }
    }
    private static boolean visible(float globalFade) {
        return DialogueVisibility.allows(AbstractDungeon.screen == null ? null : AbstractDungeon.screen.name(),
            AbstractDungeon.isScreenUp, com.megacrit.cardcrawl.core.CardCrawlGame.isPopupOpen,
            globalFade, AbstractDungeon.fadeColor == null ? 1f : AbstractDungeon.fadeColor.a);
    }
    private static final class Source {
        final Object token = new Object();
        final String kind, name;
        Source(String kind, String name) { this.kind = kind; this.name = name; }
    }
    private static final class Rendered {
        final Source source;
        final String channel;
        final RenderedWords words = new RenderedWords();
        boolean complete;
        String language;
        List<EventReading.Option> options;
        Rendered(Source source, String channel) { this.source = source; this.channel = channel; }
    }
}
