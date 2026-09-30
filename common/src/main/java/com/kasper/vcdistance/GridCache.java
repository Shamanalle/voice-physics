package com.kasper.vcdistance;

/**
 * The blocks read during one tick for the searches round walls, shared by every search of that tick so
 * each block is read from the world once. Plain arrays, kept between ticks and emptied in no time by a
 * new generation number, so a busy tick makes no garbage.
 */
public final class GridCache implements SoundPath.Grid {

    private static final int START = 1 << 12;
    private static final int LARGEST = 1 << 20;

    private SoundPath.Grid world = (x, y, z) -> true;
    private long[] keys = new long[START];
    private boolean[] open = new boolean[START];
    private int[] stamps = new int[START];
    private int generation = 1;
    private int size;
    private long reads;

    /** Starts a new tick: forgets every block and reads from {@code world} from now on. */
    public void newTick(SoundPath.Grid world) {
        this.world = world;
        size = 0;
        if (++generation == Integer.MAX_VALUE) {
            generation = 1;
            java.util.Arrays.fill(stamps, 0);
        }
    }

    @Override
    public boolean open(int x, int y, int z) {
        long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFFL);
        int mask = keys.length - 1;
        int i = hash(key) & mask;
        while (stamps[i] == generation) {
            if (keys[i] == key) {
                return open[i];
            }
            i = (i + 1) & mask;
        }
        boolean value = world.open(x, y, z);
        reads++;
        if (size * 2 >= keys.length) {
            if (keys.length >= LARGEST) {
                // Full for this tick: answer without keeping it
                return value;
            }
            grow();
            i = hash(key) & (keys.length - 1);
            while (stamps[i] == generation) {
                i = (i + 1) & (keys.length - 1);
            }
        }
        keys[i] = key;
        open[i] = value;
        stamps[i] = generation;
        size++;
        return value;
    }

    /** Blocks read from the world since this cache was made (for tests and the load readout). */
    public long reads() {
        return reads;
    }

    /** Blocks kept this tick. */
    public int size() {
        return size;
    }

    private void grow() {
        long[] oldKeys = keys;
        boolean[] oldOpen = open;
        int[] oldStamps = stamps;
        int capacity = keys.length * 2;
        keys = new long[capacity];
        open = new boolean[capacity];
        stamps = new int[capacity];
        int mask = capacity - 1;
        for (int j = 0; j < oldKeys.length; j++) {
            if (oldStamps[j] == generation) {
                int i = hash(oldKeys[j]) & mask;
                while (stamps[i] == generation) {
                    i = (i + 1) & mask;
                }
                keys[i] = oldKeys[j];
                open[i] = oldOpen[j];
                stamps[i] = generation;
            }
        }
    }

    private static int hash(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }
}
