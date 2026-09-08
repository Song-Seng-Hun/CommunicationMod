package communicationmod.observation;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import communicationmod.ChoiceScreenUtils;
import communicationmod.CommandExecutor;
import java.util.*;

/** Game-thread binding. Queues are checked for pending work, never serialized or simulated. */
public final class CombatObservation {
    private static final CombatDecision GATE = new CombatDecision();
    private static AbstractPlayer player;
    private static AbstractRoom room;
    private static float globalFade = 1;
    private static boolean frameFinished, reported;
    private static String lastStatus;
    private CombatObservation() { }

    public static void beginFrame(float fade) { globalFade = fade; frameFinished = false; }
    public static void completeFrame(float fade) {
        globalFade = fade;
        Sample sample = sample();
        GATE.completeFrame(sample.mode, sample.key, sample.reason);
        frameFinished = true;
        String status = GATE.snapshot().toString();
        if (!status.equals(lastStatus)) communicationmod.CommunicationMod.mustSendGameState = true;
        lastStatus = status;
    }
    public static void endFrame() { if (!frameFinished) invalidate(); }
    public static void invalidate() { GATE.reset(); }
    public static void failed(Throwable failure) {
        invalidate();
        if (!reported) {
            reported = true;
            System.err.println("[COMM-COMBAT] Readiness observation failed; decisions withheld (repeats suppressed).");
            failure.printStackTrace(System.err);
        }
    }
    private static AbstractRoom currentRoom() {
        return AbstractDungeon.getCurrMapNode() == null ? null : AbstractDungeon.getCurrMapNode().getRoom();
    }
    public static boolean inCombat() {
        AbstractRoom current = currentRoom();
        return CommandExecutor.isInDungeon() && current != null && current.phase == AbstractRoom.RoomPhase.COMBAT;
    }
    private static Sample refresh() {
        Sample sample = sample();
        GATE.refresh(sample.mode, sample.key, sample.reason);
        return sample;
    }
    public static boolean ready() { refresh(); return GATE.ready(); }
    public static boolean handComplete() { refresh(); return GATE.handComplete(); }

    public static Map<String,Object> observation() {
        refresh(); GATE.issue();
        Map<String,Object> result = GATE.snapshot();
        result.put("support_status", "partial");
        result.put("basis", "two_completed_frames_and_live_dispatch_revalidation");
        return result;
    }

    /** Recheck after description/tooltip getters; an observation itself must not publish a changed hand as ready. */
    @SuppressWarnings("unchecked")
    public static void finishObservation(Map<String,Object> response) {
        Map<String,Object> before = (Map<String,Object>)response.get("combat_decision");
        Map<String,Object> after = observation();
        response.put("combat_decision", after);
        boolean same = before != null && Objects.equals(before.get("decision_id"), after.get("decision_id"));
        if (inCombat()) response.put("ready_for_command", same && Boolean.TRUE.equals(after.get("ready")));
        if (!same || !Boolean.TRUE.equals(after.get("hand_complete"))) {
            Object game = response.get("game_state");
            Object combat = game instanceof Map ? ((Map<?,?>)game).get("combat_state") : null;
            if (combat instanceof Map) {
                Map<String,Object> state = (Map<String,Object>)combat;
                state.put("hand", new ArrayList<>()); state.put("hand_complete", false);
                state.put("hand_unavailable_reason", "not_a_stable_play_decision_use_selection_screen_if_present");
            }
        }
    }

    /** No unversioned combat mutation may bypass the decision token, even on a selector screen. */
    public static boolean allowsLegacyCommand(String command) {
        return !inCombat() || "state".equals(command) || "wait".equals(command);
    }
    public static void validate(String decisionId, String expectedMode) {
        Sample sample = refresh();
        if (!expectedMode.equals(sample.mode)) throw new IllegalArgumentException("Wrong combat decision screen");
        GATE.validate(decisionId, sample.mode, sample.key, sample.reason);
    }
    public static void claim(String decisionId, String expectedMode) {
        validate(decisionId, expectedMode);
        Sample sample = sample();
        GATE.claim(decisionId, sample.mode, sample.key, sample.reason);
        communicationmod.CommunicationMod.mustSendGameState = true;
    }

