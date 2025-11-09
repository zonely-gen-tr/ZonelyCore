package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.store.dao.AuctionDao;
import dev.zonely.whiteeffect.store.model.AuctionItem;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.ItemSerializer;
import java.util.Arrays;
import java.util.Map;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MezatVerCommand implements CommandExecutor {

    private static final String PREFIX_KEY = "prefix.lobby";
    private static final String PREFIX_DEFAULT = "&3Lobby &8» ";

    private final Core plugin;
    private final AuctionDao dao;

    public MezatVerCommand(Core plugin) {
        this.plugin = plugin;
        this.dao = plugin.getAuctionMenuManager().getDao();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "commands.auctions.only-player",
                    "&cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 2 && "approve".equalsIgnoreCase(args[0])) {
            handleApprove(player, args[1]);
            return true;
        }

        if (args.length == 1) {
            handleSubmit(player, args[0], label);
            return true;
        }

        Profile profile = Profile.getProfile(player.getName());
        LanguageManager.send(player,
                "commands.auctions.submit.usage",
                "{prefix}&cUsage: /{label} <credits> or /{label} approve <id>",
                "prefix", LanguageManager.get(profile, PREFIX_KEY, PREFIX_DEFAULT),
                "label", label);
        return true;
    }

    private void handleApprove(Player player, String idArgument) {
        Profile profile = Profile.getProfile(player.getName());
        String prefix = LanguageManager.get(profile, PREFIX_KEY, PREFIX_DEFAULT);

        if (!player.hasPermission("zonely.auction.manage")) {
            LanguageManager.send(player,
                    "commands.auctions.approve.no-permission",
                    "{prefix}&cYou do not have permission to approve auctions.",
                    "prefix", prefix);
            return;
        }

        int auctionId;
        try {
            auctionId = Integer.parseInt(idArgument);
        } catch (NumberFormatException ex) {
            LanguageManager.send(player,
                    "commands.auctions.approve.invalid-id",
                    "{prefix}&cInvalid auction ID.",
                    "prefix", prefix);
            return;
        }

        try {
            AuctionItem item = dao.getById(auctionId);
            if (item == null) {
                LanguageManager.send(player,
                        "commands.auctions.approve.not-found",
                        "{prefix}&cAuction not found.",
                        "prefix", prefix,
                        "id", auctionId);
                return;
            }

            dao.markApproved(auctionId);

            int amount = resolveItemAmount(item);
            broadcastApproval(item, auctionId, amount);
        } catch (Exception ex) {
            plugin.getLogger().severe("Auction approve error: " + ex.getMessage());
            LanguageManager.send(player,
                    "commands.auctions.approve.error",
                    "{prefix}&cAn error occurred while approving the auction.",
                    "prefix", prefix);
        }
    }

    private void handleSubmit(Player player, String costArgument, String label) {
        Profile profile = Profile.getProfile(player.getName());
        String prefix = LanguageManager.get(profile, PREFIX_KEY, PREFIX_DEFAULT);

        if (!player.hasPermission("zonely.auction")) {
            LanguageManager.send(player,
                    "commands.auctions.submit.no-permission",
                    "{prefix}&cYou do not have permission to create auctions.",
                    "prefix", prefix);
            return;
        }

        int cost;
        try {
            cost = Integer.parseInt(costArgument);
        } catch (NumberFormatException ex) {
            LanguageManager.send(player,
                    "commands.auctions.submit.invalid-cost",
                    "{prefix}&cInvalid credit amount.",
                    "prefix", prefix);
            return;
        }

        ItemStack hand = BukkitUtils.getItemInHand(player);
        if (hand == null || hand.getType() == Material.AIR) {
            LanguageManager.send(player,
                    "commands.auctions.submit.no-item",
                    "{prefix}&cYou must hold an item to create an auction.",
                    "prefix", prefix);
            return;
        }

        try {
            AuctionItem item = buildAuctionItem(player, hand, cost);
            int auctionId = dao.insert(item);

            int amount = Math.max(1, hand.getAmount());
            BukkitUtils.setItemInHand(player, null);
            dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(player);

            LanguageManager.send(player,
                    "commands.auctions.submit.success",
                    "{prefix}&aAuction request created (ID: {id}, x{amount}).",
                    "prefix", prefix,
                    "id", auctionId,
                    "amount", amount);

            notifyApprovers(item, auctionId, amount);
        } catch (Exception ex) {
            plugin.getLogger().severe("Auction submit error: " + ex.getMessage());
            LanguageManager.send(player,
                    "commands.auctions.submit.error",
                    "{prefix}&cAn error occurred while submitting the auction.",
                    "prefix", prefix);
        }
    }

    private AuctionItem buildAuctionItem(Player owner, ItemStack stack, int credit) throws Exception {
        AuctionItem item = new AuctionItem();
        int userId = dao.fetchUserId(owner.getName());
        item.setCreatorId(userId);
        item.setUserId(userId);
        item.setMaterial(stack.getType().name());
        item.setCustomTitle(stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? stack.getItemMeta().getDisplayName()
                : stack.getType().name());
        item.setDescription("");

        StringBuilder enchBuilder = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            enchBuilder.append(entry.getKey().getName())
                    .append(":")
                    .append(entry.getValue())
                    .append(", ");
        }
        if (enchBuilder.length() > 0) {
            enchBuilder.setLength(enchBuilder.length() - 2);
        }
        item.setEnchantments(enchBuilder.toString());
        item.setDurabilityCurrent(stack.getDurability());
        item.setDurabilityMax(stack.getType().getMaxDurability());
        item.setItemData(ItemSerializer.serialize(stack));
        item.setCreditCost(credit);
        return item;
    }

    private void broadcastApproval(AuctionItem item, int auctionId, int amount) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Profile profile = Profile.getProfile(online.getName());
            String quantityText = amount > 1
                    ? LanguageManager.get(profile,
                    "commands.auctions.approve.quantity",
                    " x{amount}",
                    "amount", amount)
                    : "";

            TextComponent message = new TextComponent("");
            BaseComponent[] broadcast = TextComponent.fromLegacyText(
                    LanguageManager.get(profile,
                            "commands.auctions.approve.broadcast",
                            "&aAuction started: &f{title}{quantity} ",
                            "title", item.getCustomTitle(),
                            "quantity", quantityText));
            for (BaseComponent part : broadcast) {
                message.addExtra(part);
            }

            String buttonText = LanguageManager.get(profile,
                    "commands.auctions.approve.button",
                    "&a[BUY]");
            BaseComponent[] actionComponents = TextComponent.fromLegacyText(buttonText);
            ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/auctions buy " + auctionId);

            String hoverText = String.join("\n", LanguageManager.getList(profile,
                    "commands.auctions.approve.hover",
                    Arrays.asList(
                            "&7Price: &e{price} credits",
                            "&7Amount: &e{amount}",
                            "&7Description: &f{description}"),
                    "price", item.getCreditCost(),
                    "amount", amount,
                    "description", safeDescription(item)));
            BaseComponent[] hoverComponents = TextComponent.fromLegacyText(hoverText);
            HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents);

            for (BaseComponent component : actionComponents) {
                component.setClickEvent(click);
                component.setHoverEvent(hover);
                message.addExtra(component);
            }

            online.spigot().sendMessage(message);
        }
    }

    private void notifyApprovers(AuctionItem item, int auctionId, int amount) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.hasPermission("zonely.auction.manage")) {
                continue;
            }
            Profile profile = Profile.getProfile(target.getName());
            String quantityText = amount > 1
                    ? LanguageManager.get(profile,
                    "commands.auctions.approve.quantity",
                    " x{amount}",
                    "amount", amount)
                    : "";

            TextComponent message = new TextComponent("");
            BaseComponent[] introComponents = TextComponent.fromLegacyText(
                    LanguageManager.get(profile,
                            "commands.auctions.notify.new",
                            "&eNew auction request [{id}] {title}{quantity} ",
                            "id", auctionId,
                            "title", item.getCustomTitle(),
                            "quantity", quantityText));
            for (BaseComponent component : introComponents) {
                message.addExtra(component);
            }

            String buttonText = LanguageManager.get(profile,
                    "commands.auctions.notify.button",
                    "&a[APPROVE]");
            BaseComponent[] approveComponents = TextComponent.fromLegacyText(buttonText);
            ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/auction approve " + auctionId);
            BaseComponent[] hoverComponents = TextComponent.fromLegacyText(String.join("\n",
                    LanguageManager.getList(profile,
                            "commands.auctions.notify.hover",
                            Arrays.asList("&7Click to approve this auction."))));
            HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents);

            for (BaseComponent component : approveComponents) {
                component.setClickEvent(click);
                component.setHoverEvent(hover);
                message.addExtra(component);
            }

            target.spigot().sendMessage(message);
        }
    }

    private int resolveItemAmount(AuctionItem item) {
        try {
            ItemStack stack = ItemSerializer.deserialize(item.getItemData());
            if (stack != null && stack.getAmount() > 0) {
                return stack.getAmount();
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private String safeDescription(AuctionItem item) {
        String description = item.getDescription();
        return description == null || description.isEmpty() ? "-" : description;
    }
}


