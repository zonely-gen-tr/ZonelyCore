package dev.zonely.whiteeffect.api.deliveries;

import dev.zonely.whiteeffect.api.exception.CreditOperationException;
import dev.zonely.whiteeffect.deliveries.Delivery;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public interface DeliveriesAPI {

    List<Delivery> listDeliveries();

    Optional<DeliveryStatus> getStatus(Player player, Delivery delivery);

    boolean claim(Player player, Delivery delivery) throws CreditOperationException;

    boolean claim(Player player, Delivery delivery, boolean notifyPlayer) throws CreditOperationException;
}
