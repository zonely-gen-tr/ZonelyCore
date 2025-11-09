package dev.zonely.whiteeffect.api;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.api.cash.CashAPI;
import dev.zonely.whiteeffect.api.cash.impl.CashApiImpl;
import dev.zonely.whiteeffect.api.deliveries.DeliveriesAPI;
import dev.zonely.whiteeffect.api.deliveries.impl.DeliveriesApiImpl;
import dev.zonely.whiteeffect.api.lastcredit.LastCreditAPI;
import dev.zonely.whiteeffect.api.lastcredit.impl.LastCreditApiImpl;
import dev.zonely.whiteeffect.api.socket.SocketAPI;
import dev.zonely.whiteeffect.api.socket.impl.SocketApiImpl;
import dev.zonely.whiteeffect.api.voucher.VoucherAPI;
import dev.zonely.whiteeffect.api.voucher.impl.VoucherApiImpl;
import dev.zonely.whiteeffect.api.auction.AuctionAPI;
import dev.zonely.whiteeffect.api.auction.impl.AuctionApiImpl;


public final class ZonelyCoreAPI {

    private static volatile ZonelyCoreAPI instance;

    private final Core plugin;
    private final CashAPI cashApi;
    private final VoucherAPI voucherApi;
    private final DeliveriesAPI deliveriesApi;
    private final LastCreditAPI lastCreditApi;
    private final SocketAPI socketApi;
    private final AuctionAPI auctionApi;

    public ZonelyCoreAPI(Core plugin) {
        this.plugin = plugin;
        this.cashApi = new CashApiImpl();
        this.voucherApi = new VoucherApiImpl(plugin);
        this.deliveriesApi = new DeliveriesApiImpl();
        this.lastCreditApi = new LastCreditApiImpl(plugin);
        this.socketApi = new SocketApiImpl(plugin);
        this.auctionApi = new AuctionApiImpl(plugin);
        instance = this;
    }

    public static ZonelyCoreAPI get() {
        ZonelyCoreAPI api = instance;
        if (api != null) {
            return api;
        }

        Core core = Core.getInstance();
        if (core == null) {
            throw new IllegalStateException("ZonelyCore is not initialised yet.");
        }

        api = core.getApi();
        if (api == null) {
            throw new IllegalStateException("ZonelyCore API is not ready yet.");
        }

        return api;
    }

    public Core getPlugin() {
        return plugin;
    }

    public CashAPI cash() {
        return cashApi;
    }

    public VoucherAPI vouchers() {
        return voucherApi;
    }

    public DeliveriesAPI deliveries() {
        return deliveriesApi;
    }

    public LastCreditAPI lastCredits() {
        return lastCreditApi;
    }

    public SocketAPI socket() {
        return socketApi;
    }

    public AuctionAPI auctions() {
        return auctionApi;
    }

    public boolean isSocketEnabled() {
        return plugin.isSocketEnabled();
    }

    public boolean areVouchersEnabled() {
        return plugin.isVouchersEnabled();
    }

    public boolean areAuctionsEnabled() {
        return plugin.isAuctionsEnabled();
    }

    public boolean areReportsEnabled() {
        return plugin.isReportsEnabled();
    }

    public boolean areLastCreditsEnabled() {
        return plugin.isLastCreditsEnabled();
    }

    public static void reset() {
        instance = null;
    }
}
