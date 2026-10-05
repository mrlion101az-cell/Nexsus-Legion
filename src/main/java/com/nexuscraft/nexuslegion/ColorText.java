package com.nexuscraft.nexuslegion;

/** The '&' colour-code shorthand used in config.yml, turned into real section-sign codes. */
final class ColorText {
    private ColorText() {
    }

    static String translate(String raw) {
        return raw == null ? null : raw.replace('&', '§');
    }
}
