package com.nightfall.config;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;

public class NightfallConfig {

    private final NightfallPlugin plugin;

    // Night
    public long nightStart;
    public long nightEnd;
    public Set<String> enabledWorlds;

    // Siege
    public double mobDamageMultiplier;
    public double playerIncomingMultiplier;

    // Heat
    public long heatDecayMillis;
    public double heatMergeRadius;
    public int heatBlockBreak;
    public int heatBlockPlace;
    public int heatSprintStart;
    public int heatInteract;
    public int heatShootBow;
    public int heatPlayerHurt;
    public double heatMobScanRadius;
    public long lastSeenMemoryMillis;

    // Light attraction
    public boolean lightAttractionEnabled;
    public double lightScanRadius;
    public Set<Material> lightBlocks;

    // Breach
    public boolean breachEnabled;
    public double breachTriggerRadius;
    public int stuckTicks;
    public int pathFailTicks;
    public int preferenceRadius;
    public Set<Material> preferenceBlocks;
    public boolean preferLeaves;
    public Set<Material> protectedBlocks;
    public boolean protectContainers;
    public Map<Integer, TierConfig> tiers;
    public int maxBreaksPerNight;
    public int maxBreaksPerChase;
    public int maxChunkBreaksPerNight;
    public int maxBreakersPerPlayer;
    public int maxBreakTicksPerTick;
    public int maxNewBreachesPerSecond;
    public long failedBlockCooldownMillis;

    // Debug
    public boolean debug;

    public enum RequiredTool { NONE, AXE, PICKAXE }
    public record TierConfig(boolean enabled, int breakTicks, Set<Material> blocks, RequiredTool requiredTool) {}

    public NightfallConfig(NightfallPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() { load(); }

    private void load() {
        FileConfiguration c = plugin.getConfig();

        nightStart = c.getLong("night.start-tick", 13000);
        nightEnd = c.getLong("night.end-tick", 23000);
        enabledWorlds = new HashSet<>(c.getStringList("night.enabled-worlds"));

        mobDamageMultiplier = c.getDouble("siege.mob-damage-multiplier", 1.4);
        playerIncomingMultiplier = c.getDouble("siege.player-incoming-multiplier", 1.0);

        heatDecayMillis = c.getLong("heat.decay-seconds", 90) * 1000L;
        heatMergeRadius = c.getDouble("heat.merge-radius", 3.0);
        heatBlockBreak = c.getInt("heat.sources.block-break", 20);
        heatBlockPlace = c.getInt("heat.sources.block-place", 10);
        heatSprintStart = c.getInt("heat.sources.sprint-start", 5);
        heatInteract = c.getInt("heat.sources.interact", 8);
        heatShootBow = c.getInt("heat.sources.shoot-bow", 15);
        heatPlayerHurt = c.getInt("heat.sources.player-hurt", 30);
        heatMobScanRadius = c.getDouble("heat.mob-scan-radius", 32.0);
        lastSeenMemoryMillis = c.getLong("heat.last-seen-memory-seconds", 20) * 1000L;

        lightAttractionEnabled = c.getBoolean("light-attraction.enabled", true);
        lightScanRadius = c.getDouble("light-attraction.scan-radius", 20.0);
        lightBlocks = parseMaterials(c.getStringList("light-attraction.light-blocks"));
        breachEnabled = c.getBoolean("breach.enabled", true);
        breachTriggerRadius = c.getDouble("breach.trigger-radius", 24.0);
        stuckTicks = c.getInt("breach.stuck-ticks", 40);
        pathFailTicks = c.getInt("breach.path-fail-ticks", 60);
        preferenceRadius = c.getInt("breach.preference-radius", 2);
        preferenceBlocks = parseMaterials(c.getStringList("breach.preference-blocks"));
        preferLeaves = c.getBoolean("breach.prefer-leaves", false);
        protectedBlocks = parseMaterials(c.getStringList("breach.protected-blocks"));
        protectContainers = c.getBoolean("breach.protect-containers", true);

        tiers = new HashMap<>();
        var tiersSection = c.getConfigurationSection("breach.tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    String path = "breach.tiers." + tier;
                    boolean en = c.getBoolean(path + ".enabled", true);
                    int bt = c.getInt(path + ".break-ticks", 60);
                    Set<Material> blocks = parseMaterials(c.getStringList(path + ".blocks"));
                    String toolStr = c.getString(path + ".required-tool", "none").toUpperCase();
                    RequiredTool tool;
                    try { tool = RequiredTool.valueOf(toolStr); }
                    catch (IllegalArgumentException ex) { tool = RequiredTool.NONE; }
                    tiers.put(tier, new TierConfig(en, bt, blocks, tool));
                } catch (NumberFormatException ignored) {}
            }
        }

        maxBreaksPerNight = c.getInt("breach.max-breaks-per-night", 8);
        maxBreaksPerChase = c.getInt("breach.max-breaks-per-chase", 3);
        maxChunkBreaksPerNight = c.getInt("breach.max-chunk-breaks-per-night", 20);
        maxBreakersPerPlayer = c.getInt("breach.max-breakers-per-player", 3);
        maxBreakTicksPerTick = c.getInt("breach.max-break-ticks-per-tick", 10);
        maxNewBreachesPerSecond = c.getInt("breach.max-new-breaches-per-second", 6);
        failedBlockCooldownMillis = c.getLong("breach.failed-block-cooldown-seconds", 15) * 1000L;

        debug = c.getBoolean("debug", false);
    }

    private Set<Material> parseMaterials(List<String> names) {
        Set<Material> set = new HashSet<>();
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                set.add(m);
            } else {
                plugin.getLogger().log(Level.WARNING, "Unknown material in config: {0}", name);
            }
        }
        return set;
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    public int getTierForBlock(Material material) {
        int best = -1;
        for (Map.Entry<Integer, TierConfig> e : tiers.entrySet()) {
            if (e.getValue().enabled() && e.getValue().blocks().contains(material)) {
                if (best == -1 || e.getKey() < best) best = e.getKey();
            }
        }
        return best;
    }
}
