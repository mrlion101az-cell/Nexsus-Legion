package com.nexuscraft.nexuslegion;

/** The fighting stats of one kind of enlistable mob. Plain data, parsed from config.yml. */
record SoldierType(String entityType, String displayName, double damage, double speed, double scale,
                   double health, int cost) {

    /** The built-in roster, used for any value config.yml does not override. */
    static java.util.List<SoldierType> defaults() {
        return java.util.List.of(
                new SoldierType("RABBIT", "Bunny", 2.0, 1.5, 0.45, 6.0, 2),
                new SoldierType("CHICKEN", "Chicken", 1.5, 1.3, 0.5, 6.0, 2),
                new SoldierType("CAT", "Cat", 2.5, 1.5, 0.5, 10.0, 4),
                new SoldierType("FOX", "Fox", 3.0, 1.5, 0.5, 10.0, 4),
                new SoldierType("PIG", "Piglet", 2.0, 1.2, 0.45, 10.0, 3),
                new SoldierType("SHEEP", "Lamb", 2.0, 1.2, 0.45, 8.0, 3),
                new SoldierType("COW", "Calf", 3.0, 1.1, 0.4, 10.0, 4),
                new SoldierType("GOAT", "Kid", 3.5, 1.4, 0.45, 10.0, 6),
                new SoldierType("FROG", "Froglet", 2.0, 1.4, 0.5, 8.0, 3),
                new SoldierType("TURTLE", "Turtle", 1.5, 1.1, 0.5, 15.0, 3));
    }
}
