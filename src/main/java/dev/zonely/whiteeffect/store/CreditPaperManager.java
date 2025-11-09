package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.MenuInventoryFactory;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.cash.CreditRepository;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CreditPaperManager implements Listener, CommandExecutor {

    private static final String PREFIX_KEY = "prefix.lobby";
    private static final String DEFAULT_PREFIX = "&3Lobby &8» ";

    private static final String CONFIRM_TITLE_KEY = "menus.credit-voucher.confirm.title";
    private static final String DEFAULT_CONFIRM_TITLE = "&8Credit Voucher Confirmation";
    private static final String CONFIRM_ACCEPT_KEY = "menus.credit-voucher.confirm.accept";
    private static final String DEFAULT_CONFIRM_ACCEPT = "&aConfirm";
    private static final String CONFIRM_CANCEL_KEY = "menus.credit-voucher.confirm.cancel";
    private static final String DEFAULT_CONFIRM_CANCEL = "&cCancel";

    public static final String VOUCHER_NAME_KEY = "items.credit-voucher.name";
    public static final String DEFAULT_VOUCHER_NAME = "&aCredit Voucher ({amount})";
    public static final String VOUCHER_LORE_KEY = "items.credit-voucher.lore";
    public static final List<String> DEFAULT_VOUCHER_LORE = Arrays.asList(
            "&7Target: {target}",
            "&7Target ID: {targetId}",
            "&7Amount: {amount}",
            "&aRight-click to confirm."
    );

    public static final String DATA_PREFIX = ChatColor.COLOR_CHAR + "0voucher:";
    public static final String DATA_TARGET_ID = DATA_PREFIX + "target-id:";
    public static final String DATA_TARGET_NAME = DATA_PREFIX + "target-name:";
    public static final String DATA_AMOUNT = DATA_PREFIX + "amount:";

    private final Core plugin;

    public CreditPaperManager(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("creditvoucher").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.isVouchersEnabled()) {
            LanguageManager.send(sender, "commands.credit-voucher.disabled",
                    "{prefix}&cCredit vouchers are disabled.",
                    "prefix", LanguageManager.get("prefix.lobby", DEFAULT_PREFIX));
            return true;
        }
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender, "commands.credit-voucher.console-only",
                    "&cThis command can only be used in-game.");
            return true;
        }

        Player player = (Player) sender;
        Profile profile = Profile.getProfile(player.getName());
        String prefix = LanguageManager.get(profile, PREFIX_KEY, DEFAULT_PREFIX);

        if (args.length != 2) {
            LanguageManager.send(player, "commands.credit-voucher.usage",
                    "{prefix}&eUsage: /creditvoucher <player> <amount>", "prefix", prefix);
            return true;
        }

        String targetName = args[0];
        String normalizedTarget = normalizeNick(targetName);
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            LanguageManager.send(player, "commands.credit-voucher.invalid-amount",
                    "{prefix}&cInvalid amount.", "prefix", prefix);
            return true;
        }

        Integer giverId = fetchUserId(normalizeNick(player.getName()));
        if (giverId == null) {
            LanguageManager.send(player, "commands.credit-voucher.missing-giver-id",
                    "{prefix}&cYour account ID could not be found.", "prefix", prefix);
            return true;
        }

        Long giverCredit = fetchCredit(giverId);
        if (giverCredit == null) {
            LanguageManager.send(player, "commands.credit-voucher.giver-credit-read-failed",
                    "{prefix}&cFailed to read your credit balance.", "prefix", prefix);
            return true;
        }

        if (giverCredit < amount) {
            LanguageManager.send(player, "commands.credit-voucher.not-enough-credit",
                    "{prefix}&cYou do not have enough credit.", "prefix", prefix);
            return true;
        }

        Integer targetId = fetchUserId(normalizedTarget);
        if (targetId == null) {
            LanguageManager.send(player, "commands.credit-voucher.target-not-found",
                    "{prefix}&cTarget player not found.", "prefix", prefix,
                    "target", normalizedTarget);
            return true;
        }

        if (!updateCredit(giverId, giverCredit - amount)) {
            LanguageManager.send(player, "commands.credit-voucher.deduct-failed",
                    "{prefix}&cUnable to deduct credit.", "prefix", prefix);
            return true;
        }
        CashManager.cacheCredit(player.getName(), giverCredit - amount);

        ItemStack voucher = new ItemStack(Material.PAPER);
        ItemMeta meta = voucher.getItemMeta();
        meta.setDisplayName(LanguageManager.get(profile, VOUCHER_NAME_KEY, DEFAULT_VOUCHER_NAME,
                "amount", amount));
        List<String> lore = new ArrayList<>();
        lore.add(DATA_TARGET_ID + targetId);
        lore.add(DATA_TARGET_NAME + normalizedTarget);
        lore.add(DATA_AMOUNT + amount);
        lore.addAll(LanguageManager.getList(profile, VOUCHER_LORE_KEY, DEFAULT_VOUCHER_LORE,
                "targetId", targetId,
                "target", normalizedTarget,
                "amount", amount));
        meta.setLore(lore);
        voucher.setItemMeta(meta);

        player.getInventory().addItem(voucher);
        LanguageManager.send(player, "commands.credit-voucher.issued",
                "{prefix}&aVoucher issued. Removed {amount} credits. Remaining balance: {remaining}.",
                "prefix", prefix,
                "amount", amount,
                "remaining", giverCredit - amount,
                "target", normalizedTarget);
        return true;
    }

    private String normalizeNick(String name) {
        return name == null ? "" : name.trim();
    }

    @EventHandler
    public void onUsePaper(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        if (lore == null || lore.isEmpty() || lore.stream().noneMatch(line -> line != null && line.startsWith(DATA_PREFIX))) {
            return;
        }

        event.setCancelled(true);

        Profile profile = Profile.getProfile(event.getPlayer().getName());
        String title = LanguageManager.get(profile, CONFIRM_TITLE_KEY, DEFAULT_CONFIRM_TITLE);
        Inventory confirm = MenuInventoryFactory.create(null, 27, title);
        ItemStack accept = MaterialResolver.createItemStack("STAINED_CLAY:13");
        ItemMeta acceptMeta = accept.getItemMeta();
        acceptMeta.setDisplayName(LanguageManager.get(profile, CONFIRM_ACCEPT_KEY, DEFAULT_CONFIRM_ACCEPT));
        accept.setItemMeta(acceptMeta);
        confirm.setItem(15, accept);

        ItemStack decline = MaterialResolver.createItemStack("STAINED_CLAY:14");
        ItemMeta declineMeta = decline.getItemMeta();
        declineMeta.setDisplayName(LanguageManager.get(profile, CONFIRM_CANCEL_KEY, DEFAULT_CONFIRM_CANCEL));
        decline.setItemMeta(declineMeta);
        confirm.setItem(11, decline);

        event.getPlayer().openInventory(confirm);
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getOpenPapers().put(event.getPlayer().getUniqueId(), item.clone()));
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Profile profile = Profile.getProfile(player.getName());
        String expectedTitle = LanguageManager.get(profile, CONFIRM_TITLE_KEY, DEFAULT_CONFIRM_TITLE);
        if (!event.getView().getTitle().equals(expectedTitle)) {
            return;
        }

        event.setCancelled(true);
        ItemStack paper = plugin.getOpenPapers().remove(player.getUniqueId());
        if (paper == null) {
            player.closeInventory();
            return;
        }

        ItemMeta meta = paper.getItemMeta();
        int targetId = extractIntegerLore(meta.getLore(), DATA_TARGET_ID);
        String targetNick = extractStringLore(meta.getLore(), DATA_TARGET_NAME);
        int amount = extractIntegerLore(meta.getLore(), DATA_AMOUNT);
        String prefix = LanguageManager.get(profile, PREFIX_KEY, DEFAULT_PREFIX);
        if (targetId <= 0 || amount <= 0 || targetNick == null || targetNick.isEmpty()) {
            LanguageManager.send(player, "commands.credit-voucher.invalid-data",
                    "{prefix}&cVoucher data is invalid.", "prefix", prefix);
            player.getInventory().addItem(paper);
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 11) {
            Integer giverId = fetchUserId(normalizeNick(player.getName()));
            if (giverId != null) {
                Long credit = fetchCredit(giverId);
                if (credit != null) {
                    updateCredit(giverId, credit + amount);
                    CashManager.cacheCredit(player.getName(), credit + amount);
                }
            }
            player.getInventory().addItem(paper);
            player.closeInventory();
            return;
        }

        if (slot == 15) {
            Long credit = fetchCredit(targetId);
            if (credit == null) {
                LanguageManager.send(player, "commands.credit-voucher.target-credit-read-failed",
                        "{prefix}&cCould not read the target credit balance.", "prefix", prefix,
                        "target", targetNick);
                player.getInventory().addItem(paper);
            } else if (updateCredit(targetId, credit + amount)) {
                CashManager.cacheCredit(targetNick, credit + amount);

                LanguageManager.send(player, "commands.credit-voucher.target-credit-updated",
                        "{prefix}&aCredit added for {target}. New total: {total}.",
                        "prefix", prefix,
                        "target", targetNick,
                        "total", credit + amount);
                player.getInventory().removeItem(paper);
            } else {
                LanguageManager.send(player, "commands.credit-voucher.target-credit-update-failed",
                        "{prefix}&cUnable to update target credit.", "prefix", prefix,
                        "target", targetNick);
                player.getInventory().addItem(paper);
            }
        } else {
            player.getInventory().addItem(paper);
        }

        player.closeInventory();
    }

    private Integer fetchUserIdByArea(String value) {
        return fetchUserId(normalizeNick(value));
    }

    private Integer fetchUserId(String nick) {
        java.util.OptionalInt userId = CreditRepository.findUserId(nick);
        return userId.isPresent() ? userId.getAsInt() : null;
    }

    private Long fetchCredit(int userId) {
        java.util.OptionalLong credit = CreditRepository.loadCredit(userId);
        return credit.isPresent() ? credit.getAsLong() : null;
    }

    private boolean updateCredit(int userId, long newCredit) {
        return CreditRepository.updateCredit(userId, newCredit);
    }

    private String extractStringLore(List<String> lore, String prefix) {
        if (lore == null) {
            return null;
        }
        for (String line : lore) {
            if (line != null && line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private int extractIntegerLore(List<String> lore, String prefix) {
        if (lore == null) {
            return -1;
        }
        for (String line : lore) {
            if (line != null && line.startsWith(prefix)) {
                String value = line.substring(prefix.length()).trim();
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

}
