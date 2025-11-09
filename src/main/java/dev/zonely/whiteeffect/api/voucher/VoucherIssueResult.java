package dev.zonely.whiteeffect.api.voucher;

import org.bukkit.inventory.ItemStack;


public final class VoucherIssueResult {

    private final ItemStack voucherItem;
    private final VoucherDetails details;
    private final long issuerRemainingBalance;

    public VoucherIssueResult(ItemStack voucherItem, VoucherDetails details, long issuerRemainingBalance) {
        this.voucherItem = voucherItem;
        this.details = details;
        this.issuerRemainingBalance = issuerRemainingBalance;
    }

    public ItemStack getVoucherItem() {
        return voucherItem;
    }

    public VoucherDetails getDetails() {
        return details;
    }

    public long getIssuerRemainingBalance() {
        return issuerRemainingBalance;
    }
}
