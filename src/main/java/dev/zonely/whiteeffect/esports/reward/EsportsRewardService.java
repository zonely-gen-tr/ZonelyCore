package dev.zonely.whiteeffect.esports.reward;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EsportsRewardService {

    private final Core plugin;
    private final Set<String> grantedKeys = ConcurrentHashMap.newKeySet();

    public EsportsRewardService(Core plugin) {
        this.plugin = plugin;
    }

    public void grantPlacementReward(EsportsTournamentData tournament,
                                     EsportsClubEntry club,
                                     int placement,
                                     Collection<Player> recipients) {
        RewardRules rules = RewardRules.parse(tournament.getRewards());
        if (rules.isEmpty() || placement <= 0) {
            return;
        }
        RewardBucket bucket = rules.findPlacementBucket(placement);
        if (bucket == null) {
            return;
        }
        String cacheKey = tournament.getId() + ":" + club.getClubId() + ":" + placement;
        if (!grantedKeys.add(cacheKey)) {
            return;
        }
        applyBucket(bucket, tournament, club, placement, recipients);
    }

    public void grantParticipationReward(EsportsTournamentData tournament,
                                         Collection<Player> recipients) {
        RewardRules rules = RewardRules.parse(tournament.getRewards());
        if (rules.isEmpty() || rules.getParticipationBucket() == null) {
            return;
        }
        applyBucket(rules.getParticipationBucket(), tournament, null, 0, recipients);
    }

    public void resetCache() {
        grantedKeys.clear();
    }

    private void applyBucket(RewardBucket bucket,
                             EsportsTournamentData tournament,
                             EsportsClubEntry club,
                             int placement,
                             Collection<Player> recipients) {
        RewardContext context = new RewardContext(tournament, club, placement);
        boolean hasRecipients = recipients != null && !recipients.isEmpty();
        bucket.applyGlobal(plugin, context);
        if (!hasRecipients) {
            plugin.getLogger().info("[Esports] Reward bucket applied but no online players matched. bucket=" + bucket.getDebugName());
            return;
        }
        for (Player player : recipients) {
            bucket.applyPerPlayer(plugin, player, context);
        }
    }

    private static final class RewardRules {
        private final List<RewardBucket> placementBuckets;
        private final RewardBucket participationBucket;

        private RewardRules(List<RewardBucket> placementBuckets, RewardBucket participationBucket) {
            this.placementBuckets = placementBuckets;
            this.participationBucket = participationBucket;
        }

        static RewardRules parse(JsonObject root) {
            if (root == null) {
                return new RewardRules(Collections.emptyList(), null);
            }
            List<RewardBucket> placement = new ArrayList<>();
            if (root.has("placements") && root.get("placements").isJsonArray()) {
                JsonArray array = root.getAsJsonArray("placements");
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) continue;
                    RewardBucket bucket = RewardBucket.fromJson(element.getAsJsonObject());
                    if (bucket != null) {
                        placement.add(bucket);
                    }
                }
            }
            RewardBucket participation = null;
            if (root.has("participation") && root.get("participation").isJsonObject()) {
                participation = RewardBucket.fromJson(root.getAsJsonObject("participation"));
            }
            return new RewardRules(placement, participation);
        }

        RewardBucket findPlacementBucket(int placement) {
            for (RewardBucket bucket : placementBuckets) {
                if (bucket.matches(placement)) {
                    return bucket;
                }
            }
            return null;
        }

        RewardBucket getParticipationBucket() {
            return participationBucket;
        }

        boolean isEmpty() {
            return placementBuckets.isEmpty() && participationBucket == null;
        }
    }

    private static final class RewardBucket {
        private final int minPlacement;
        private final int maxPlacement;
        private final List<RewardAction> perPlayerActions;
        private final List<RewardAction> globalActions;
        private final String debugName;

        private RewardBucket(int minPlacement,
                             int maxPlacement,
                             List<RewardAction> perPlayerActions,
                             List<RewardAction> globalActions,
                             String debugName) {
            this.minPlacement = minPlacement;
            this.maxPlacement = maxPlacement;
            this.perPlayerActions = perPlayerActions;
            this.globalActions = globalActions;
            this.debugName = debugName;
        }

        static RewardBucket fromJson(JsonObject json) {
            if (json == null) {
                return null;
            }
            int min = json.has("from") ? json.get("from").getAsInt() :
                    json.has("place") ? json.get("place").getAsInt() : 0;
            int max = json.has("to") ? json.get("to").getAsInt() : (json.has("place") ? json.get("place").getAsInt() : min);

            List<RewardAction> perPlayer = new ArrayList<>();
            List<RewardAction> global = new ArrayList<>();

            extractCommands(json, perPlayer);
            extractMessages(json, perPlayer, global);
            extractItems(json, perPlayer);

            if (perPlayer.isEmpty() && global.isEmpty()) {
                return null;
            }
            String debug = json.has("title") ? json.get("title").getAsString() : "placement:" + min + "-" + max;
            return new RewardBucket(min, max, perPlayer, global, debug);
        }

        private static void extractCommands(JsonObject json, List<RewardAction> perPlayer) {
            if (!json.has("commands")) {
                return;
            }
            JsonArray commands = json.getAsJsonArray("commands");
            for (JsonElement entry : commands) {
                if (!entry.isJsonPrimitive()) continue;
                String command = entry.getAsString();
                if (command.isEmpty()) continue;
                perPlayer.add(new CommandRewardAction(command));
            }
        }

        private static void extractMessages(JsonObject json,
                                            List<RewardAction> perPlayer,
                                            List<RewardAction> global) {
            if (json.has("messages") && json.get("messages").isJsonArray()) {
                for (JsonElement entry : json.getAsJsonArray("messages")) {
                    if (!entry.isJsonPrimitive()) continue;
                    perPlayer.add(new MessageRewardAction(entry.getAsString(), false));
                }
            }
            if (json.has("announce") && json.get("announce").isJsonArray()) {
                for (JsonElement entry : json.getAsJsonArray("announce")) {
                    if (!entry.isJsonPrimitive()) continue;
                    global.add(new MessageRewardAction(entry.getAsString(), true));
                }
            }
        }

        private static void extractItems(JsonObject json, List<RewardAction> perPlayer) {
            if (!json.has("items") || !json.get("items").isJsonArray()) {
                return;
            }
            for (JsonElement entry : json.getAsJsonArray("items")) {
                if (!entry.isJsonObject()) continue;
                JsonObject obj = entry.getAsJsonObject();
                ItemStack item = deserializeItem(obj);
                if (item != null) {
                    perPlayer.add(new ItemRewardAction(item));
                }
            }
        }

        private static ItemStack deserializeItem(JsonObject obj) {
            if (obj.has("definition")) {
                String definition = obj.get("definition").getAsString();
                return BukkitUtils.deserializeItemStack(definition);
            }
            if (!obj.has("material")) {
                return null;
            }
            String material = obj.get("material").getAsString();
            int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1;
            StringBuilder builder = new StringBuilder(material)
                    .append(" : ")
                    .append(amount);
            if (obj.has("name")) {
                builder.append(" : name>").append(obj.get("name").getAsString());
            }
            if (obj.has("lore")) {
                JsonArray lore = obj.getAsJsonArray("lore");
                List<String> lines = new ArrayList<>();
                for (JsonElement line : lore) {
                    if (!line.isJsonPrimitive()) continue;
                    lines.add(line.getAsString());
                }
                if (!lines.isEmpty()) {
                    builder.append(" : desc>").append(String.join("\\n", lines));
                }
            }
            return BukkitUtils.deserializeItemStack(builder.toString());
        }

        boolean matches(int placement) {
            if (minPlacement == 0 && maxPlacement == 0) {
                return true; // participation bucket
            }
            if (placement <= 0) return false;
            return placement >= minPlacement && placement <= maxPlacement;
        }

        void applyGlobal(Core plugin, RewardContext context) {
            for (RewardAction action : globalActions) {
                action.execute(plugin, null, context);
            }
        }

        void applyPerPlayer(Core plugin, Player player, RewardContext context) {
            for (RewardAction action : perPlayerActions) {
                action.execute(plugin, player, context);
            }
        }

        String getDebugName() {
            return debugName;
        }
    }

    private interface RewardAction {
        void execute(Core plugin, Player player, RewardContext context);
    }

    private static final class CommandRewardAction implements RewardAction {
        private final String rawCommand;

        private CommandRewardAction(String rawCommand) {
            this.rawCommand = rawCommand;
        }

        @Override
        public void execute(Core plugin, Player player, RewardContext context) {
            if (player == null) {
                return;
            }
            String parsed = format(rawCommand, player, context);
            ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
            Bukkit.dispatchCommand(console, parsed);
        }
    }

    private static final class MessageRewardAction implements RewardAction {
        private final String message;
        private final boolean broadcast;

        private MessageRewardAction(String message, boolean broadcast) {
            this.message = message;
            this.broadcast = broadcast;
        }

        @Override
        public void execute(Core plugin, Player player, RewardContext context) {
            String parsed = ChatColor.translateAlternateColorCodes('&', format(message, player, context));
            if (broadcast) {
                Bukkit.getServer().broadcastMessage(parsed);
                return;
            }
            if (player != null) {
                player.sendMessage(parsed);
            }
        }
    }

    private static final class ItemRewardAction implements RewardAction {
        private final ItemStack template;

        private ItemRewardAction(ItemStack template) {
            this.template = template;
        }

        @Override
        public void execute(Core plugin, Player player, RewardContext context) {
            if (player == null || template == null) {
                return;
            }
            ItemStack stack = template.clone();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
    }

    private static String format(String template, Player player, RewardContext context) {
        if (template == null) {
            return "";
        }
        String result = template;
        Map<String, String> replacements = context.placeholders(player);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static final class RewardContext {
        private final EsportsTournamentData tournament;
        private final EsportsClubEntry club;
        private final int placement;

        private RewardContext(EsportsTournamentData tournament,
                              EsportsClubEntry club,
                              int placement) {
            this.tournament = tournament;
            this.club = club;
            this.placement = placement;
        }

        Map<String, String> placeholders(Player player) {
            String playerName = player != null ? player.getName() : "";
            String clubName = club != null ? Objects.toString(club.getName(), "") : "";
            String clubTag = club != null ? Objects.toString(club.getTag(), "") : "";
            String tournamentName = tournament != null ? Objects.toString(tournament.getName(), "") : "";

            Map<String, String> replacements = new HashMap<String, String>();
            replacements.put("player", playerName);
            replacements.put("playerName", playerName);
            replacements.put("clubName", clubName);
            replacements.put("clubTag", clubTag);
            replacements.put("placement", placement > 0 ? String.valueOf(placement) : "");
            replacements.put("tournamentName", tournamentName);
            return replacements;
        }
    }
}
