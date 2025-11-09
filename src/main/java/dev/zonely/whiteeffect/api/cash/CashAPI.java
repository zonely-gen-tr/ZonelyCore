package dev.zonely.whiteeffect.api.cash;

import dev.zonely.whiteeffect.api.exception.CreditOperationException;
import org.bukkit.entity.Player;

public interface CashAPI {

    long getBalance(String playerName);

    long getBalance(Player player);

    long setBalance(String playerName, long amount) throws CreditOperationException;

    long setBalance(Player player, long amount) throws CreditOperationException;

    long addBalance(String playerName, long amount) throws CreditOperationException;

    long addBalance(Player player, long amount) throws CreditOperationException;

    long withdraw(String playerName, long amount) throws CreditOperationException;

    long withdraw(Player player, long amount) throws CreditOperationException;

    boolean isCached(String playerName);

    void invalidateCache(String playerName);
}
