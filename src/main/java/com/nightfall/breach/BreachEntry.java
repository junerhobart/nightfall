package com.nightfall.breach;

import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

/**
 * Represents an active breach attempt by one mob against one block.
 */
public class BreachEntry {

    private final Mob mob;
    private final Block target;
    private final int totalTicks;
    private int elapsedTicks = 0;

    public BreachEntry(Mob mob, Block target, int totalTicks) {
        this.mob = mob;
        this.target = target;
        this.totalTicks = totalTicks;
    }

    /** Advance by one tick. Returns true when the break is complete. */
    public boolean tick() {
        elapsedTicks++;
        return elapsedTicks >= totalTicks;
    }

    public float getProgress() {
        return Math.min(1.0f, (float) elapsedTicks / totalTicks);
    }

    public Mob getMob() { return mob; }
    public Block getTarget() { return target; }
    public int getElapsedTicks() { return elapsedTicks; }
}
