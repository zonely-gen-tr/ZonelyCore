package dev.zonely.whiteeffect.api.deliveries.impl;

import dev.zonely.whiteeffect.api.deliveries.DeliveriesAPI;
import dev.zonely.whiteeffect.api.deliveries.DeliveryStatus;
import dev.zonely.whiteeffect.api.exception.CreditOperationException;
import dev.zonely.whiteeffect.deliveries.Delivery;
import dev.zonely.whiteeffect.player.Profile;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DeliveriesApiImpl implements DeliveriesAPI {

    @Override
    public List<Delivery> listDeliveries() {
        return Collections.unmodifiableList(new ArrayList<>(Delivery.listDeliveries()));
    }

    @Override
    public Optional<DeliveryStatus> getStatus(Player player, Delivery delivery) {
        if (player == null || delivery == null) {
            return Optional.empty();
        }

        Profile profile = Profile.getProfile(player.getName());
        if (profile == null) {
            return Optional.empty();
        }

        boolean hasPermission = delivery.hasPermission(player);
        long claimTime = profile.getDeliveriesContainer().getClaimTime(delivery.getId());
        boolean alreadyClaimed = claimTime > System.currentTimeMillis();

        return Optional.of(new DeliveryStatus(delivery, hasPermission, alreadyClaimed, claimTime));
    }

    @Override
    public boolean claim(Player player, Delivery delivery) throws CreditOperationException {
        return claim(player, delivery, true);
    }

    @Override
    public boolean claim(Player player, Delivery delivery, boolean notifyPlayer) throws CreditOperationException {
        if (player == null || delivery == null) {
            return false;
        }

        Profile profile = Profile.getProfile(player.getName());
        if (profile == null) {
            throw new CreditOperationException("Profile is not loaded for player " + player.getName());
        }

        if (!delivery.hasPermission(player)) {
            return false;
        }

        if (profile.getDeliveriesContainer().alreadyClaimed(delivery.getId())) {
            return false;
        }

        profile.getDeliveriesContainer().claimDelivery(delivery.getId(), delivery.getDays());
        delivery.listRewards().forEach(reward -> reward.dispatch(profile));
        if (notifyPlayer) {
            player.sendMessage(delivery.getMessage());
        }
        return true;
    }
}
