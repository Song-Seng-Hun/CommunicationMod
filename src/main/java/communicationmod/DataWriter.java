package communicationmod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

public class DataWriter implements Runnable {

    private static final int TARGET_MESSAGE_BYTES = 4000;
    private static final Gson gson = new Gson();

    private final BlockingQueue<String> queue;
    private final OutputStream stream;
    private boolean verbose;
    private static final Logger logger = LogManager.getLogger(DataWriter.class.getName());

    public DataWriter(BlockingQueue<String> queue, OutputStream stream, boolean verbose) {
        this.queue = queue;
        this.stream = stream;
        this.verbose = verbose;
    }

    public void run() {
        String message = "";
        while (!Thread.currentThread().isInterrupted()) {
            try {
                message = compactChoiceState(this.queue.take());
                if (verbose) {
                    logger.info("Sending message: " + message);
                }
                stream.write(message.getBytes(StandardCharsets.UTF_8));
                stream.write('\n');
                stream.flush();
            } catch (InterruptedException e) {
                logger.info("Communications writing thread interrupted.");
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                logger.error("Transport output failed; writing stopped.", e);
                return;
            }
        }
    }

    /**
     * Card-heavy decision screens can easily exceed tool output limits because the normal game state
     * repeats the full deck/map as well as the cards currently being offered. Keep the normal protocol
     * everywhere else, but make GRID and SHOP_SCREEN states action-oriented and small enough for tool
     * clients to receive inline.
     */
    private static String compactChoiceState(String message) {
        try {
            JsonElement parsed = new JsonParser().parse(message);
            if (!parsed.isJsonObject()) {
                return message;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("game_state") || !root.get("game_state").isJsonObject()) {
                return message;
            }

            JsonObject gameState = root.getAsJsonObject("game_state");
            String screenType = getString(gameState, "screen_type");
            if (!"GRID".equals(screenType) && !"SHOP_SCREEN".equals(screenType)) {
                return message;
            }

            JsonObject screenState = gameState.has("screen_state") && gameState.get("screen_state").isJsonObject()
                    ? gameState.getAsJsonObject("screen_state")
                    : new JsonObject();

            if ("GRID".equals(screenType)) {
                compactGridState(gameState, screenState);
            } else {
                compactShopState(gameState, screenState);
            }

            gameState.remove("map");
            compactRelics(gameState);
            compactPotions(gameState);
            gameState.addProperty("compact_state", true);

            String compact = gson.toJson(root);

            if (utf8Length(compact) > TARGET_MESSAGE_BYTES && gameState.has("deck")) {
                gameState.remove("deck");
                gameState.addProperty("context_truncated", true);
                compact = gson.toJson(root);
            }

            if (utf8Length(compact) > TARGET_MESSAGE_BYTES && gameState.has("choice_list")) {
                gameState.remove("choice_list");
                gameState.addProperty("context_truncated", true);
                compact = gson.toJson(root);
            }

            if (utf8Length(compact) > TARGET_MESSAGE_BYTES && "GRID".equals(screenType)) {
                makeGridChoicesTerse(screenState);
                gameState.addProperty("context_truncated", true);
                compact = gson.toJson(root);
            }

            return compact;
        } catch (RuntimeException e) {
            logger.warn("Could not compact game state; sending original message.", e);
            return message;
        }
    }

    private static void compactGridState(JsonObject gameState, JsonObject screenState) {
        boolean confirmUp = getBoolean(screenState, "confirm_up");
        boolean deckIsSelectionTarget = getBoolean(screenState, "for_upgrade")
                || getBoolean(screenState, "for_transform")
                || getBoolean(screenState, "for_purge");

        if (screenState.has("cards") && screenState.get("cards").isJsonArray()) {
            JsonArray cards = new JsonArray();
            int choiceIndex = 0;
            for (JsonElement element : screenState.getAsJsonArray("cards")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject card = compactCard(element.getAsJsonObject());
                if (!confirmUp) {
                    card.addProperty("choice_index", choiceIndex++);
                }
                cards.add(card);
            }
            screenState.add("cards", cards);
        }

        if (screenState.has("selected_cards") && screenState.get("selected_cards").isJsonArray()) {
            JsonArray selected = new JsonArray();
            for (JsonElement element : screenState.getAsJsonArray("selected_cards")) {
                if (element.isJsonObject()) {
                    selected.add(compactCard(element.getAsJsonObject()));
                }
            }
            screenState.add("selected_cards", selected);
        }

        if (deckIsSelectionTarget) {
            gameState.remove("deck");
        } else {
            compactDeck(gameState);
        }
    }

