package communicationmod.protocol;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import communicationmod.ChoiceScreenUtils;
import communicationmod.observation.CombatObservation;
import communicationmod.safety.AutomationSafety;
import java.util.*;

/** Versioned screen actions for the future live v2 bridge. Never executes from an observation. */
public final class CombatActions {
    private CombatActions() { }

    public static List<ProtocolSession.Action> offer() {
        if (!AutomationSafety.isAutomationAllowed()) return Collections.emptyList();
        Map<String,Object> view = CombatObservation.observation();
        if (!Boolean.TRUE.equals(view.get("ready"))) return Collections.emptyList();
        String decision = (String)view.get("decision_id"), mode = (String)view.get("mode");
        List<ProtocolSession.Action> result = new ArrayList<>();
        if ("play".equals(mode)) {
            for (AbstractCard card : AbstractDungeon.player.hand.group) {
                if (card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
                    List<AbstractMonster> monsters = AbstractDungeon.getMonsters().monsters;
                    for (int i=0; i<monsters.size(); i++) {
                        AbstractMonster target = monsters.get(i);
                        if (card.canUse(AbstractDungeon.player, target)) result.add(play(decision, card, target, i));
                    }
                } else if (card.canUse(AbstractDungeon.player, null)) result.add(play(decision, card, null, -1));
            }
            result.add(action("end_turn", "End turn", decision, mode, () -> {},
                () -> AbstractDungeon.overlayMenu.endTurnButton.disable(true)));
        } else if ("selection".equals(mode)) {
            List<String> choices = ChoiceScreenUtils.getCurrentChoiceList();
            for (int i=0; i<choices.size(); i++) {
                final int index = i;
                result.add(action("select:"+index, choices.get(i), decision, mode, () -> {
                    if (index >= ChoiceScreenUtils.getCurrentChoiceList().size()) throw new IllegalArgumentException("Selection changed");
                }, () -> ChoiceScreenUtils.executeChoice(index)));
            }
            if (ChoiceScreenUtils.isConfirmButtonAvailable()) result.add(action("confirm_selection",
                ChoiceScreenUtils.getConfirmButtonText(), decision, mode, () -> {
                    if (!ChoiceScreenUtils.isConfirmButtonAvailable()) throw new IllegalArgumentException("Confirmation unavailable");
                }, ChoiceScreenUtils::pressConfirmButton));
            if (ChoiceScreenUtils.isCancelButtonAvailable()) result.add(action("cancel_selection",
                ChoiceScreenUtils.getCancelButtonText(), decision, mode, () -> {
                    if (!ChoiceScreenUtils.isCancelButtonAvailable()) throw new IllegalArgumentException("Cancellation unavailable");
                }, ChoiceScreenUtils::pressCancelButton));
        }
        // canUse/mod hooks must not make an old observation become eligible again.
        CombatObservation.validate(decision, mode);
        return result;
    }

    private static ProtocolSession.Action play(String decision, AbstractCard card, AbstractMonster target, int index) {
        String uuid = card.uuid.toString();
        Runnable validate = () -> {
            if (!card.uuid.toString().equals(uuid) || !AbstractDungeon.player.hand.group.contains(card))
                throw new IllegalArgumentException("Card no longer in the observed hand");
            if (target != null && (index >= AbstractDungeon.getMonsters().monsters.size()
                || AbstractDungeon.getMonsters().monsters.get(index) != target)) throw new IllegalArgumentException("Target changed");
            if (!card.canUse(AbstractDungeon.player, target)) throw new IllegalArgumentException("Card cannot be used on this target");
        };
        return action("play:"+uuid+":"+index, card.name + (target == null ? "" : " -> "+target.name), decision, "play", validate,
            () -> AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, target)));
    }

    private static ProtocolSession.Action action(String id, String label, String decision, String mode,
                                                Runnable validate, Runnable mutation) {
        JsonObject schema = new JsonObject(), properties = new JsonObject(), token = new JsonObject();
        token.addProperty("type", "string"); JsonArray allowed = new JsonArray(); allowed.add(decision); token.add("enum", allowed);
        properties.add("decision_id", token); schema.add("properties", properties);
        schema.addProperty("type", "object"); schema.addProperty("additionalProperties", false);
        JsonArray required = new JsonArray(); required.add("decision_id"); schema.add("required", required);
        return new ProtocolSession.Action(id, label, schema, args -> {
            checkArguments(args, decision);
            CombatObservation.validate(decision, mode); validate.run(); CombatObservation.validate(decision, mode);
        }, args -> {
            checkArguments(args, decision);
            execute(decision, mode, validate, mutation);
        });
    }

    private static void checkArguments(JsonObject args, String decision) {
        JsonElement token = args.get("decision_id");
        if (args.entrySet().size() != 1 || token == null || !token.isJsonPrimitive()
            || !token.getAsJsonPrimitive().isString() || !decision.equals(token.getAsString()))
            throw new IllegalArgumentException("Exact observed decision_id required");
    }

    private static void execute(String decision, String mode, Runnable validate, Runnable mutation) {
        AutomationSafety.requireAutomationAllowed();
        CombatObservation.validate(decision, mode);
        validate.run();
        CombatObservation.claim(decision, mode);
        mutation.run();
    }
}
