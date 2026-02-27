package com.nightfall.breach;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-mob memory of attempted breach block positions and their cooldown expiry.
 */
public class MobMemory {

    // blockKey -> expiry timestamp
    private final Map<Long, Long> failedBlocks = new HashMap<>();

    private int breaksThisNight = 0;
    private int breaksThisChase = 0;
    private boolean currentlyBreaching = false;

    // Stuck detection: snapshot of mob position, updated each stuckCheckTick
    private Location lastTrackedLocation = null;
    private int stuckTickCount = 0;

    public static long blockKey(Block block) {
        // Pack block coords into a long. Works for coords within +/-32768.
        long x = (long) (block.getX() + 32768) & 0xFFFFL;
        long y = (long) (block.getY() + 2048) & 0xFFFL;
        long z = (long) (block.getZ() + 32768) & 0xFFFFL;
        return (x << 28) | (y << 16) | z;
    }

    public void recordFailed(Block block, long cooldownMillis) {
        failedBlocks.put(blockKey(block), System.currentTimeMillis() + cooldownMillis);
    }

    public boolean isFailed(Block block) {
        Long expiry = failedBlocks.get(blockKey(block));
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            failedBlocks.remove(blockKey(block));
            return false;
        }
        return true;
    }

    public void pruneFailed() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, Long>> it = failedBlocks.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue()) it.remove();
        }
    }

    public void recordBreak() {
        breaksThisNight++;
        breaksThisChase++;
    }

    /** Torch-hunt breaks count toward the nightly cap but not the per-chase cap. */
    public void recordTorchBreak() {
        breaksThisNight++;
    }

    /**
     * Call each stuckCheckTick with the mob's current location.
     * Returns the updated stuck tick count (incremented if mob hasn't moved > 0.5 blocks).
     */
    public int updateStuck(Location currentLoc) {
        if (lastTrackedLocation == null
                || !lastTrackedLocation.getWorld().equals(currentLoc.getWorld())
                || lastTrackedLocation.distanceSquared(currentLoc) > 0.25) {
            lastTrackedLocation = currentLoc.clone();
            stuckTickCount = 0;
        } else {
            stuckTickCount += 10; // stuckCheckTick fires every 10 ticks
        }
        return stuckTickCount;
    }

    public void resetStuck() {
        stuckTickCount = 0;
        lastTrackedLocation = null;
    }

    public void resetNight() {
        breaksThisNight = 0;
        breaksThisChase = 0;
        failedBlocks.clear();
        currentlyBreaching = false;
        stuckTickCount = 0;
        lastTrackedLocation = null;
    }

    public void resetChase() {
        breaksThisChase = 0;
        currentlyBreaching = false;
        stuckTickCount = 0;
    }

    public int getBreaksThisNight() { return breaksThisNight; }
    public int getBreaksThisChase() { return breaksThisChase; }
    public boolean isCurrentlyBreaching() { return currentlyBreaching; }
    public void setCurrentlyBreaching(boolean v) { currentlyBreaching = v; }
    public int getStuckTickCount() { return stuckTickCount; }
}
