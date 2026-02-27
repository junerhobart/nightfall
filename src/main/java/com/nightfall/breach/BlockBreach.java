package com.nightfall.breach;

import org.bukkit.block.Block;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared breach state for a single block. Multiple mobs can participate,
 * each contributing one tick of progress per server tick when adjacent.
 */
public class BlockBreach {

    private final Block block;
    private final int totalTicks;
    private int elapsedTicks = 0;
    private final LinkedHashSet<UUID> participants = new LinkedHashSet<>();
    private final boolean torchHunt;

    public BlockBreach(Block block, int totalTicks, UUID firstMob, boolean torchHunt) {
        this.block = block;
        this.totalTicks = totalTicks;
        this.torchHunt = torchHunt;
        participants.add(firstMob);
    }

    /** Advance one tick. Returns true when the block is done. */
    public boolean tick() {
        elapsedTicks++;
        return elapsedTicks >= totalTicks;
    }

    public float getProgress() {
        return Math.min(1f, (float) elapsedTicks / totalTicks);
    }

    public void addParticipant(UUID id) { participants.add(id); }
    public void removeParticipant(UUID id) { participants.remove(id); }
    public boolean isEmpty() { return participants.isEmpty(); }
    public Set<UUID> getParticipants() { return Collections.unmodifiableSet(participants); }

    public Block getBlock() { return block; }
    public int getElapsedTicks() { return elapsedTicks; }
    public boolean isTorchHunt() { return torchHunt; }
}
