package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Sound zone: ..." above the hotbar when a player enters or leaves a zone. It is sent by the server
 * as an action-bar line, so every player sees it, with or without the addon.
 */
public final class ZoneNotices {

    private final Map<UUID, Zone> current = new ConcurrentHashMap<>();

    /**
     * Records the zone the player is in now.
     *
     * @return the line to show above their hotbar, or {@code null} when nothing changed (or notices are off)
     */
    public String update(ServerPlayers.Info player, Zone zone, ServerSettings settings) {
        UUID id = player.id();
        Zone before = zone == null ? current.remove(id) : current.put(id, zone);
        String was = before == null ? null : before.key();
        String now = zone == null ? null : zone.key();
        if (java.util.Objects.equals(was, now) || !settings.isZoneNotices()) {
            return null;
        }
        String language = settings.languageFor(player.language());
        if (zone != null) {
            String message = zone.rules().enterMessage();
            return message != null ? message : ServerText.get(language, "notice.zone_enter", zone.name());
        }
        return ServerText.get(language, "notice.zone_left", before.name());
    }

    public void forget(UUID player) {
        current.remove(player);
    }

    public void clear() {
        current.clear();
    }
}
