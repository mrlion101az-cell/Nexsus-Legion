package com.nexuscraft.nexuslegion;

/** Built-in protected zones: whole worlds, and a radius around the spawn of listed worlds. Other
 *  plugins can add their own protection by cancelling {@link SoldierAttackEvent}. */
final class ProtectedZones {

    private ProtectedZones() {
    }

    static boolean isProtected(LegionConfig cfg, String worldName, double x, double z, double spawnX, double spawnZ) {
        for (String w : cfg.protectedWorlds) {
            if (w.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        if (cfg.spawnProtectRadius > 0) {
            for (String w : cfg.spawnProtectWorlds) {
                if (w.equalsIgnoreCase(worldName)) {
                    double dx = x - spawnX;
                    double dz = z - spawnZ;
                    if (dx * dx + dz * dz <= (double) cfg.spawnProtectRadius * cfg.spawnProtectRadius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
