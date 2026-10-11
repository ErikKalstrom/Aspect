package com.aspects.smp.item;

import java.util.List;

public final class AspectSelectorItem {
    private AspectSelectorItem() {}

    public static final String TOKEN_DISPLAY_NAME = "<gradient:dark_purple:aqua>✦ Aspect Selection Token</gradient>";

    public static List<String> tokenLore(String targetAspectDisplayName) {
        return List.of(
                "<gray>A token imbued with a specific elemental power.</gray>",
                "<gray>Right-click to become " + targetAspectDisplayName + ".</gray>",
                "<gray>This will replace your current Aspect.</gray>"
        );
    }

    public static final String SELECTOR_DISPLAY_NAME = "<gradient:dark_purple:aqua>✦ Aspect Selector</gradient>";

    public static final List<String> SELECTOR_LORE = List.of(
            "<gray>A compass that points toward your true power.</gray>",
            "<gray>Right-click to choose a new Aspect.</gray>",
            "<gray>This will replace your current Aspect.</gray>"
    );
}
