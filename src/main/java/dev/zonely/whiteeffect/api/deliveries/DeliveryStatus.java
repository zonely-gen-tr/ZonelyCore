package dev.zonely.whiteeffect.api.deliveries;

import dev.zonely.whiteeffect.deliveries.Delivery;

public final class DeliveryStatus {

    private final Delivery delivery;
    private final boolean hasPermission;
    private final boolean alreadyClaimed;
    private final long nextAvailableAt;

    public DeliveryStatus(Delivery delivery, boolean hasPermission, boolean alreadyClaimed, long nextAvailableAt) {
        this.delivery = delivery;
        this.hasPermission = hasPermission;
        this.alreadyClaimed = alreadyClaimed;
        this.nextAvailableAt = nextAvailableAt;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public boolean hasPermission() {
        return hasPermission;
    }

    public boolean isAlreadyClaimed() {
        return alreadyClaimed;
    }

    public long getNextAvailableAt() {
        return nextAvailableAt;
    }

    public boolean canClaimNow() {
        return hasPermission && !alreadyClaimed;
    }
}
