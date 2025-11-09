package dev.zonely.whiteeffect.api.voucher;


public final class VoucherDetails {

    private final int targetId;
    private final String targetName;
    private final long amount;

    public VoucherDetails(int targetId, String targetName, long amount) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.amount = amount;
    }

    public int getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public long getAmount() {
        return amount;
    }
}
