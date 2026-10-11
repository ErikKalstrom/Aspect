package com.aspects.smp.util;

import org.bukkit.util.Vector;

public final class DirectionUtil {
    private DirectionUtil() {}

    public static Vector horizontalFromYaw(float yawDegrees) {
        double yawRadians = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
    }
}
