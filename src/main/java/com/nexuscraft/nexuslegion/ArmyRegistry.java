package com.nexuscraft.nexuslegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who owns which soldier. Pure data (no server types) so it can be tested, with a flat
 *  snapshot form for saving. The soldier entity's own data tag says who owns it; this registry is
 *  what makes counting (caps), looking up and saving fast. */
final class ArmyRegistry {

    private final Map<UUID, Army> armies = new HashMap<>();
    private final Map<UUID, UUID> ownerBySoldier = new HashMap<>();
    private boolean dirty;

    Army army(UUID owner) {
        return armies.computeIfAbsent(owner, k -> new Army());
    }

    boolean hasArmy(UUID owner) {
        Army a = armies.get(owner);
        return a != null && !a.soldiers.isEmpty();
    }

    List<UUID> owners() {
        return new ArrayList<>(armies.keySet());
    }

    int count(UUID owner) {
        Army a = armies.get(owner);
        return a == null ? 0 : a.soldiers.size();
    }

    int total() {
        return ownerBySoldier.size();
    }

    boolean add(UUID owner, UUID soldier) {
        UUID previous = ownerBySoldier.get(soldier);
        if (previous != null && !previous.equals(owner)) {
            armies.get(previous).soldiers.remove(soldier);
        }
        ownerBySoldier.put(soldier, owner);
        boolean added = army(owner).soldiers.add(soldier);
        dirty |= added || previous != null;
        return added;
    }

    /** Removes a soldier wherever it is registered. Returns its former owner, or null. */
    UUID remove(UUID soldier) {
        UUID owner = ownerBySoldier.remove(soldier);
        if (owner != null) {
            Army a = armies.get(owner);
            if (a != null) {
                a.soldiers.remove(soldier);
            }
            dirty = true;
        }
        return owner;
    }

    UUID ownerOf(UUID soldier) {
        return ownerBySoldier.get(soldier);
    }

    /** Disbanding: forget every soldier and bump the epoch. Returns the soldiers that were registered. */
    List<UUID> disband(UUID owner) {
        Army a = army(owner);
        List<UUID> gone = new ArrayList<>(a.soldiers);
        for (UUID s : gone) {
            ownerBySoldier.remove(s);
        }
        a.soldiers.clear();
        a.epoch++;
        dirty = true;
        return gone;
    }

    boolean trust(UUID owner, UUID other) {
        boolean changed = army(owner).trusted.add(other);
        dirty |= changed;
        return changed;
    }

    boolean untrust(UUID owner, UUID other) {
        Army a = armies.get(owner);
        boolean changed = a != null && a.trusted.remove(other);
        dirty |= changed;
        return changed;
    }

    boolean isTrusted(UUID owner, UUID other) {
        Army a = armies.get(owner);
        return a != null && a.trusted.contains(other);
    }

    void setStance(UUID owner, Stance stance) {
        Army a = army(owner);
        if (a.stance != stance) {
            a.stance = stance;
            dirty = true;
        }
    }

    boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------ save / load

    /** owner -> flat record, for writing to disk. */
    Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map.Entry<UUID, Army> e : armies.entrySet()) {
            Army a = e.getValue();
            if (a.soldiers.isEmpty() && a.trusted.isEmpty() && a.epoch == 0 && a.stance == Stance.FOLLOW) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("soldiers", strings(a.soldiers));
            m.put("trusted", strings(a.trusted));
            m.put("stance", a.stance.name());
            m.put("epoch", a.epoch);
            out.put(e.getKey().toString(), m);
        }
        return out;
    }

    void restore(Map<String, Map<String, Object>> data) {
        armies.clear();
        ownerBySoldier.clear();
        for (Map.Entry<String, Map<String, Object>> e : data.entrySet()) {
            UUID owner;
            try {
                owner = UUID.fromString(e.getKey());
            } catch (IllegalArgumentException bad) {
                continue;
            }
            Army a = army(owner);
            Map<String, Object> m = e.getValue();
            for (UUID s : uuids(m.get("soldiers"))) {
                a.soldiers.add(s);
                ownerBySoldier.put(s, owner);
            }
            a.trusted.addAll(uuids(m.get("trusted")));
            a.stance = Stance.parse(String.valueOf(m.get("stance")), Stance.FOLLOW);
            Object ep = m.get("epoch");
            a.epoch = ep instanceof Number n ? n.intValue() : 0;
        }
        dirty = false;
    }

    private static List<String> strings(Iterable<UUID> ids) {
        List<String> out = new ArrayList<>();
        for (UUID id : ids) {
            out.add(id.toString());
        }
        return out;
    }

    private static List<UUID> uuids(Object raw) {
        List<UUID> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try {
                    out.add(UUID.fromString(String.valueOf(o)));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited bad id is skipped, not fatal
                }
            }
        }
        return out;
    }
}
