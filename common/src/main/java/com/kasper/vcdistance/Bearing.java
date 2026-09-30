package com.kasper.vcdistance;

/**
 * Direction of a sound relative to where the listener looks, in Minecraft's yaw convention
 * (yaw 0 faces +Z, turning right increases it).
 */
public final class Bearing {

    /** Arrows for 8 sectors, clockwise from straight ahead. */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private Bearing() {
    }

    /**
     * @param dx  source x minus listener x
     * @param dz  source z minus listener z
     * @param yaw listener yaw in degrees
     * @return degrees from straight ahead, -180 - 180; positive is to the right
     */
    public static double relative(double dx, double dz, double yaw) {
        if (dx == 0.0 && dz == 0.0) {
            return 0.0;
        }
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        return wrap(target - yaw);
    }

    /** Wraps an angle to -180 - 180 degrees. */
    public static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    /** One of 8 arrows pointing towards the source, or an empty string when the direction is unknown. */
    public static String arrow(double relative) {
        if (Double.isNaN(relative)) {
            return "";
        }
        int sector = (int) Math.floor((wrap(relative) + 360.0 + 22.5) / 45.0) % 8;
        return ARROWS[sector];
    }

    /**
     * A subtitle-style line for a voice: {@code ◀ Anna 6m} to the left, {@code Bob 6m ▶} to the right,
     * the plain {@code Sam 3m} ahead (within 25 degrees), {@code Sam 3m ▼} behind (beyond 155).
     *
     * @param distance in blocks, negative when unknown
     */
    public static String marker(String name, double relative, double distance) {
        String text = distance >= 0.0 ? name + " " + Math.round(distance) + "m" : name;
        if (Double.isNaN(relative)) {
            return text;
        }
        double wrapped = wrap(relative);
        double abs = Math.abs(wrapped);
        if (abs <= 25.0) {
            return text;
        }
        if (abs >= 155.0) {
            return text + " ▼";
        }
        return wrapped < 0.0 ? "◀ " + text : text + " ▶";
    }
}
