package com.aspects.smp.item;

import java.util.List;

public final class RerollerItem {
    private RerollerItem() {}

    public static final String DISPLAY_NAME = "<gradient:dark_purple:aqua>✦ Aspect Reroller</gradient>";

    public static final List<String> LORE = List.of(
            "<gray>A fragment of unstable elemental energy.</gray>",
            "<gray>Right-click to abandon your current Aspect.</gray>",
            "<gray>A new random Aspect will replace it.</gray>"
    );
}