    /** Add only on stable combat hand rows, never on hidden/unordered draw-pile cards. */
    public static void addHandPosition(Map<String,Object> row, AbstractCard card, int index) {
        row.put("hand_index", index);
        Boolean deadOn = deadOnPosition(card);
        if (deadOn != null) {
            row.put("dead_on_position_active", deadOn);
            row.put("dead_on_source", "hermit_isDeadOnPos_ui_predicate");
        }
    }

    private static Boolean deadOnPosition(AbstractCard card) {
        boolean tagged = false;
        for (AbstractCard.CardTags tag : card.tags) if ("DEADON".equals(tag.name())) tagged = true;
        if (!tagged) return null;
        for (Class<?> type = card.getClass(); type != null; type = type.getSuperclass()) {
            if ("hermit.cards.AbstractHermitCard".equals(type.getName())) {
                try { return (Boolean)type.getMethod("isDeadOnPos").invoke(card); }
                catch (ReflectiveOperationException | LinkageError failure) {
                    throw new IllegalStateException("Hermit UI position predicate unavailable", failure);
                }
            }
        }
        return null;
    }

    private static Sample sample() {
        try { return capture(); }
        catch (RuntimeException | LinkageError failure) { failed(failure); return new Sample("none", "", "observation_failed"); }
    }
    private static Sample capture() {
        AbstractRoom current = currentRoom();
        if (player != AbstractDungeon.player || room != current) {
            player = AbstractDungeon.player; room = current; reported = false; GATE.reset();
        }
        if (!inCombat() || player == null) return new Sample("none", "", "not_in_combat");
        GameActionManager manager = AbstractDungeon.actionManager;
        String reason = null;
        if (manager == null) return new Sample("none", "", "missing_action_manager");
        if (player.isDead || player.isDying || current.isBattleOver) reason = "combat_ending";
        if (CardCrawlGame.isPopupOpen || AbstractDungeon.isFadingIn || AbstractDungeon.isFadingOut
            || globalFade != 0 || AbstractDungeon.fadeColor == null || AbstractDungeon.fadeColor.a != 0) reason = "transition_or_popup";
        String screen = AbstractDungeon.screen == null ? "unknown" : AbstractDungeon.screen.name();
        boolean selection = AbstractDungeon.isScreenUp && ("GRID".equals(screen) || "HAND_SELECT".equals(screen));
        String mode = selection ? "selection" : "play";
        if (!selection && (AbstractDungeon.isScreenUp || !"NONE".equals(screen))) reason = "unsupported_screen";
        if (player.isDraggingCard) reason = "manual_card_drag";

        StringBuilder key = new StringBuilder();
        token(key, screen); token(key, Settings.language);
        token(key, GameActionManager.turn); token(key, player.currentHealth); token(key, player.currentBlock);
        token(key, com.megacrit.cardcrawl.ui.panels.EnergyPanel.totalCount);
        token(key, player.stance == null ? null : player.stance.ID);
        powers(key, player.powers);
        if (selection) {
            // These actions intentionally pause the main queue for player input. Do not demand it drains.
            if ("GRID".equals(screen)) {
                cards(key, AbstractDungeon.gridSelectScreen.targetGroup, false);
                cards(key, AbstractDungeon.gridSelectScreen.selectedCards, false);
                token(key, AbstractDungeon.gridSelectScreen.confirmScreenUp);
            } else {
                cards(key, player.hand, false);
                cards(key, AbstractDungeon.handCardSelectScreen.selectedCards, false);
                token(key, AbstractDungeon.handCardSelectScreen.numCardsToSelect);
            }
            token(key, ChoiceScreenUtils.getCurrentChoiceList());
            token(key, ChoiceScreenUtils.isConfirmButtonAvailable());
            token(key, ChoiceScreenUtils.isCancelButtonAvailable());
        } else {
            if (!communicationmod.GameStateListener.isPlayerTurn() || player.endTurnQueued || player.isEndingTurn || manager.turnHasEnded)
                reason = "not_player_turn";
            if (manager.phase != GameActionManager.Phase.WAITING_ON_USER || manager.currentAction != null
                || manager.turnStartCurrentAction != null || manager.usingCard || !manager.actions.isEmpty()
                || !manager.preTurnActions.isEmpty() || !manager.cardQueue.isEmpty() || !manager.monsterQueue.isEmpty()
                || !player.limbo.isEmpty()) reason = "pending_actions";
            if (!cards(key, player.hand, true)) reason = "hand_unreadable_or_moving";
            for (AbstractCard card : player.hand.group) token(key, deadOnPosition(card));
        }
        for (AbstractMonster monster : AbstractDungeon.getMonsters().monsters) {
            token(key, System.identityHashCode(monster)); token(key, monster.id);
            token(key, monster.currentHealth); token(key, monster.currentBlock);
            token(key, monster.isDead); token(key, monster.isDying); token(key, monster.halfDead);
            token(key, monster.intent); powers(key, monster.powers);
        }
        if (!selection && AbstractDungeon.getMonsters().areMonstersBasicallyDead()) reason = "combat_ending";
        if (communicationmod.GameStateListener.isStateUpdateBlocked()) reason = "state_update_blocked";
        return new Sample(mode, key.toString(), reason);
    }

