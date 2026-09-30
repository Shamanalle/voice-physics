package com.kasper.vcdistance;

import java.util.Locale;

/**
 * A loudspeaker an admin placed with {@code /vcd speaker add}: whoever talks within {@code pickup} blocks of
 * it is heard from the speaker by every player within {@code radius} blocks of it. Kept in the server's
 * settings file ({@code speaker.<name>=world|x|y|z|pickup|radius}).
 *
 * @param name   what admins call it (letters, digits, {@code _} and {@code -})
 * @param pickup how close a talker must be to the speaker's position to be picked up, in blocks
 * @param radius how far from the speaker players hear it, in blocks
 */
public record Loudspeaker(String name, String world, double x, double y, double z, double pickup, double radius) {

    public static final double DEFAULT_PICKUP = 3.0;
    public static final double DEFAULT_RADIUS = 48.0;
    public static final double MAX_PICKUP = 16.0;
    public static final double MAX_RADIUS = 256.0;
    public static final int MAX_NAME = 32;

    public Loudspeaker {
        name = name == null ? "" : name.trim();
        world = world == null ? "" : world.trim();
        pickup = Math.max(0.5, Math.min(MAX_PICKUP, pickup));
        radius = Math.max(1.0, Math.min(MAX_RADIUS, radius));
    }

    /** The key of the name: case does not matter. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Whether {@code name} can name a speaker. */
    public static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME && name.matches("[A-Za-z0-9_-]+");
    }

    /** Whether {@code player} stands close enough to the speaker to be picked up. */
    public boolean picksUp(ServerPlayers.Info player) {
        return inWorld(player) && dist(player) <= pickup;
    }

    /** Whether {@code player} is close enough to hear the speaker. */
    public boolean reaches(ServerPlayers.Info player) {
        return inWorld(player) && dist(player) <= radius;
    }

    private boolean inWorld(ServerPlayers.Info p) {
        return p.world() != null && Zone.sameWorld(world, p.world());
    }

    private double dist(ServerPlayers.Info p) {
        double dx = p.x() - x;
        double dy = p.y() - y;
        double dz = p.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** "world|x|y|z|pickup|radius" for the settings file. */
    String encode() {
        return world.replace("|", "") + "|" + ConfigWriter.number(x) + "|" + ConfigWriter.number(y) + "|" + ConfigWriter.number(z)
                + "|" + ConfigWriter.number(pickup) + "|" + ConfigWriter.number(radius);
    }

    /** @return the speaker, or {@code null} when the value or the name is damaged */
    static Loudspeaker decode(String name, String text) {
        if (!validName(name) || text == null) {
            return null;
        }
        String[] p = text.split("\\|", -1);
        if (p.length < 4 || p[0].isBlank()) {
            return null;
        }
        try {
            return new Loudspeaker(name, p[0], Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()),
                    Double.parseDouble(p[3].trim()),
                    p.length > 4 ? Double.parseDouble(p[4].trim()) : DEFAULT_PICKUP,
                    p.length > 5 ? Double.parseDouble(p[5].trim()) : DEFAULT_RADIUS);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
