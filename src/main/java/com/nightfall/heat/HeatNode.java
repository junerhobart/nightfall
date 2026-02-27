package com.nightfall.heat;

import org.bukkit.Location;

public class HeatNode {

    private Location location;
    private int intensity;
    private long createdAt;
    private long lastUpdated;

    public HeatNode(Location location, int intensity) {
        this.location = location.clone();
        this.intensity = intensity;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = this.createdAt;
    }

    public void merge(int additionalIntensity) {
        this.intensity += additionalIntensity;
        this.lastUpdated = System.currentTimeMillis();
    }

    /** Returns true if this node has fully decayed and should be removed. */
    public boolean decay(long decayMillis) {
        long age = System.currentTimeMillis() - lastUpdated;
        if (age >= decayMillis) {
            intensity = 0;
            return true;
        }
        // Linear decay -- reduce proportionally to age
        double fraction = 1.0 - ((double) age / decayMillis);
        intensity = (int) (intensity * fraction);
        return intensity <= 0;
    }

    public Location getLocation() { return location; }
    public int getIntensity() { return intensity; }
    public long getLastUpdated() { return lastUpdated; }
}
