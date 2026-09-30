package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Doorway sound for players without the addon: when the way round a wall is worth taking. */
public class DoorwayTest {

    /** A wall at x = 4 over z -10..10 and y -5..5, with a doorway two blocks tall at z = 5. */
    private static SoundPath.Grid wallWithDoorway() {
        Set<String> solid = new HashSet<>();
        for (int y = -5; y <= 5; y++) {
            for (int z = -10; z <= 10; z++) {
                solid.add(4 + "," + y + "," + z);
            }
        }
        solid.remove("4,0,5");
        solid.remove("4,1,5");
        return (x, y, z) -> !solid.contains(x + "," + y + "," + z);
    }

    @Test
    @DisplayName("A doorway close to the line is taken instead of the wall; a thin wall or a bad way round is not")
    void choose() {
        SoundPath.Result r = SoundPath.find(wallWithDoorway(), 0.5, 0.5, 0.5, 8.5, 0.5, 0.5,
                ServerDoorway.limit(8.0, 48.0), ServerDoorway.MAX_NODES);
        assertNotNull(r, "the way through the doorway is found within the limits");
        assertTrue(r.corners() > 0);

        assertSame(r, ServerDoorway.choose(3.0, r), "behind a thick wall the doorway is better");
        assertNull(ServerDoorway.choose(0.2, r), "a wall this thin is not worth a detour");
        assertNull(ServerDoorway.choose(Double.NaN, r), "unmeasured walls decide nothing");
        assertNull(ServerDoorway.choose(3.0, null), "no way round");
        assertNull(ServerDoorway.choose(r.thickness() / ServerDoorway.BETTER_THAN - 0.01, r),
                "a way round that muffles almost as much as the wall stays on the straight path");

        assertTrue(ServerDoorway.worthLooking(1.0));
        assertFalse(ServerDoorway.worthLooking(0.1));
        assertFalse(ServerDoorway.worthLooking(Double.NaN));
    }

    @Test
    @DisplayName("The search is bounded: short for near voices, never beyond 48 blocks")
    void limit() {
        assertEquals(28.0, ServerDoorway.limit(8.0, 48.0));
        assertEquals(48.0, ServerDoorway.limit(100.0, 200.0));
        assertEquals(14.0, ServerDoorway.limit(1.0, 4.0), "twice the distance and 12 more, however far the voice carries");
        assertNull(SoundPath.find(wallWithDoorway(), 0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 10.0, ServerDoorway.MAX_NODES),
                "a doorway beyond the limit is not found");
    }
}
