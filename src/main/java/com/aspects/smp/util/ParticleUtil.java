package com.aspects.smp.util;

import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.logging.Logger;

public final class ParticleUtil {
    private ParticleUtil() {}

    public static void spawn(Location location, String particleName, int count, Logger logger) {
        if (particleName == null || particleName.isBlank()) {
            return;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown particle '" + particleName + "' in configuration, skipping.");
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, 0.5, 0.5, 0.5, 0.01);
    }
}
