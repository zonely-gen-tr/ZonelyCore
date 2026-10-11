package dev.zonely.whiteeffect.esports.model;

import org.bukkit.Location;

import java.util.Objects;

public final class EsportsSpawnSlot {

    private final int slotIndex;
    private final String label;
    private final EsportsSpawnPoint location;

    public EsportsSpawnSlot(int slotIndex, String label, EsportsSpawnPoint location) {
        this.slotIndex = Math.max(0, slotIndex);
        this.label = label == null ? "Slot #" + (slotIndex + 1) : label;
        this.location = location;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String getLabel() {
        return label;
    }

    public EsportsSpawnPoint getLocation() {
        return location;
    }

    public Location toLocation(org.bukkit.World fallbackWorld) {
        return location == null ? null : location.toLocation(fallbackWorld);
    }

    @Override
    public String toString() {
        return "EsportsSpawnSlot{" +
                "slotIndex=" + slotIndex +
                ", label='" + label + '\'' +
                ", location=" + location +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EsportsSpawnSlot)) return false;
        EsportsSpawnSlot that = (EsportsSpawnSlot) o;
        return slotIndex == that.slotIndex &&
                Objects.equals(label, that.label) &&
                Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotIndex, label, location);
    }
}
