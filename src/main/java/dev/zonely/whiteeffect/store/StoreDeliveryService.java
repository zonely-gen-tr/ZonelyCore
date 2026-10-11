package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.Core;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class StoreDeliveryService {
    private final Core plugin;
    private final StorePurchaseService purchases;
    private final StoreDeliveryRepository repository;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean ready, stopped;
    private volatile int serverId;
    private BukkitTask timer;
    public StoreDeliveryService(Core plugin, StorePurchaseService purchases, StorePurchaseService.Connections connections) {
        this.plugin=plugin; this.purchases=purchases; this.repository=new StoreDeliveryRepository(connections);
    }
    public boolean isReady() { return ready && serverId > 0 && !stopped; }
    public int getServerId() { return serverId; }
    public void start() {
        timer=Bukkit.getScheduler().runTaskTimer(plugin, this::poll, 20L, 40L);
    }
    private void poll() {
        if(stopped || !busy.compareAndSet(false,true)) return;
        Set<String> online=new HashSet<>();
        for(Player player:Bukkit.getOnlinePlayers()) online.add(player.getName().toLowerCase(Locale.ROOT));
        final boolean requireOnline=plugin.getConfig().getBoolean("store.require-player-online",true);
        final String token=plugin.getConfig().getString("web-token","");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if(!ready) {
                    purchases.initialize();
                    serverId=repository.resolveServer(token);
                    ready=true;
                    if(serverId==0) plugin.getLogger().warning("[Store] Set web-token to this server's unique panel connection token. Purchases are disabled until exactly one panel server matches.");
                    else plugin.getLogger().info("[Store] Delivery receiver ready for panel server #"+serverId);
                } else if(serverId==0) serverId=repository.resolveServer(token);
                if(serverId==0 || stopped) { busy.set(false); return; }
                List<StoreDeliveryRepository.Delivery> rows=repository.claim(serverId,online,requireOnline,100);
                if(stopped || !plugin.isEnabled()) {
                    for(StoreDeliveryRepository.Delivery row:rows) repository.complete(row.id,"PENDING","Receiver stopped before dispatch");
                    busy.set(false); return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> dispatch(rows,requireOnline));
            } catch(Exception e) {
                ready=false; busy.set(false);
                plugin.getLogger().log(Level.WARNING,"[Store] Delivery polling failed: "+e.getClass().getSimpleName());
            }
        });
    }
    private void dispatch(List<StoreDeliveryRepository.Delivery> rows, boolean requireOnline) {
        List<String> statuses=new ArrayList<>();
        for(StoreDeliveryRepository.Delivery row:rows) {
            Player player=Bukkit.getPlayerExact(row.username);
            String status;
            if(stopped || (requireOnline && (player==null || !player.isOnline()))) status="PENDING";
            else {
                try { status=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),row.command)?"DONE":"FAILED"; }
                catch(RuntimeException e) { status="FAILED"; }
                if("FAILED".equals(status)) plugin.getLogger().warning("[Store] Delivery #"+row.id+" failed on server #"+serverId+". Check the product command and installed plugin.");
            }
            statuses.add(status);
        }
        if(!plugin.isEnabled()) { busy.set(false); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for(int i=0;i<rows.size();i++) repository.complete(rows.get(i).id,statuses.get(i),statuses.get(i));
            } catch(Exception e) {
                plugin.getLogger().warning("[Store] Delivery acknowledgement failed; inspect CLAIMED deliveries before retrying.");
            } finally { busy.set(false); }
        });
    }
    public void shutdown() { stopped=true; if(timer!=null)timer.cancel(); }
}
