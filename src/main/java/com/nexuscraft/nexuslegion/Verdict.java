package com.nexuscraft.nexuslegion;

/** The answer to "may this soldier (belonging to this owner) attack that target?". */
enum Verdict {
    OK(null),
    SELF("That is your own army or you."),
    TRUSTED("That player is trusted by you."),
    KIND("Armies cannot attack that kind of target."),
    PROTECTED("That target is standing in a protected zone."),
    GAMEMODE("That player cannot be attacked right now."),
    VANISHED("That target is not attackable."),
    INVULNERABLE("That target is invulnerable.");

    final String reason;

    Verdict(String reason) {
        this.reason = reason;
    }
}