    private static boolean cards(StringBuilder key, CardGroup cards, boolean mustBeSettled) {
        if (cards == null) throw new IllegalArgumentException("Invalid card area");
        return cards(key, cards.group, mustBeSettled);
    }
    private static boolean cards(StringBuilder key, List<AbstractCard> cards, boolean mustBeSettled) {
        if (cards == null || cards.size() > 1000) throw new IllegalArgumentException("Invalid card area");
        Set<UUID> ids = new HashSet<>(); boolean settled = true;
        token(key, cards.size());
        for (AbstractCard card : cards) {
            if (card == null || card.uuid == null || !ids.add(card.uuid)) throw new IllegalArgumentException("Ambiguous card identity");
            token(key, card.uuid); token(key, System.identityHashCode(card));
            token(key, card.costForTurn); token(key, card.damage); token(key, card.block); token(key, card.magicNumber);
            token(key, card.upgraded); token(key, card.isFlipped); token(key, card.isSeen); token(key, card.isLocked);
            token(key, card.freeToPlayOnce); token(key, card.exhaust); token(key, card.retain);
            if (mustBeSettled) {
                settled &= card.isSeen && !card.isLocked && !card.isFlipped
                    && near(card.current_x, card.target_x, .5f) && near(card.current_y, card.target_y, .5f)
                    && near(card.angle, card.targetAngle, .5f) && near(card.drawScale, card.targetDrawScale, .005f);
                token(key, card.target_x); token(key, card.target_y);
            }
        }
        return settled;
    }
    private static boolean near(float current, float target, float tolerance) {
        return !Float.isInfinite(current) && !Float.isInfinite(target) && Math.abs(current - target) <= tolerance;
    }
    private static void powers(StringBuilder key, List<AbstractPower> powers) {
        token(key, powers.size());
        for (AbstractPower power : powers) { token(key, power.ID); token(key, power.amount); }
    }
    private static void token(StringBuilder key, Object value) {
        String text = String.valueOf(value); key.append(text.length()).append(':').append(text);
        if (key.length() > 262144) throw new IllegalArgumentException("Combat observation budget exceeded");
    }
    private static final class Sample {
        final String mode, key, reason;
        Sample(String mode, String key, String reason) { this.mode=mode; this.key=key; this.reason=reason; }
    }
}
