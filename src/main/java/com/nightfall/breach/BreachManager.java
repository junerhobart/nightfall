package com.nightfall.breach;

import com.nightfall.config.NightfallConfig;
import com.nightfall.main.NightfallPlugin;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
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

    // Per-block shared breach: blockKey -> BlockBreach
    private final Map<Long, BlockBreach> blockBreaches = new ConcurrentHashMap<>();

    // Mob UUID -> block key they are targeting (quick lookup)
    private final Map<UUID, Long> mobBreachTarget = new ConcurrentHashMap<>();

    // Per-mob memory
    private final Map<UUID, MobMemory> mobMemories = new ConcurrentHashMap<>();

    // Creepers navigating toward an explosion target
    private final Map<UUID, Location> creeperTargets = new ConcurrentHashMap<>();

    // How many stuckCheckTicks a creeper has been waiting for friends to clear
    private final Map<UUID, Integer> creeperWaitTicks = new ConcurrentHashMap<>();

    // Per-chunk break count this night
    private final Map<Long, Integer> chunkBreaks = new ConcurrentHashMap<>();

    private final AtomicInteger tickBreakCount = new AtomicInteger(0);
    private int newBreachesThisSecond = 0;
    private long lastSecondTs = System.currentTimeMillis();

    private BukkitTask breakProgressTask;
    private BukkitTask stuckCheckTask;

    public BreachManager(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        breakProgressTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::progressTick, 1L, 1L);
        stuckCheckTask    = plugin.getServer().getScheduler().runTaskTimer(plugin, this::stuckCheckTick, 10L, 10L);
    }

    public void shutdown() {
        if (breakProgressTask != null) breakProgressTask.cancel();
        if (stuckCheckTask != null) stuckCheckTask.cancel();
        blockBreaches.clear();
        mobBreachTarget.clear();
        mobMemories.clear();
        chunkBreaks.clear();
        creeperTargets.clear();
        creeperWaitTicks.clear();
    }

    public void reload() {
        shutdown();
        startTasks();
    }

    public void onNightBegin(World world) {
        for (Entity e : world.getEntities()) {
            if (e instanceof Mob) {
                MobMemory mem = mobMemories.get(e.getUniqueId());
                if (mem != null) mem.resetNight();
            }
        }
        chunkBreaks.clear();
    }

    public void onDayBegin(World world) {
        Iterator<Map.Entry<Long, BlockBreach>> it = blockBreaches.entrySet().iterator();
        while (it.hasNext()) {
            BlockBreach breach = it.next().getValue();
            if (!breach.getBlock().getWorld().equals(world)) continue;
            cancelVisual(breach.getBlock());
            for (UUID id : breach.getParticipants()) releaseMob(id);
            it.remove();
        }
        // Clear creeper assignments for this world
        creeperTargets.keySet().removeIf(id -> {
            Entity e = plugin.getServer().getEntity(id);
            return e == null || e.getWorld().equals(world);
        });
        creeperWaitTicks.keySet().removeIf(id -> {
            Entity e = plugin.getServer().getEntity(id);
            return e == null || e.getWorld().equals(world);
        });
    }

    // -------------------------------------------------------------------------
    // Progress tick: every server tick
    // -------------------------------------------------------------------------

    private void progressTick() {
        if (blockBreaches.isEmpty()) return;
        tickBreakCount.set(0);
        NightfallConfig cfg = plugin.getNfConfig();

        Iterator<Map.Entry<Long, BlockBreach>> it = blockBreaches.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, BlockBreach> entry = it.next();
            BlockBreach breach = entry.getValue();
            Block block = breach.getBlock();
            World world = block.getWorld();

            if (!plugin.getSiegeManager().isNight(world)) {
                cancelVisual(block);
                for (UUID id : breach.getParticipants()) releaseMob(id);
                it.remove();
                continue;
            }

            if (!breach.isTorchHunt() && !isPlayerNear(block.getLocation(), cfg.breachTriggerRadius)) {
                cancelVisual(block);
                for (UUID id : breach.getParticipants()) releaseMob(id);
                it.remove();
                continue;
            }

            if (block.getType().isAir()) {
                for (UUID id : breach.getParticipants()) {
                    MobMemory mem = mobMemories.get(id);
                    if (mem != null) { mem.setCurrentlyBreaching(false); mem.resetChase(); }
                    mobBreachTarget.remove(id);
                }
                it.remove();
                continue;
            }

            // Each participant: freeze in place, face block, contribute a tick
            int ticksThisBlock = 0;
            for (UUID mobId : new ArrayList<>(breach.getParticipants())) {
                Entity ent = plugin.getServer().getEntity(mobId);
                if (!(ent instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                    breach.removeParticipant(mobId);
                    releaseMob(mobId);
                    continue;
                }

                mob.getPathfinder().stopPathfinding();
                mob.setVelocity(new Vector(0, Math.min(mob.getVelocity().getY(), 0), 0));
                mob.lookAt(block.getLocation().add(0.5, 0.5, 0.5));

                if (tickBreakCount.get() < cfg.maxBreakTicksPerTick) {
                    tickBreakCount.incrementAndGet();
                    ticksThisBlock++;
                }
            }

            if (breach.isEmpty()) {
                cancelVisual(block);
                it.remove();
                continue;
            }

            // Advance block progress (each adjacent mob adds one tick)
            boolean done = false;
            for (int i = 0; i < ticksThisBlock; i++) {
                done = breach.tick();
                if (done) break;
            }

            // Visual crack + particles
            float progress = breach.getProgress();
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(block.getLocation()) < 400) {
                    p.sendBlockDamage(block.getLocation(), progress);
                }
            }
            if (ticksThisBlock > 0 && breach.getElapsedTicks() % 10 == 0) {
                world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                        6, 0.3, 0.3, 0.3, block.getBlockData());
                world.playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 0.5f, 0.8f);
            }

            if (done) {
                completeBreach(breach);
                it.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Stuck-check / breach-start tick: every 10 ticks
    // -------------------------------------------------------------------------

    private void stuckCheckTick() {
        if (!plugin.getNfConfig().breachEnabled) return;

        long now = System.currentTimeMillis();
        if (now - lastSecondTs >= 1000) {
            newBreachesThisSecond = 0;
            lastSecondTs = now;
        }

        NightfallConfig cfg = plugin.getNfConfig();
        double reachSq = cfg.maxBreachReach * cfg.maxBreachReach;

        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.getSiegeManager().isNight(world)) continue;
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                UUID id = mob.getUniqueId();
                MobMemory mem = mobMemories.computeIfAbsent(id, k -> new MobMemory());

                mem.updateStuck(mob.getLocation());

                // Route by mob role
                if (!cfg.exploderMobs.isEmpty() && cfg.exploderMobs.contains(mob.getType())) {
                    handleCreeperTick(mob, mem, cfg);
                    continue;
                }
                if (!cfg.followerMobs.isEmpty() && cfg.followerMobs.contains(mob.getType())) {
                    handleFollowerTick(mob, cfg);
                    continue;
                }
                // If a breaker list is configured, only those mobs break blocks
                if (!cfg.breakerMobs.isEmpty() && !cfg.breakerMobs.contains(mob.getType())) continue;

                if (mem.isCurrentlyBreaching()) continue;
                if (mem.getBreaksThisNight() >= cfg.maxBreaksPerNight) continue;
                if (newBreachesThisSecond >= cfg.maxNewBreachesPerSecond) break;

                Block candidate = null;
                boolean isTorchHunt = false;

                // Torch in path: no player target required.
                // Use player direction when available, otherwise mob's own facing direction.
                if (!cfg.torchHuntBlocks.isEmpty()) {
                    Block torchInPath = (mob.getTarget() instanceof Player tp)
                            ? findCandidateInPath(mob, tp, cfg.torchHuntBlocks)
                            : findCandidateInFacingDir(mob, cfg.torchHuntBlocks);
                    if (torchInPath != null && !mem.isFailed(torchInPath)) {
                        candidate = torchInPath;
                        isTorchHunt = true;
                    }
                }

                // Wall-breach: only when targeting a player and stuck or LOS blocked
                if (candidate == null && mob.getTarget() instanceof Player target && target.isOnline()
                        && isPlayerNear(mob.getLocation(), cfg.breachTriggerRadius)
                        && mem.getBreaksThisChase() < cfg.maxBreaksPerChase) {
                    int nearBreakers = countBreakersNear(target.getLocation(), cfg.breachTriggerRadius);
                    if (nearBreakers < cfg.maxBreakersPerPlayer) {
                        boolean losBlocked = !mob.hasLineOfSight(target);
                        boolean isStuck = mem.getStuckTickCount() >= cfg.stuckTicks;
                        if (isStuck || losBlocked) {
                            candidate = findCandidate(mob, target);
                            if (candidate != null && mem.isFailed(candidate)) {
                                candidate = tryOffsetCandidate(mob, target, mem);
                            }
                            if (candidate != null
                                    && mob.getLocation().distanceSquared(candidate.getLocation()) > reachSq) {
                                candidate = null;
                            }
                        }
                    }
                }

                if (candidate == null) continue;

                int tier = cfg.getTierForBlock(candidate.getType());
                if (tier < 0) continue;
                NightfallConfig.TierConfig tierCfg = cfg.tiers.get(tier);
                if (tierCfg == null || !tierCfg.enabled()) continue;
                if (!canMobBreachTier(mob, tierCfg)) continue;

                long chunkKey = chunkKey(candidate.getChunk());
                if (chunkBreaks.getOrDefault(chunkKey, 0) >= cfg.maxChunkBreaksPerNight) continue;

                int breakTicks = cfg.getBreakTicksForMaterial(candidate.getType());
                long bKey = blockKey(candidate);

                // Join an existing breach on this block, or start a new one
                BlockBreach existing = blockBreaches.get(bKey);
                if (existing != null) {
                    existing.addParticipant(id);
                    mobBreachTarget.put(id, bKey);
                    mem.setCurrentlyBreaching(true);
                    if (cfg.debug) plugin.getLogger().info("[Nightfall] Mob joined breach: "
                            + mob.getType() + " block=" + candidate.getType());
                } else {
                    BlockBreach breach = new BlockBreach(candidate, breakTicks, id, isTorchHunt);
                    blockBreaches.put(bKey, breach);
                    mobBreachTarget.put(id, bKey);
                    mem.setCurrentlyBreaching(true);
                    newBreachesThisSecond++;
                    if (cfg.debug) plugin.getLogger().info("[Nightfall] Breach started: mob="
                            + mob.getType() + " block=" + candidate.getType()
                            + " @ " + candidate.getLocation().toVector()
                            + (isTorchHunt ? " [torch-hunt]" : " [wall]"));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Role handlers
    // -------------------------------------------------------------------------

    /**
     * Creeper breach role: navigate to the wall blocking the path and ignite.
     * Uses the same stuck/LOS trigger as normal breach but replaces chipping
     * with a full explosion, which clears multiple blocks at once.
     */
    private void handleCreeperTick(Mob mob, MobMemory mem, NightfallConfig cfg) {
        if (!(mob instanceof Creeper creeper)) return;
        UUID id = mob.getUniqueId();

        // If fuse is already counting down (ignited naturally or by us), leave it alone
        if (creeper.getFuseTicks() < creeper.getMaxFuseTicks()) {
            creeperTargets.remove(id);
            return;
        }

        Location target = creeperTargets.get(id);

        if (target != null) {
            double distSq = creeper.getLocation().distanceSquared(target);
            if (distSq <= cfg.exploderTriggerRadius * cfg.exploderTriggerRadius) {
                // Wait for nearby friendly mobs to clear before detonating
                double blastRadius = creeper.getExplosionRadius() + 2.0;
                int friendsNearby = countMobsNear(target, blastRadius, creeper);
                int waited = creeperWaitTicks.getOrDefault(id, 0);

                if (friendsNearby > 0 && waited < 50) {
                    // Back off a step to give mobs room to pass
                    Vector away = creeper.getLocation().toVector()
                            .subtract(target.toVector()).normalize();
                    Location backOff = creeper.getLocation().clone().add(away);
                    try { creeper.getPathfinder().moveTo(backOff, 0.5); } catch (Exception ignored) {}
                    creeperWaitTicks.merge(id, 1, Integer::sum);
                } else {
                    creeper.ignite();
                    creeperTargets.remove(id);
                    creeperWaitTicks.remove(id);
                }
            } else {
                try { creeper.getPathfinder().moveTo(target, 1.1); } catch (Exception ignored) {}
            }
            return;
        }

        // Find a target using the same stuck/LOS trigger
        if (!(mob.getTarget() instanceof Player player) || !player.isOnline()) return;
        if (!isPlayerNear(mob.getLocation(), cfg.breachTriggerRadius)) return;

        boolean isStuck = mem.getStuckTickCount() >= cfg.stuckTicks;
        boolean losBlocked = !mob.hasLineOfSight(player);
        if (!isStuck && !losBlocked) return;

        Block candidate = findCandidate(mob, player);
        if (candidate == null) return;

        Location dest = candidate.getLocation().add(0.5, 0, 0.5);
        creeperTargets.put(id, dest);
        try { creeper.getPathfinder().moveTo(dest, 1.1); } catch (Exception ignored) {}
        if (cfg.debug) plugin.getLogger().info("[Nightfall] Creeper targeting explosion @ " + candidate.getLocation().toVector());
    }

    /**
     * Follower breach role (skeletons, strays): don't break blocks, but flock
     * toward the nearest active breach so they support the mob pushing through.
     * Falls back to vanilla AI when no breach is active.
     */
    private void handleFollowerTick(Mob mob, NightfallConfig cfg) {
        if (blockBreaches.isEmpty()) return;

        Location mobLoc = mob.getLocation();
        double radiusSq = cfg.followerRadius * cfg.followerRadius;
        Location target = null;
        double bestDist = radiusSq;

        for (BlockBreach breach : blockBreaches.values()) {
            if (!breach.getBlock().getWorld().equals(mob.getWorld())) continue;
            double dist = mobLoc.distanceSquared(breach.getBlock().getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                target = breach.getBlock().getLocation().add(0.5, 0, 0.5);
            }
        }

        if (target != null) {
            try { mob.getPathfinder().moveTo(target, 1.0); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void releaseMob(UUID mobId) {
        MobMemory mem = mobMemories.get(mobId);
        if (mem != null) {
            mem.setCurrentlyBreaching(false);
            mem.resetStuck(); // prevent immediate re-breach while still frozen in place
        }
        mobBreachTarget.remove(mobId);
    }

    private void cancelVisual(Block block) {
        for (Player p : block.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(block.getLocation()) < 400) {
                p.sendBlockDamage(block.getLocation(), 0f);
            }
        }
    }

    private void completeBreach(BlockBreach breach) {
        Block block = breach.getBlock();
        World world = block.getWorld();
        cancelVisual(block);
        world.playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.9f);
        world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                20, 0.4, 0.4, 0.4, block.getBlockData());
        block.breakNaturally(new org.bukkit.inventory.ItemStack(Material.IRON_PICKAXE));
        chunkBreaks.merge(chunkKey(block.getChunk()), 1, Integer::sum);
        for (UUID mobId : breach.getParticipants()) {
            MobMemory mem = mobMemories.get(mobId);
            if (mem != null) {
                if (breach.isTorchHunt()) mem.recordTorchBreak();
                else mem.recordBreak();
            }
            releaseMob(mobId);
        }
    }

    private Block findCandidate(Mob mob, Player target) {
        NightfallConfig cfg = plugin.getNfConfig();
        Location eyeLoc = mob.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0);
        Vector direction = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double distance = eyeLoc.distance(targetLoc);

        RayTraceResult result = mob.getWorld().rayTraceBlocks(eyeLoc, direction, distance + 1,
                org.bukkit.FluidCollisionMode.NEVER, false);
        if (result == null || result.getHitBlock() == null) return null;
        Block hit = result.getHitBlock();
        if (!isBreachable(hit, cfg)) return null;

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
                    Block c = anchor.getRelative(dx, dy, dz);
                    if (!isPreferenceBlock(c, cfg)) continue;
                    double dist = c.getLocation().distanceSquared(targetLoc);
                    if (dist < bestDist) { bestDist = dist; best = c; }
                }
            }
        }
        return best;
    }

    private Block tryOffsetCandidate(Mob mob, Player target, MobMemory mem) {
        NightfallConfig cfg = plugin.getNfConfig();
        Location eyeLoc = mob.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0);
        Vector[] offsets = {
            new Vector(1, 0, 0), new Vector(-1, 0, 0),
            new Vector(0, 0, 1), new Vector(0, 0, -1),
            new Vector(0, 1, 0)
        };
        for (Vector offset : offsets) {
            Location offsetTarget = targetLoc.clone().add(offset);
            Vector dir = offsetTarget.toVector().subtract(eyeLoc.toVector()).normalize();
            double dist = eyeLoc.distance(offsetTarget);
            RayTraceResult result = mob.getWorld().rayTraceBlocks(eyeLoc, dir, dist + 1,
                    org.bukkit.FluidCollisionMode.NEVER, false);
            if (result == null || result.getHitBlock() == null) continue;
            Block hit = result.getHitBlock();
            if (!isBreachable(hit, cfg) || mem.isFailed(hit)) continue;
            return hit;
        }
        return null;
    }

    private static final org.bukkit.block.BlockFace[] CARDINAL_FACES = {
        org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
        org.bukkit.block.BlockFace.EAST,  org.bukkit.block.BlockFace.WEST,
        org.bukkit.block.BlockFace.UP,    org.bukkit.block.BlockFace.DOWN
    };

    /**
     * Raytrace in the mob's facing direction (no player target needed).
     * Checks the first hit block AND its faces -- wall torches are always
     * mounted on the solid block the raytrace hits, not the torch itself.
     */
    private Block findCandidateInFacingDir(Mob mob, Set<Material> materials) {
        Location eyeLoc = mob.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        RayTraceResult result = mob.getWorld().rayTraceBlocks(eyeLoc, direction, 4.0,
                org.bukkit.FluidCollisionMode.NEVER, false);
        if (result == null || result.getHitBlock() == null) return null;
        return torchOnOrAdjacentTo(result.getHitBlock(), materials);
    }

    /**
     * Raytrace from mob eyes toward the player. Checks the first hit block AND
     * its faces for attached torches/light sources.
     */
    private Block findCandidateInPath(Mob mob, Player target, Set<Material> materials) {
        Location eyeLoc = mob.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0);
        Vector direction = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double distance = eyeLoc.distance(targetLoc);
        RayTraceResult result = mob.getWorld().rayTraceBlocks(eyeLoc, direction, distance + 1,
                org.bukkit.FluidCollisionMode.NEVER, false);
        if (result == null || result.getHitBlock() == null) return null;
        return torchOnOrAdjacentTo(result.getHitBlock(), materials);
    }

    /** Returns the hit block if it's a torch, otherwise checks all six faces for one. */
    private Block torchOnOrAdjacentTo(Block hit, Set<Material> materials) {
        NightfallConfig cfg = plugin.getNfConfig();
        if (materials.contains(hit.getType()) && isBreachable(hit, cfg)) return hit;
        for (org.bukkit.block.BlockFace face : CARDINAL_FACES) {
            Block adj = hit.getRelative(face);
            if (materials.contains(adj.getType()) && isBreachable(adj, cfg)) return adj;
        }
        return null;
    }

    private int countMobsNear(Location loc, double radius, Mob exclude) {
        double radiusSq = radius * radius;
        int count = 0;
        for (Mob m : loc.getWorld().getEntitiesByClass(Mob.class)) {
            if (m == exclude) continue;
            if (m.getLocation().distanceSquared(loc) <= radiusSq) count++;
        }
        return count;
    }

    private boolean isBreachable(Block block, NightfallConfig cfg) {
        Material mat = block.getType();
        if (mat.isAir()) return false;
        if (mat.getHardness() < 0) return false;
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
        for (UUID mobId : mobBreachTarget.keySet()) {
            Entity ent = plugin.getServer().getEntity(mobId);
            if (ent != null && ent.getLocation().distanceSquared(loc) <= radiusSq) count++;
        }
        return count;
    }

    private boolean canMobBreachTier(Mob mob, NightfallConfig.TierConfig tierCfg) {
        NightfallConfig.RequiredTool req = tierCfg.requiredTool();
        if (req == NightfallConfig.RequiredTool.NONE) return true;
        if (mob.getEquipment() == null) return false;
        String hand = mob.getEquipment().getItemInMainHand().getType().name();
        return switch (req) {
            case AXE -> hand.endsWith("_AXE");
            case PICKAXE -> hand.endsWith("_PICKAXE");
            default -> true;
        };
    }

    private static long blockKey(Block b) {
        long x = (long) (b.getX() + 32768) & 0xFFFFL;
        long y = (long) (b.getY() + 2048) & 0xFFFL;
        long z = (long) (b.getZ() + 32768) & 0xFFFFL;
        return (x << 28) | (y << 16) | z;
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

    /** Returns the number of blocks currently being breached. */
    public int getActiveBreachCount() {
        return blockBreaches.size();
    }

    /** Returns participating mob count across all active breaches. */
    public int getActiveMobCount() {
        return mobBreachTarget.size();
    }
}