    private static void compactShopState(JsonObject gameState, JsonObject screenState) {
        JsonArray choices = new JsonArray();
        int choiceIndex = 0;
        int gold = getInt(gameState, "gold", 0);
        int purgeCost = getInt(screenState, "purge_cost", Integer.MAX_VALUE);

        if (getBoolean(screenState, "purge_available") && gold >= purgeCost) {
            JsonObject purge = new JsonObject();
            purge.addProperty("choice_index", choiceIndex++);
            purge.addProperty("type", "purge");
            purge.addProperty("name", "purge");
            purge.addProperty("price", purgeCost);
            choices.add(purge);
        }

        choiceIndex = appendAffordableShopItems(choices, screenState, "cards", "card", gold, choiceIndex);
        choiceIndex = appendAffordableShopItems(choices, screenState, "relics", "relic", gold, choiceIndex);
        appendAffordableShopItems(choices, screenState, "potions", "potion", gold, choiceIndex);

        screenState.remove("cards");
        screenState.remove("relics");
        screenState.remove("potions");
        screenState.add("choices", choices);

        compactDeck(gameState);
    }

    private static int appendAffordableShopItems(JsonArray choices, JsonObject screenState, String key,
                                                  String type, int gold, int choiceIndex) {
        if (!screenState.has(key) || !screenState.get(key).isJsonArray()) {
            return choiceIndex;
        }

        for (JsonElement element : screenState.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            int price = getInt(item, "price", Integer.MAX_VALUE);
            if (price > gold) {
                continue;
            }

            JsonObject choice = new JsonObject();
            choice.addProperty("choice_index", choiceIndex++);
            choice.addProperty("type", type);
            copyIfPresent(item, choice, "name");
            copyIfPresent(item, choice, "id");
            copyIfPresent(item, choice, "upgrades");
            copyIfPresent(item, choice, "misc");
            choice.addProperty("price", price);
            choices.add(choice);
        }
        return choiceIndex;
    }

    private static JsonObject compactCard(JsonObject card) {
        JsonObject compact = new JsonObject();
        copyIfPresent(card, compact, "name");
        copyIfPresent(card, compact, "id");
        copyIfPresent(card, compact, "upgrades");
        copyIfPresent(card, compact, "misc");
        return compact;
    }

    private static void compactDeck(JsonObject gameState) {
        if (!gameState.has("deck") || !gameState.get("deck").isJsonArray()) {
            return;
        }
        JsonArray deck = new JsonArray();
        for (JsonElement element : gameState.getAsJsonArray("deck")) {
            if (element.isJsonObject()) {
                deck.add(compactCard(element.getAsJsonObject()));
            }
        }
        gameState.add("deck", deck);
    }

    private static void compactRelics(JsonObject gameState) {
        if (!gameState.has("relics") || !gameState.get("relics").isJsonArray()) {
            return;
        }
        JsonArray relics = new JsonArray();
        for (JsonElement element : gameState.getAsJsonArray("relics")) {
            if (element.isJsonObject()) {
                JsonObject relic = element.getAsJsonObject();
                if (relic.has("name")) {
                    relics.add(relic.get("name"));
                }
            }
        }
        gameState.add("relics", relics);
    }

    private static void compactPotions(JsonObject gameState) {
        if (!gameState.has("potions") || !gameState.get("potions").isJsonArray()) {
            return;
        }
        JsonArray potions = new JsonArray();
        for (JsonElement element : gameState.getAsJsonArray("potions")) {
            if (element.isJsonObject()) {
                JsonObject potion = element.getAsJsonObject();
                if (potion.has("name")) {
                    potions.add(potion.get("name"));
                }
            }
        }
        gameState.add("potions", potions);
    }

    private static void makeGridChoicesTerse(JsonObject screenState) {
        if (!screenState.has("cards") || !screenState.get("cards").isJsonArray()) {
            return;
        }
        JsonArray terse = new JsonArray();
        int index = 0;
        for (JsonElement element : screenState.getAsJsonArray("cards")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject card = element.getAsJsonObject();
            String name = getString(card, "name");
            int upgrades = getInt(card, "upgrades", 0);
            String suffix = upgrades > 0 ? "+" + upgrades : "";
            terse.add(index++ + ":" + name + suffix);
        }
        screenState.add("cards", terse);
    }

    private static void copyIfPresent(JsonObject from, JsonObject to, String key) {
        if (from.has(key) && !from.get(key).isJsonNull()) {
            to.add(key, from.get(key));
        }
    }

    private static String getString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
