package dev.zonely.whiteeffect.esports.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.CreationSession.PotionSetting;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnPoint;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentDraft;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.InputTarget;
import dev.zonely.whiteeffect.utils.JsonUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.menu.ChatPrompt;
import dev.zonely.whiteeffect.utils.menu.SignPrompt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class EsportsCreationService implements Listener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MIN_ROUNDS = 1;
    private static final int MAX_ROUNDS = 4;
    private static final int MIN_MATCH_CLUBS = 2;
    private static final int MAX_MATCH_CLUBS = 8;

    private final Core plugin;
    private final EsportsManager manager;
    private final Map<UUID, CreationSession> sessions = new ConcurrentHashMap<>();
    private final ItemStack guideItem;
    private final String guideName;
    private final double autoSpawnRadius;

    public EsportsCreationService(Core plugin, EsportsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.guideName = ChatColor.LIGHT_PURPLE + EsportsLang.get("creation.guide.name", "Tournament Settings");
        this.guideItem = buildGuideItem();
        double radius = 6.0D;
        if (manager != null && manager.getSettings() != null && manager.getSettings().getArenaSettings() != null) {
            radius = manager.getSettings().getArenaSettings().getAutoSpawnRadius();
        }
        this.autoSpawnRadius = Math.max(2.0D, radius);
    }

    static String placementLabel(int placement) {
        switch (placement) {
            case 1:
                return EsportsLang.get("creation.placement.first", "1st Place");
            case 2:
                return EsportsLang.get("creation.placement.second", "2nd Place");
            case 3:
                return EsportsLang.get("creation.placement.third", "3rd Place");
            default:
                return EsportsLang.get("creation.placement.generic", "#{placement}", "placement", placement);
        }
    }

    public boolean begin(Player player) {
        CreationSession session = sessions.computeIfAbsent(player.getUniqueId(),
                uuid -> new CreationSession(uuid, player.getName()));
        player.sendMessage(EsportsLang.get("creation.begin.started", "&bTournament setup wizard started."));
        player.sendMessage(EsportsLang.get("creation.begin.abort", "&7Use /esports admin cancel to abort."));
        ensureGuideItem(player);
        player.sendMessage(EsportsLang.get("creation.begin.right-click",
                "&eRight-click the '{item}' item to continue.",
                "item", guideName + ChatColor.YELLOW));
        if (!session.hasName()) {
            player.sendMessage(EsportsLang.get("creation.begin.first-step",
                    "&7First step: enter the tournament name."));
        }
        return true;
    }

    public void cancel(Player player, boolean notify) {
        CreationSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.clearPendingInput();
            if (notify) {
                player.sendMessage(EsportsLang.get("creation.cancelled", "&cTournament setup session cancelled."));
            }
            removeGuideItem(player);
        }
    }

    void openWizard(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsTournamentWizardMenu(plugin, player, this, session));
    }

    void openEventMenu(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsTournamentEventMenu(plugin, player, this, session));
    }

    void openSpawnMenu(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsTournamentSpawnMenu(plugin, player, this, session));
    }

    void openPotionMenu(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsTournamentPotionMenu(plugin, player, this, session));
    }

    void openStartMenu(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsTournamentStartMenu(plugin, player, this, session));
    }

    void openKitEditor(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsKitMenu(
                plugin,
                player,
                null,
                session.getKitConfig(),
                payload -> {
                    session.setKitConfig(payload);
                    player.sendMessage(EsportsLang.get("creation.kit-updated", "&aTournament kit updated."));
                    openWizard(player, session);
                    return true;
                },
                true,
                ChatColor.DARK_GRAY + "Tournament Kit"
        ));
    }

    void openRewardMenu(Player player, CreationSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> new EsportsTournamentRewardMenu(plugin, player, this, session));
    }

    void requestRewardCommands(Player player, CreationSession session, int placement) {
        if (player == null || session == null) {
            return;
        }
        String label = placementLabel(placement);
        String existing = session.getPlacementCommands(placement).isEmpty()
                ? EsportsLang.get("creation.commands.none", "&7No commands set.")
                : EsportsLang.get("creation.commands.current-count",
                "&7Current commands: {count}",
                "count", session.getPlacementCommands(placement).size());
        player.sendMessage(existing);
        ChatPrompt.request(player,
                EsportsLang.get("creation.commands.prompt.title",
                        "&eEnter commands for {placement} separated by ';'",
                        "placement", label),
                EsportsLang.get("creation.commands.prompt.example",
                        "&7Example: eco give {player} 5000; crate give {player} Champion 1")
                        + "\\n" + EsportsLang.get("creation.commands.prompt.clear", "&7Type 'clear' to reset."),
                input -> {
                    handleRewardInput(session, placement, input);
                    Bukkit.getScheduler().runTask(plugin, () -> openRewardMenu(player, session));
                },
                () -> Bukkit.getScheduler().runTask(plugin, () -> openRewardMenu(player, session)));
    }

    private void handleRewardInput(CreationSession session, int placement, String rawInput) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        String value = rawInput == null ? "" : rawInput.trim();
        if (value.equalsIgnoreCase("cancel")) {
            player.sendMessage(EsportsLang.get("creation.commands.cancelled", "&eCommand input cancelled."));
            return;
        }
        if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
            session.clearPlacementCommands(placement);
            player.sendMessage(EsportsLang.get("creation.commands.cleared",
                    "&eCommands for {placement} were cleared.",
                    "placement", placementLabel(placement)));
            return;
        }
        List<String> commands = Arrays.stream(value.split("[;\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (commands.isEmpty()) {
            session.clearPlacementCommands(placement);
            player.sendMessage(EsportsLang.get("creation.commands.none-valid",
                    "&eNo valid commands found for {placement}. Cleared.",
                    "placement", placementLabel(placement)));
            return;
        }
        session.setPlacementCommands(placement, commands);
        player.sendMessage(EsportsLang.get("creation.commands.saved",
                "&aSaved {count} command(s) for {placement}.",
                "count", commands.size(),
                "placement", placementLabel(placement)));
    }

    void setSpectatorLobby(Player player, CreationSession session) {
        if (player == null || session == null) {
            return;
        }
        session.setSpectatorSpawn(player.getLocation());
        player.sendMessage(EsportsLang.get("creation.waiting-lobby.saved", "&aWaiting lobby location saved."));
        openWizard(player, session);
    }

    void clearSpectatorLobby(Player player, CreationSession session) {
        if (player == null || session == null) {
            return;
        }
        session.clearSpectatorSpawn();
        player.sendMessage(EsportsLang.get("creation.waiting-lobby.cleared", "&eWaiting lobby cleared."));
        openWizard(player, session);
    }

    void requestNameInput(CreationSession session) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        session.setPendingInput(InputTarget.NAME);
        Runnable reopen = () -> Bukkit.getScheduler().runTask(plugin, () -> openWizard(player, session));
        boolean sign = SignPrompt.prompt(player, new String[]{"", "", "", ""},
                lines -> handleNameInput(session, mergeLines(lines)),
                () -> {
                    session.clearPendingInput();
                    reopen.run();
                });
        if (sign) {
            player.sendMessage(EsportsLang.get("creation.name-sign.prompt",
                    "&eEnter the tournament name on the sign and click 'Done'."));
            player.sendMessage(EsportsLang.get("creation.name-sign.color-codes", "&7Use & for color codes."));
            return;
        }
        player.sendMessage(EsportsLang.get("creation.sign-unsupported",
                "&eSign input is not supported on this version, switched to chat input."));
        ChatPrompt.request(player,
                EsportsLang.get("creation.name-chat.title", "&eType the tournament name"),
                EsportsLang.get("creation.name-chat.lore", "&7Use & for colors. Type 'cancel' to abort."),
                input -> handleNameInput(session, input),
                () -> {
                    session.clearPendingInput();
                    reopen.run();
                });
    }

    void requestStartDateInput(CreationSession session) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        session.setPendingInput(InputTarget.START_DATE);
        Runnable reopen = () -> Bukkit.getScheduler().runTask(plugin, () -> openWizard(player, session));
        boolean sign = SignPrompt.prompt(player, new String[]{"", "", "", ""},
                lines -> handleStartDateInput(session, mergeDateLines(lines)),
                () -> {
                    session.clearPendingInput();
                    reopen.run();
                });
        if (sign) {
            player.sendMessage(EsportsLang.get("creation.date-sign.prompt",
                    "&eLine 1: date (yyyy-MM-dd), line 2: time (HH:mm). Click 'Done' to save."));
            return;
        }
        player.sendMessage(EsportsLang.get("creation.sign-unsupported",
                "&eSign input is not supported on this version, switched to chat input."));
        ChatPrompt.request(player,
                EsportsLang.get("creation.date-chat.title",
                        "&eEnter start date in '{format}' format",
                        "format", DATE_FORMAT),
                EsportsLang.get("creation.date-chat.example", "&7Example: 2025-02-14 20:30"),
                input -> handleStartDateInput(session, input),
                () -> {
                    session.clearPendingInput();
                    reopen.run();
                });
    }

    private boolean parseAndStoreStartDate(CreationSession session, String value) {
        Player player = session.getPlayer();
        if (player == null) {
            return false;
        }
        if (value == null || value.trim().isEmpty()) {
            player.sendMessage(EsportsLang.get("creation.date.invalid-example",
                    "&cInvalid date format. Example: 2025-02-14 20:30"));
            return false;
        }
        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            player.sendMessage(EsportsLang.get("creation.date.format",
                    "&cUse date format '{format}'.",
                    "format", DATE_FORMAT));
            return false;
        }
        if (parsed.isBefore(LocalDateTime.now().plusMinutes(5))) {
            player.sendMessage(EsportsLang.get("creation.date.future",
                    "&cDate must be in the future (at least 5 minutes from now)."));
            return false;
        }
        session.setStartDate(parsed);
        return true;
    }

    void finalizeSession(CreationSession session) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        if (session.getDefinedSpawnSlotCount() < session.getRequiredSpawnSlots()) {
            if (session.autoPopulateSpawnSlots(autoSpawnRadius)) {
                player.sendMessage(EsportsLang.get("creation.spawns.auto-generated",
                        "&7Missing spawn points were auto-generated."));
            }
        }
        if (!session.isReady()) {
            player.sendMessage(EsportsLang.get("creation.save.missing-fields",
                    "&cCould not save. Missing fields: {fields}",
                    "fields", String.join(", ", session.describeMissingFields())));
            return;
        }
        JsonObject settings = session.buildSettings();
        JsonObject rewards = session.buildRewards();
        String slug = session.ensureSlug(manager);
        EsportsTournamentDraft draft = new EsportsTournamentDraft(
                session.getDisplayName(),
                slug,
                session.getEventType(),
                "upcoming",
                "Created via in-game wizard",
                session.getStartDate(),
                null,
                settings,
                rewards == null ? new JsonObject() : rewards,
                new JsonObject()
        );
        int id = manager.createTournament(draft);
        if (id <= 0) {
            player.sendMessage(EsportsLang.get("creation.save.error",
                    "&cAn error occurred while saving the tournament."));
            return;
        }
        sessions.remove(session.getOwner());
        removeGuideItem(player);
        player.sendMessage(EsportsLang.get("creation.save.success",
                "&aTournament #{id} created successfully.",
                "id", id));
    }

    private void handleNameInput(CreationSession session, String rawInput) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        String value = rawInput == null ? "" : rawInput.replace('\n', ' ').trim();
        if (value.isEmpty()) {
            player.sendMessage(EsportsLang.get("creation.name.empty", "&cTournament name cannot be empty."));
            requestNameInput(session);
            return;
        }
        if (!session.applyName(value)) {
            player.sendMessage(EsportsLang.get("creation.name.length", "&cName must be between 3 and 32 characters."));
            requestNameInput(session);
            return;
        }
        session.clearPendingInput();
        player.sendMessage(EsportsLang.get("creation.name.updated",
                "&aTournament name updated: {name}",
                "name", ChatColor.RESET + session.getDisplayName()));
        openWizard(player, session);
    }

    private void handleStartDateInput(CreationSession session, String rawInput) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        String value = rawInput == null ? "" : rawInput.trim();
        if (value.isEmpty()) {
            player.sendMessage(EsportsLang.get("creation.date.empty", "&cDate cannot be empty."));
            requestStartDateInput(session);
            return;
        }
        if (!parseAndStoreStartDate(session, value)) {
            requestStartDateInput(session);
            return;
        }
        session.clearPendingInput();
        player.sendMessage(EsportsLang.get("creation.date.updated", "&aStart date updated."));
        openWizard(player, session);
    }

    private String mergeLines(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(line.trim());
            }
        }
        return builder.toString();
    }

    private String mergeDateLines(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        String first = lines[0] == null ? "" : lines[0].trim();
        String second = lines.length > 1 && lines[1] != null ? lines[1].trim() : "";
        if (first.isEmpty()) {
            return "";
        }
        return second.isEmpty() ? first : first + " " + second;
    }

    private ItemStack buildGuideItem() {
        Material material = MaterialResolver.resolveMaterial("NETHER_STAR");
        if (material == null) {
            material = MaterialResolver.resolveMaterial("COMPASS");
        }
        if (material == null) {
            material = Material.NETHER_STAR;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(guideName);
            meta.setLore(Arrays.asList(
                    EsportsLang.get("creation.guide.lore.1", "&7Right-click to open"),
                    EsportsLang.get("creation.guide.lore.2", "&7tournament setup menus."),
                    EsportsLang.get("creation.guide.lore.3", "&8First step is the name input.")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void ensureGuideItem(Player player) {
        if (hasGuideItem(player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        HashMap<Integer, ItemStack> leftover = inventory.addItem(guideItem.clone());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    private boolean hasGuideItem(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isGuideItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private void removeGuideItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean dirty = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isGuideItem(stack)) {
                inventory.setItem(i, null);
                dirty = true;
            }
        }
        if (dirty) {
            player.updateInventory();
        }
    }

    private boolean isGuideItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && guideName.equals(meta.getDisplayName());
    }

    @EventHandler
    public void onGuideInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!isGuideItem(stack)) {
            return;
        }
        Player player = event.getPlayer();
        CreationSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        ensureGuideItem(player);
        if (!session.hasName()) {
            requestNameInput(session);
            return;
        }
        openWizard(player, session);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        CreationSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAwaitingInput()) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleChat(session, message));
    }

    private void handleChat(CreationSession session, String message) {
        Player player = session.getPlayer();
        if (player == null) {
            sessions.remove(session.getOwner());
            return;
        }
        if (message.equalsIgnoreCase("iptal") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(EsportsLang.get("creation.input.cancelled", "&cInput cancelled."));
            session.clearPendingInput();
            openWizard(player, session);
            return;
        }
        InputTarget target = session.getPendingInput();
        if (target == InputTarget.NAME) {
            if (!session.applyName(message)) {
                player.sendMessage(EsportsLang.get("creation.name.length", "&cName must be between 3 and 32 characters."));
                return;
            }
            player.sendMessage(EsportsLang.get("creation.name.updated-short", "&aTournament name updated."));
            session.clearPendingInput();
            openWizard(player, session);
            return;
        }
        if (target == InputTarget.START_DATE) {
            if (!parseAndStoreStartDate(session, message)) {
                return;
            }
            player.sendMessage(EsportsLang.get("creation.date.set", "&aStart date set."));
            session.clearPendingInput();
            openWizard(player, session);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CreationSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.clearPendingInput();
        }
        removeGuideItem(event.getPlayer());
    }

    enum InputTarget {
        NONE,
        NAME,
        START_DATE
    }

    public final class CreationSession {
        private final UUID owner;
        private final String ownerName;
        private String displayName;
        private String slug;
        private String eventType = "pvp";
        private int rounds = MIN_ROUNDS;
        private int matchClubCount = MIN_MATCH_CLUBS;
        private LocalDateTime startDate;
        private JsonObject kitConfig;
        private Location spectatorSpawn;
        private final Map<Integer, JsonObject> spawnSlots = new LinkedHashMap<>();
        private final Map<String, PotionSetting> potions = new LinkedHashMap<>();
        private final Map<Integer, List<String>> placementRewards = new HashMap<>();
        private String repeatMode = "off";
        private InputTarget pendingInput = InputTarget.NONE;

        CreationSession(UUID owner, String ownerName) {
            this.owner = owner;
            this.ownerName = ownerName;
        }

        UUID getOwner() {
            return owner;
        }

        Player getPlayer() {
            return Bukkit.getPlayer(owner);
        }

        String getDisplayName() {
            return displayName;
        }

        String getSlug() {
            return slug;
        }

        String ensureSlug(EsportsManager mgr) {
            if (mgr == null) {
                if (slug == null || slug.trim().isEmpty()) {
                    slug = "es-" + System.currentTimeMillis();
                }
                return slug;
            }
            String source = displayName != null && !displayName.trim().isEmpty()
                    ? ChatColor.stripColor(displayName)
                    : ownerName;
            if (source == null || source.trim().isEmpty()) {
                source = ownerName != null && !ownerName.trim().isEmpty()
                        ? ownerName
                        : "es-" + owner.toString().substring(0, 8);
            }
            slug = mgr.generateSlug(source);
            return slug;
        }

        String getEventType() {
            return eventType;
        }

        int getRoundCount() {
            return rounds;
        }

        LocalDateTime getStartDate() {
            return startDate;
        }

        LocalDateTime ensureStartDate() {
            if (startDate == null) {
                startDate = defaultStartDate();
            } else {
                startDate = sanitizeStartDate(startDate);
            }
            return startDate;
        }

        JsonObject getKitConfig() {
            return JsonUtils.deepCopy(kitConfig);
        }

        JsonObject getSpawnPoint(int index) {
            return spawnSlots.get(index);
        }

        int getRequiredSpawnSlots() {
            return matchClubCount;
        }

        int getParticipantSlots() {
            return getRequiredSpawnSlots();
        }

        int getMatchClubCount() {
            return matchClubCount;
        }

        int getBracketParticipantSlots() {
            return 1 << rounds;
        }

        int getDefinedSpawnSlotCount() {
            int count = 0;
            for (int i = 0; i < getRequiredSpawnSlots(); i++) {
                if (spawnSlots.containsKey(i)) {
                    count++;
                }
            }
            return count;
        }

        boolean hasKit() {
            return kitConfig != null;
        }

        int getPotionCount() {
            return potions.size();
        }

        List<String> describeMissingFields() {
            List<String> missing = new ArrayList<>();
            if (displayName == null) {
                missing.add("name");
            }
            if (startDate == null) {
                missing.add("date");
            }
            if (kitConfig == null) {
                missing.add("kit");
            }
            if (!hasSpectatorSpawn()) {
                missing.add("waiting lobby");
            }
            if (getDefinedSpawnSlotCount() < getRequiredSpawnSlots()) {
                missing.add("spawn");
            }
            return missing;
        }

        boolean isReady() {
            return describeMissingFields().isEmpty();
        }

        boolean hasName() {
            return displayName != null && !displayName.trim().isEmpty();
        }

        void setPendingInput(InputTarget target) {
            this.pendingInput = target == null ? InputTarget.NONE : target;
        }

        boolean isAwaitingInput() {
            return pendingInput != InputTarget.NONE;
        }

        InputTarget getPendingInput() {
            return pendingInput;
        }

        void clearPendingInput() {
            this.pendingInput = InputTarget.NONE;
        }

        boolean applyName(String raw) {
            if (raw == null) {
                return false;
            }
            String trimmed = raw.trim();
            if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
                return false;
            }
            this.displayName = ChatColor.translateAlternateColorCodes('&', trimmed);
            String plain = ChatColor.stripColor(this.displayName);
            EsportsManager mgr = EsportsCreationService.this.manager;
            if (mgr != null) {
                this.slug = mgr.generateSlug(plain);
            } else {
                this.slug = plain.toLowerCase(Locale.ROOT).replace(' ', '-');
            }
            return true;
        }

        void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate == null ? null : sanitizeStartDate(startDate);
        }

        void setSpectatorSpawn(Location location) {
            this.spectatorSpawn = location == null ? null : location.clone();
        }

        void clearSpectatorSpawn() {
            this.spectatorSpawn = null;
        }

        boolean hasSpectatorSpawn() {
            return spectatorSpawn != null && spectatorSpawn.getWorld() != null;
        }

        Location getSpectatorSpawn() {
            return spectatorSpawn == null ? null : spectatorSpawn.clone();
        }

        boolean adjustStartDateMinutes(int deltaMinutes) {
            if (deltaMinutes == 0) {
                return false;
            }
            LocalDateTime current = ensureStartDate();
            LocalDateTime updated = current.plusMinutes(deltaMinutes);
            LocalDateTime minAllowed = LocalDateTime.now().plusMinutes(5);
            if (updated.isBefore(minAllowed)) {
                updated = minAllowed;
            }
            if (updated.equals(current)) {
                return false;
            }
            startDate = updated;
            return true;
        }

        void resetStartDateToDefault() {
            startDate = defaultStartDate();
        }

        private LocalDateTime defaultStartDate() {
            return LocalDateTime.now().plusMinutes(30);
        }

        private LocalDateTime sanitizeStartDate(LocalDateTime candidate) {
            if (candidate == null) {
                return null;
            }
            LocalDateTime minAllowed = LocalDateTime.now().plusMinutes(5);
            if (candidate.isBefore(minAllowed)) {
                return minAllowed;
            }
            return candidate;
        }

        void setEventType(String type) {
            if (type == null || type.trim().isEmpty()) {
                return;
            }
            this.eventType = type.toLowerCase(Locale.ROOT);
        }

        boolean adjustRounds(int delta) {
            return setRoundValue(rounds + delta);
        }

        boolean adjustMatchClubCount(int delta) {
            return setMatchClubCount(matchClubCount + delta);
        }

        boolean cycleParticipantSlots(int direction) {
            if (direction == 0) {
                return false;
            }
            int currentIndex = rounds - MIN_ROUNDS;
            int nextIndex = Math.max(0, Math.min(MAX_ROUNDS - MIN_ROUNDS,
                    currentIndex + (direction > 0 ? 1 : -1)));
            if (nextIndex == currentIndex) {
                return false;
            }
            return setRoundValue(MIN_ROUNDS + nextIndex);
        }

        private boolean setRoundValue(int value) {
            int updated = Math.max(MIN_ROUNDS, Math.min(MAX_ROUNDS, value));
            if (updated == rounds) {
                return false;
            }
            this.rounds = updated;
            return true;
        }

        private boolean setMatchClubCount(int value) {
            int updated = Math.max(MIN_MATCH_CLUBS, Math.min(MAX_MATCH_CLUBS, value));
            if (updated == matchClubCount) {
                return false;
            }
            this.matchClubCount = updated;
            int limit = getRequiredSpawnSlots();
            spawnSlots.keySet().removeIf(key -> key >= limit);
            return true;
        }

        void setSpawnPoint(int index, JsonObject point) {
            if (index < 0) {
                return;
            }
            int limit = getRequiredSpawnSlots();
            if (index >= limit) {
                return;
            }
            if (point == null) {
                spawnSlots.remove(index);
            } else {
                spawnSlots.put(index, point);
            }
        }

        boolean autoPopulateSpawnSlots(double radius) {
            return populateSpawnSlots(radius, false);
        }

        boolean regenerateSpawnSlots(double radius) {
            return populateSpawnSlots(radius, true);
        }

        private boolean populateSpawnSlots(double radius, boolean overwrite) {
            if (!hasSpectatorSpawn()) {
                return false;
            }
            Location base = getSpectatorSpawn();
            if (base == null || base.getWorld() == null) {
                return false;
            }
            if (overwrite) {
                spawnSlots.clear();
            }
            double appliedRadius = Math.max(2.0D, radius);
            int total = getRequiredSpawnSlots();
            if (total <= 0) {
                return false;
            }
            double step = (Math.PI * 2D) / total;
            for (int i = 0; i < total; i++) {
                if (!overwrite && spawnSlots.containsKey(i)) {
                    continue;
                }
                double angle = step * i;
                Location loc = base.clone();
                loc.setX(base.getX() + (Math.cos(angle) * appliedRadius));
                loc.setZ(base.getZ() + (Math.sin(angle) * appliedRadius));
                loc.setYaw((float) Math.toDegrees(angle + Math.PI));
                spawnSlots.put(i, EsportsSpawnPoint.toJson(loc));
            }
            return true;
        }

        void clearSpawnPoint(int index) {
            spawnSlots.remove(index);
        }

        void setKitConfig(JsonObject kit) {
            this.kitConfig = JsonUtils.deepCopy(kit);
        }

        PotionSetting getPotionSetting(String key) {
            return potions.get(cleanKey(key));
        }

        void bumpPotionLevel(String key) {
            key = cleanKey(key);
            PotionSetting setting = potions.computeIfAbsent(key, ignored -> new PotionSetting(1, 120));
            setting.level = setting.level >= 5 ? 1 : setting.level + 1;
        }

        void bumpPotionDuration(String key) {
            key = cleanKey(key);
            PotionSetting setting = potions.computeIfAbsent(key, ignored -> new PotionSetting(1, 120));
            int[] options = {60, 120, 180, 0};
            int nextIndex = 0;
            for (int i = 0; i < options.length; i++) {
                if (options[i] == setting.durationSeconds) {
                    nextIndex = (i + 1) % options.length;
                    break;
                }
            }
            setting.durationSeconds = options[nextIndex];
        }

        void removePotion(String key) {
            potions.remove(cleanKey(key));
        }

        JsonObject buildSettings() {
            JsonObject root = new JsonObject();
            root.addProperty("rounds", rounds);
            root.addProperty("participantSlots", getBracketParticipantSlots());
            root.addProperty("matchClubCount", getRequiredSpawnSlots());
            root.addProperty("repeatable", isRepeatable());
            root.addProperty("repeatMode", repeatMode);
            root.addProperty("repeatDays", getRepeatIntervalDays());

            JsonArray spawnArray = new JsonArray();
            for (int i = 0; i < getRequiredSpawnSlots(); i++) {
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", i);
                entry.addProperty("label", "Slot #" + (i + 1));
                JsonObject json = spawnSlots.get(i);
                if (json != null) {
                    entry.add("location", JsonUtils.deepCopy(json));
                }
                spawnArray.add(entry);
            }
            root.add("spawnSlots", spawnArray);

            if (kitConfig != null) {
                root.add("defaultKit", JsonUtils.deepCopy(kitConfig));
            }

            if (!potions.isEmpty()) {
                JsonArray effects = new JsonArray();
                for (Map.Entry<String, PotionSetting> entry : potions.entrySet()) {
                    PotionSetting setting = entry.getValue();
                    JsonObject effect = new JsonObject();
                    effect.addProperty("type", entry.getKey());
                    effect.addProperty("amplifier", Math.max(0, setting.level - 1));
                    effect.addProperty("durationSeconds", setting.durationSeconds);
                    effects.add(effect);
                }
                root.add("potionEffects", effects);
            }
            if (spectatorSpawn != null && spectatorSpawn.getWorld() != null) {
                JsonObject spectator = new JsonObject();
                spectator.addProperty("world", spectatorSpawn.getWorld().getName());
                spectator.addProperty("x", spectatorSpawn.getX());
                spectator.addProperty("y", spectatorSpawn.getY());
                spectator.addProperty("z", spectatorSpawn.getZ());
                spectator.addProperty("yaw", spectatorSpawn.getYaw());
                spectator.addProperty("pitch", spectatorSpawn.getPitch());
                root.add("spectator", spectator);
            }
            return root;
        }

        String getRepeatMode() {
            return repeatMode == null ? "off" : repeatMode;
        }

        String getRepeatLabel() {
            String mode = getRepeatMode();
            if ("daily".equalsIgnoreCase(mode)) {
                return "Daily";
            }
            if ("weekly".equalsIgnoreCase(mode)) {
                return "Weekly";
            }
            if ("biweekly".equalsIgnoreCase(mode)) {
                return "Biweekly";
            }
            return "Off";
        }

        int getRepeatIntervalDays() {
            String mode = getRepeatMode();
            if ("daily".equalsIgnoreCase(mode)) {
                return 1;
            }
            if ("weekly".equalsIgnoreCase(mode)) {
                return 7;
            }
            if ("biweekly".equalsIgnoreCase(mode)) {
                return 14;
            }
            return 0;
        }

        boolean isRepeatable() {
            return getRepeatIntervalDays() > 0;
        }

        void cycleRepeatMode() {
            String[] modes = {"off", "daily", "weekly", "biweekly"};
            String current = getRepeatMode().toLowerCase(Locale.ROOT);
            int index = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(current)) {
                    index = i;
                    break;
                }
            }
            repeatMode = modes[(index + 1) % modes.length];
        }

        JsonObject buildRewards() {
            JsonObject root = new JsonObject();
            if (placementRewards.isEmpty()) {
                return root;
            }
            JsonArray placementArray = new JsonArray();
            placementRewards.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        JsonObject bucket = new JsonObject();
                        bucket.addProperty("place", entry.getKey());
                        bucket.addProperty("title", placementLabel(entry.getKey()));
                        JsonArray commands = new JsonArray();
                        for (String command : entry.getValue()) {
                            commands.add(new JsonPrimitive(command));
                        }
                        bucket.add("commands", commands);
                        placementArray.add(bucket);
                    });
            if (placementArray.size() > 0) {
                root.add("placements", placementArray);
            }
            return root;
        }

        List<String> getPlacementCommands(int placement) {
            List<String> commands = placementRewards.get(placement);
            if (commands == null || commands.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(commands));
        }

        void setPlacementCommands(int placement, List<String> commands) {
            if (placement < 1) {
                return;
            }
            if (commands == null || commands.isEmpty()) {
                placementRewards.remove(placement);
                return;
            }
            List<String> sanitized = new ArrayList<>();
            for (String command : commands) {
                String trimmed = command == null ? "" : command.trim();
                if (!trimmed.isEmpty()) {
                    sanitized.add(trimmed);
                }
            }
            if (sanitized.isEmpty()) {
                placementRewards.remove(placement);
            } else {
                placementRewards.put(placement, sanitized);
            }
        }

        void clearPlacementCommands(int placement) {
            placementRewards.remove(placement);
        }

        String describeRewardSummary() {
            StringBuilder builder = new StringBuilder();
            for (int placement = 1; placement <= 3; placement++) {
                if (placement > 1) {
                    builder.append('\n');
                }
                List<String> commands = placementRewards.get(placement);
                builder.append("&7#").append(placement).append(": ")
                        .append(commands == null || commands.isEmpty() ? "&cNone" : "&a" + commands.size() + " commands");
            }
            return builder.toString();
        }

        private String cleanKey(String key) {
            return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        }

        final class PotionSetting {
            private int level;
            private int durationSeconds;

            PotionSetting(int level, int durationSeconds) {
                this.level = level;
                this.durationSeconds = durationSeconds;
            }

            int getLevel() {
                return level;
            }

            int getDurationSeconds() {
                return durationSeconds;
            }
        }
    }

    double getAutoSpawnRadius() {
        return autoSpawnRadius;
    }
}
