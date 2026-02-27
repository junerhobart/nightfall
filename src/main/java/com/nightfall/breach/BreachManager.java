package com.nightfall.breach;

import com.nightfall.config.NightfallConfig;
import com.nightfall.heat.HeatManager;
import com.nightfall.main.NightfallPlugin;
import com.nightfall.ward.WardManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BreachManager {

    private final NightfallPlugin plugin;

    // Active breaches: mob UUID -> entry
    private final Map<UUID, BreachEntry> activeBreaches = new ConcurrentHashMap<>();

    // Per-mob memory
    private final Map<UUID, MobMemory> mobMemories = new ConcurrentHashMap<>();

    // Per-chunk break count this night: chunkKey -> count
    private final Map<Long, Integer> chunkBreaks = new ConcurrentHashMap<>();

    // Per-tick rate limiting
    private final AtomicInteger tickBreakCount = new AtomicInteger(0);

    // Per-second new-breach rate limiting
    private int newBreachesThisSecond = 0;
    private long lastSecondTs = System.currentTimeMillis();

    private BukkitTask breakProgressTask;
    private BukkitTask stuckCheckTask;

    public BreachManager(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        // Break progress: every tick
        breakProgressTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::progressTick, 1L, 1L);
        // Stuck check: every 10 ticks
        stuckCheckTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::stuckCheckTick, 10L, 10L);
    }

    public void shutdown() {
        if (breakProgressTask != null) breakProgressTask.cancel();
        if (stuckCheckTask != null) stuckCheckTask.cancel();
        activeBreaches.clear();
        mobMemories.clear();
        chunkBreaks.clear();
    }

    public void reload() {
        shutdown();
        startTasks();
    }

    public void onNightBegin(World world) {
        // Reset per-night mob memories for mobs in this world
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (e instanceof Mob) {
                MobMemory mem = mobMemories.get(e.getUniqueId());
                if (mem != null) mem.resetNight();
            }
        }
        // Reset chunk break counts for this world
        chunkBreaks.clear();
    }

    public void onDayBegin(World world) {
        // Cancel all active breaches in this world
        Iterator<Map.Entry<UUID, BreachEntry>> it = activeBreaches.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BreachEntry> entry = it.next();
            Mob mob = entry.getValue().getMob();
            if (mob.getWorld().equals(world)) {
                Block block = entry.getValue().getTarget();
                // Cancel visual damage
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(block.getLocation()) < 400) {
                        p.sendBlockDamage(block.getLocation(), 0f);
                    }
                }
                MobMemory mem = mobMemories.get(mob.getUniqueId());
                if (mem != null) mem.setCurrentlyBreaching(false);
                it.remove();
            }
        }
    }

    private void progressTick() {
        if (activeBreaches.isEmpty()) return;
        tickBreakCount.set(0);
        NightfallConfig cfg = plugin.getNfConfig();

        Iterator<Map.Entry<UUID, BreachEntry>> it = activeBreaches.entrySet().iterator();
        while (it.hasNext()) {
            if (tickBreakCount.get() >= cfg.maxBreakTicksPerTick) break;

            Map.Entry<UUID, BreachEntry> entry = it.next();
            BreachEntry breach = entry.getValue();
            Mob mob = breach.getMob();
            Block block = breach.getTarget();

            if (!mob.isValid() || mob.isDead()) {
                cancelBreach(entry.getKey(), block, mob.getWorld());
                it.remove();
                continue;
            }

            if (!plugin.getSiegeManager().isNight(mob.getWorld())) {
                cancelBreach(entry.getKey(), block, mob.getWorld());
                it.remove();
                continue;
            }

            // Abort if player moved too far
            if (!isPlayerNear(block.getLocation(), cfg.breachTriggerRadius)) {
                cancelBreach(entry.getKey(), block, mob.getWorld());
                it.remove();
                continue;
            }

            // Abort if block changed (already broken or changed type)
            if (block.getType().isAir()) {
                MobMemory mem = mobMemories.get(entry.getKey());
                if (mem != null) {
                    mem.setCurrentlyBreaching(false);
                    mem.resetChase();
                }
                it.remove();
                continue;
            }

            tickBreakCount.incrementAndGet();
            boolean done = breach.tick();

            // Send progressive damage visual to nearby players
            float progress = breach.getProgress();
            for (Player p : mob.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(block.getLocation()) < 400) {
                    p.sendBlockDamage(block.getLocation(), progress);
                }
            }

            // Particles + sound every 10 ticks
            if (breach.getElapsedTicks() % 10 == 0) {
                block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                        6, 0.3, 0.3, 0.3, block.getBlockData());
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 0.5f, 0.8f);
            }

            if (done) {
                completeBreach(entry.getKey(), breach);
                it.remove();
            }
        }
    }

    private void cancelBreach(UUID mobId, Block block, World world) {
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(block.getLocation()) < 400) {
                p.sendBlockDamage(block.getLocation(), 0f);
            }
        }
        MobMemory mem = mobMemories.get(mobId);
        if (mem != null) mem.setCurrentlyBreaching(false);
    }

    private void completeBreach(UUID mobId, BreachEntry breach) {
        Block block = breach.getTarget();
        World world = block.getWorld();

        // Cancel visual
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(block.getLocation()) < 400) {
                p.sendBlockDamage(block.getLocation(), 0f);
            }
        }

        // Break the block (no drops -- siege mobs aren't here for loot)
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.9f);
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                20, 0.4, 0.4, 0.4, block.getBlockData());
        block.setType(Material.AIR);

        // Update counters
        long chunkKey = chunkKey(block.getChunk());
        chunkBreaks.merge(chunkKey, 1, Integer::sum);

        MobMemory mem = mobMemories.get(mobId);
        if (mem != null) {
            mem.recordBreak();
            mem.setCurrentlyBreaching(false);
        }
    }

    private void stuckCheckTick() {
        if (!plugin.getNfConfig().breachEnabled) return;

        long now = System.currentTimeMillis();
        // Reset per-second counter
        if (now - lastSecondTs >= 1000) {
            newBreachesThisSecond = 0;
            lastSecondTs = now;
        }

        NightfallConfig cfg = plugin.getNfConfig();

        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.getSiegeManager().isNight(world)) continue;
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (mob.getTarget() == null) continue;
                if (!(mob.getTarget() instanceof Player target)) continue;
                if (!target.isOnline()) continue;

                UUID id = mob.getUniqueId();
                MobMemory mem = mobMemories.computeIfAbsent(id, k -> new MobMemory());

                // Track stuck ticks every check interval (10 ticks per call)
                int stuckTicks = mem.updateStuck(mob.getLocation());

                if (mem.isCurrentlyBreaching()) continue;

                if (mem.getBreaksThisNight() >= cfg.maxBreaksPerNight) continue;
                if (mem.getBreaksThisChase() >= cfg.maxBreaksPerChase) continue;

                if (newBreachesThisSecond >= cfg.maxNewBreachesPerSecond) break;

                // Count active breakers near this player
                int nearBreakers = countBreakersNear(target.getLocation(), cfg.breachTriggerRadius);
                if (nearBreakers >= cfg.maxBreakersPerPlayer) continue;

                if (!isPlayerNear(mob.getLocation(), cfg.breachTriggerRadius)) continue;

                // Only breach if mob is stuck OR cannot see the player
                boolean losBlocked = !mob.hasLineOfSight(target);
                boolean isStuck = stuckTicks >= cfg.stuckTicks;
                if (!isStuck && !losBlocked) continue;

                Block candidate = findCandidate(mob, target);
                if (candidate == null) continue;

                if (plugin.getWardManager().isProtected(candidate.getLocation())) continue;

                if (mem.isFailed(candidate)) {
                    // Try offset raytraces
                    candidate = tryOffsetCandidate(mob, target, mem);
                    if (candidate == null) continue;
                }

                int tier = cfg.getTierForBlock(candidate.getType());
                if (tier < 0) continue;

                NightfallConfig.TierConfig tierCfg = cfg.tiers.get(tier);
                if (!tierCfg.enabled()) continue;

                // Start breach
                mem.setCurrentlyBreaching(true);
                activeBreaches.put(id, new BreachEntry(mob, candidate, tierCfg.breakTicks()));
                newBreachesThisSecond++;

                if (cfg.debug) {
                    plugin.getLogger().info("[Nightfall] Breach started: mob=" + mob.getType()
                        + " block=" + candidate.getType() + " @ " + candidate.getLocation().toVector());
                }
            }
        }
    }

    private Block findCandidate(Mob mob, Player target) {
        NightfallConfig cfg = plugin.getNfConfig();
        Location eyeLoc = mob.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0);
        Vector direction = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double distance = eyeLoc.distance(targetLoc);

        // false = do NOT ignore passable blocks, so torches/doors/etc. are hit
        RayTraceResult result = mob.getWorld().rayTraceBlocks(eyeLoc, direction, distance + 1,
                org.bukkit.FluidCollisionMode.NEVER, false);

        if (result == null || result.getHitBlock() == null) return null;
        Block hit = result.getHitBlock();

        if (!isBreachable(hit, cfg)) return null;

        // Check breach preference -- search neighborhood for easier entry
        if (cfg.preferenceRadius > 0) {
            Block preferred = findPreferredEntry(hit, target.getLocation(), cfg);
            if (preferred != null) return preferred;
        }

        return hit;
    }

    private Block findPreferredEntry(Block anchor, Location targetLoc, NightfallConfig cfg) {
        int r = cfg.preferenceRadius;
        Block best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block candidate = anchor.getRelative(dx, dy, dz);
                    if (!isPreferenceBlock(candidate, cfg)) continue;
                    double dist = candidate.getLocation().distanceSquared(targetLoc);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private Block tryOffsetCandidate(Mob mob, Player target, MobMemory mem) {
        NightfallConfig cfg = plugin.getNfConfig();
        Location eyeLoc = mob.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0);

        // Try left/right/up offsets from target
        Vector[] offsets = {
            new Vector(1, 0, 0), new Vector(-1, 0, 0),
            new Vector(0, 0, 1), new Vector(0, 0, -1),
            new Vector(0, 1, 0)
        };

        for (Vector offset : offsets) {
            Location offsetTarget = targetLoc.clone().add(offset);
            Vector direction = offsetTarget.toVector().subtract(eyeLoc.toVector()).normalize();
            double distance = eyeLoc.distance(offsetTarget);
            RayTraceResult result = mob.getWorld().rayTraceBlocks(eyeLoc, direction, distance + 1,
                    org.bukkit.FluidCollisionMode.NEVER, false);
            if (result == null || result.getHitBlock() == null) continue;
            Block hit = result.getHitBlock();
            if (!isBreachable(hit, cfg)) continue;
            if (mem.isFailed(hit)) continue;
            if (plugin.getWardManager().isProtected(hit.getLocation())) continue;
            return hit;
        }
        return null;
    }

    private boolean isBreachable(Block block, NightfallConfig cfg) {
        Material mat = block.getType();
        if (mat.isAir()) return false;
        if (cfg.protectedBlocks.contains(mat)) return false;
        if (cfg.protectContainers && isContainer(block)) return false;
        return cfg.getTierForBlock(mat) >= 0;
    }

    private boolean isPreferenceBlock(Block block, NightfallConfig cfg) {
        Material mat = block.getType();
        if (mat.isAir()) return false;
        if (cfg.protectedBlocks.contains(mat)) return false;
        if (cfg.protectContainers && isContainer(block)) return false;
        if (cfg.preferenceBlocks.contains(mat)) return true;
        if (cfg.preferLeaves && mat.name().endsWith("_LEAVES")) return true;
        return false;
    }

    private boolean isContainer(Block block) {
        return block.getState() instanceof org.bukkit.block.Container;
    }

    private boolean isPlayerNear(Location loc, double radius) {
        double radiusSq = radius * radius;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= radiusSq) return true;
        }
        return false;
    }

    private int countBreakersNear(Location loc, double radius) {
        double radiusSq = radius * radius;
        int count = 0;
        for (BreachEntry entry : activeBreaches.values()) {
            if (entry.getTarget().getLocation().distanceSquared(loc) <= radiusSq) count++;
        }
        return count;
    }

    private static long chunkKey(Chunk chunk) {
        return ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
    }

    public int getChunkBreaks(Chunk chunk) {
        return chunkBreaks.getOrDefault(chunkKey(chunk), 0);
    }

    public MobMemory getMemory(UUID mobId) {
        return mobMemories.computeIfAbsent(mobId, k -> new MobMemory());
    }

    public Map<UUID, BreachEntry> getActiveBreaches() {
        return Collections.unmodifiableMap(activeBreaches);
    }
}
