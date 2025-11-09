package dev.zonely.whiteeffect.api.cash.impl;

import dev.zonely.whiteeffect.api.cash.CashAPI;
import dev.zonely.whiteeffect.api.exception.CreditOperationException;
import dev.zonely.whiteeffect.cash.CashException;
import dev.zonely.whiteeffect.cash.CashManager;
import org.bukkit.entity.Player;

public class CashApiImpl implements CashAPI {

    @Override
    public long getBalance(String playerName) {
        return CashManager.getCash(playerName);
    }

    @Override
    public long getBalance(Player player) {
        return player == null ? 0L : CashManager.getCash(player);
    }

    @Override
    public long setBalance(String playerName, long amount) throws CreditOperationException {
        requireName(playerName);
        try {
            CashManager.setCash(playerName, amount);
            return amount;
        } catch (CashException ex) {
            throw wrap("Failed to set balance for " + playerName, ex);
        }
    }

    @Override
    public long setBalance(Player player, long amount) throws CreditOperationException {
        requirePlayer(player);
        return setBalance(player.getName(), amount);
    }

    @Override
    public long addBalance(String playerName, long amount) throws CreditOperationException {
        requireName(playerName);
        try {
            return CashManager.addCash(playerName, amount);
        } catch (CashException ex) {
            throw wrap("Failed to add balance for " + playerName, ex);
        }
    }

    @Override
    public long addBalance(Player player, long amount) throws CreditOperationException {
        requirePlayer(player);
        return addBalance(player.getName(), amount);
    }

    @Override
    public long withdraw(String playerName, long amount) throws CreditOperationException {
        requireName(playerName);
        try {
            return CashManager.removeCash(playerName, amount);
        } catch (CashException ex) {
            throw wrap("Failed to withdraw balance for " + playerName, ex);
        }
    }

    @Override
    public long withdraw(Player player, long amount) throws CreditOperationException {
        requirePlayer(player);
        return withdraw(player.getName(), amount);
    }

    @Override
    public boolean isCached(String playerName) {
        return CashManager.isCached(playerName);
    }

    @Override
    public void invalidateCache(String playerName) {
        CashManager.invalidateCache(playerName);
    }

    private void requireName(String name) throws CreditOperationException {
        if (name == null || name.trim().isEmpty()) {
            throw new CreditOperationException("Player name is required.");
        }
    }

    private void requirePlayer(Player player) throws CreditOperationException {
        if (player == null) {
            throw new CreditOperationException("Player instance is required.");
        }
    }

    private CreditOperationException wrap(String message, Exception cause) {
        return new CreditOperationException(message, cause);
    }
}
