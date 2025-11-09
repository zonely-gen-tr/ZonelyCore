package dev.zonely.whiteeffect.api.voucher;

import dev.zonely.whiteeffect.api.exception.CreditOperationException;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public interface VoucherAPI {

    VoucherIssueResult issueVoucher(String issuerName, String targetName, long amount) throws CreditOperationException;

    Optional<VoucherDetails> parseVoucher(ItemStack itemStack);

    default boolean isVoucher(ItemStack itemStack) {
        return parseVoucher(itemStack).isPresent();
    }

    void redeemVoucher(VoucherDetails voucherDetails) throws CreditOperationException;

    default void redeemVoucher(ItemStack itemStack) throws CreditOperationException {
        Optional<VoucherDetails> details = parseVoucher(itemStack);
        if (!details.isPresent()) {
            throw new CreditOperationException("Provided item is not a Zonely credit voucher.");
        }
        redeemVoucher(details.get());
    }
}
