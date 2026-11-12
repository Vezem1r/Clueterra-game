package io.clueterra.questions.clients.fastfood.dto;

import java.util.List;
import java.util.Map;

public final class FastFoodConstants {

    private FastFoodConstants() {}

    public static final int TOTAL_TRACKED = 15;
    public static final int MAX_SIGNATURE_ITEMS = 4;
    public static final int MAX_CULTURAL_FACTS = 2;

    public static final List<String> TRACKED_CHAINS = List.of(
            "mcdonalds", "starbucks", "kfc", "burger_king", "subway",
            "pizza_hut", "dominos", "taco_bell", "wendys", "popeyes",
            "chick_fil_a", "dunkin", "papa_johns", "five_guys", "chipotle"
    );

    public static final Map<String, String> CHAIN_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("mcdonalds", "McDonald's"),
            Map.entry("starbucks", "Starbucks"),
            Map.entry("kfc", "KFC"),
            Map.entry("burger_king", "Burger King"),
            Map.entry("subway", "Subway"),
            Map.entry("pizza_hut", "Pizza Hut"),
            Map.entry("dominos", "Domino's"),
            Map.entry("taco_bell", "Taco Bell"),
            Map.entry("wendys", "Wendy's"),
            Map.entry("popeyes", "Popeyes"),
            Map.entry("chick_fil_a", "Chick-fil-A"),
            Map.entry("dunkin", "Dunkin'"),
            Map.entry("papa_johns", "Papa John's"),
            Map.entry("five_guys", "Five Guys"),
            Map.entry("chipotle", "Chipotle")
    );

    public static final Map<String, String> CHAIN_TO_INDICATOR = Map.ofEntries(
            Map.entry("mcdonalds", "FF_MCDONALDS_COUNT"),
            Map.entry("starbucks", "FF_STARBUCKS_COUNT"),
            Map.entry("kfc", "FF_KFC_COUNT"),
            Map.entry("burger_king", "FF_BURGER_KING_COUNT"),
            Map.entry("subway", "FF_SUBWAY_COUNT"),
            Map.entry("pizza_hut", "FF_PIZZA_HUT_COUNT"),
            Map.entry("dominos", "FF_DOMINOS_COUNT")
    );

    public static final Map<String, String> NOTABLE_ABSENT_REASONS = Map.ofEntries(
            Map.entry("mcdonalds", "market conditions or political factors keep the golden arches out"),
            Map.entry("starbucks", "local coffee culture proved too strong to crack"),
            Map.entry("kfc", "the fried chicken giant hasn't expanded here yet"),
            Map.entry("burger_king", "the flame-grilled chain skipped this market"),
            Map.entry("subway", "the sandwich chain has no presence here"),
            Map.entry("taco_bell", "Mexican-inspired fast food hasn't caught on here"),
            Map.entry("chick_fil_a", "the chicken chain remains US-centric"),
            Map.entry("five_guys", "the burger chain hasn't ventured this far"),
            Map.entry("chipotle", "the burrito chain stays close to home")
    );
}
