package dev.zonely.whiteeffect.api.voucher.impl;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.api.exception.CreditOperationException;
import dev.zonely.whiteeffect.api.voucher.VoucherAPI;
import dev.zonely.whiteeffect.api.voucher.VoucherDetails;
import dev.zonely.whiteeffect.api.voucher.VoucherIssueResult;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.cash.CreditRepository;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.store.CreditPaperManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.logging.Level;

public class VoucherApiImpl implements VoucherAPI {

    private final Core plugin;

    public VoucherApiImpl(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public VoucherIssueResult issueVoucher(String issuerName, String targetName, long amount) throws CreditOperationException {
        String issuer = normalize(issuerName);
        String target = normalize(targetName);

        if (issuer.isEmpty()) {
            throw new CreditOperationException("Issuer name must not be empty.");
        }
        if (target.isEmpty()) {
            throw new CreditOperationException("Target name must not be empty.");
        }
        if (amount <= 0) {
            throw new CreditOperationException("Voucher amount must be greater than zero.");
        }

        OptionalInt issuerIdOpt = CreditRepository.findUserId(issuer);
        if (!issuerIdOpt.isPresent()) {
            throw new CreditOperationException("Issuer account could not be located.");
        }

        OptionalLong issuerCreditOpt = CreditRepository.loadCredit(issuerIdOpt.getAsInt());
        if (!issuerCreditOpt.isPresent()) {
            throw new CreditOperationException("Unable to read issuer credit balance.");
        }

        long issuerCredit = issuerCreditOpt.getAsLong();
        if (issuerCredit < amount) {
            throw new CreditOperationException("Issuer does not have enough credit.");
        }

        OptionalInt targetIdOpt = CreditRepository.findUserId(target);
        if (!targetIdOpt.isPresent()) {
            throw new CreditOperationException("Target account could not be located.");
        }

        int targetId = targetIdOpt.getAsInt();
        long remaining = issuerCredit - amount;
        if (!CreditRepository.updateCredit(issuerIdOpt.getAsInt(), remaining)) {
            throw new CreditOperationException("Failed to update issuer credit balance.");
        }
        CashManager.cacheCredit(issuer, remaining);

        ItemStack voucher = buildVoucherItem(target, targetId, amount);
        VoucherDetails details = new VoucherDetails(targetId, target, amount);
        return new VoucherIssueResult(voucher, details, remaining);
    }

    @Override
    public Optional<VoucherDetails> parseVoucher(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.PAPER) {
            return Optional.empty();
        }

        ItemMeta meta = itemStack.getItemMeta();
        List<String> lore = meta != null ? meta.getLore() : null;
        if (lore == null || lore.isEmpty()) {
            return Optional.empty();
        }

        Integer targetId = extractInteger(lore, CreditPaperManager.DATA_TARGET_ID);
        String targetName = extractString(lore, CreditPaperManager.DATA_TARGET_NAME);
        Long amount = extractLong(lore, CreditPaperManager.DATA_AMOUNT);
        if (targetId == null || amount == null || amount <= 0L || targetName == null || targetName.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new VoucherDetails(targetId, targetName, amount));
    }

    @Override
    public void redeemVoucher(VoucherDetails voucherDetails) throws CreditOperationException {
        if (voucherDetails == null) {
            throw new CreditOperationException("Voucher details are required.");
        }

        long amount = voucherDetails.getAmount();
        if (amount <= 0L) {
            throw new CreditOperationException("Voucher amount must be positive.");
        }

        OptionalLong creditOpt = CreditRepository.loadCredit(voucherDetails.getTargetId());
        long current = creditOpt.orElse(0L);
        long updated = current + amount;
        if (!CreditRepository.updateCredit(voucherDetails.getTargetId(), updated)) {
            throw new CreditOperationException("Failed to update target credit balance.");
        }
        CashManager.cacheCredit(voucherDetails.getTargetName(), updated);
    }

    private ItemStack buildVoucherItem(String targetName, int targetId, long amount) {
        ItemStack voucher = new ItemStack(Material.PAPER);
        ItemMeta meta = voucher.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LanguageManager.get(
                    CreditPaperManager.VOUCHER_NAME_KEY,
                    CreditPaperManager.DEFAULT_VOUCHER_NAME,
                    "amount", amount));

            List<String> lore = new ArrayList<>();
            lore.add(CreditPaperManager.DATA_TARGET_ID + targetId);
            lore.add(CreditPaperManager.DATA_TARGET_NAME + targetName);
            lore.add(CreditPaperManager.DATA_AMOUNT + amount);
            lore.addAll(LanguageManager.getList(
                    CreditPaperManager.VOUCHER_LORE_KEY,
                    CreditPaperManager.DEFAULT_VOUCHER_LORE,
                    "targetId", targetId,
                    "target", targetName,
                    "amount", amount));
            meta.setLore(lore);
            voucher.setItemMeta(meta);
        } else {
            log(Level.WARNING, "Unable to populate voucher metadata; ItemMeta was null.");
        }

        return voucher;
    }

    private Integer extractInteger(List<String> lore, String prefix) {
        String value = extractString(lore, prefix);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long extractLong(List<String> lore, String prefix) {
        String value = extractString(lore, prefix);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String extractString(List<String> lore, String prefix) {
        for (String line : lore) {
            if (line != null && line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void log(Level level, String message) {
        if (plugin != null) {
            plugin.getLogger().log(level, message);
        }
    }
}
