package dev.zonely.whiteeffect.clubs;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.menu.ClubCreationMenu;
import dev.zonely.whiteeffect.clubs.model.BannerDesign;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.clubs.model.ClubDraft;
import dev.zonely.whiteeffect.clubs.model.ClubMemberRole;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ClubManager implements Listener {

    private final Core plugin;
    private final ClubRepository repository;
    private final java.util.Map<UUID, ClubCreationSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public ClubManager(Core plugin) {
        this.plugin = plugin;
        this.repository = new ClubRepository(plugin.getLogger());
    }

    public Optional<ClubData> getClubByOwner(Player player) {
        return repository.findByOwner(player.getUniqueId());
    }

    public Optional<ClubData> getClubByOwner(UUID uuid) {
        return repository.findByOwner(uuid);
    }

    public Optional<ClubData> getClubById(int clubId) {
        return repository.findById(clubId);
    }

    public List<ClubData> listAllClubs() {
        return repository.listAll();
    }

    public Optional<ClubData> getClubByMember(Player player) {
        return repository.findByMember(player.getUniqueId());
    }

    public boolean isMemberOfAnyClub(Player player) {
        return getClubByOwner(player).isPresent() || getClubByMember(player).isPresent();
    }

    public ClubCreationSession beginCreation(Player player) {
        ClubCreationSession session = new ClubCreationSession(player.getUniqueId(), player.getName());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public Optional<ClubCreationSession> getSession(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public void cancelSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean createClub(Player player, ClubCreationSession session) {
        String baseTag = session.buildTagCandidate();
        String tag = baseTag;
        int attempt = 1;
        while (repository.existsByTag(tag)) {
            tag = baseTag + attempt++;
            if (tag.length() > 6) {
                tag = tag.substring(0, 6);
            }
        }
        ClubDraft draft = session.toDraft(tag);
        int id = repository.insertClub(draft);
        if (id <= 0) {
            player.sendMessage(ClubLang.get("manager.create.failed", "&cClub could not be created. Please try again."));
            return false;
        }
        repository.insertMember(id, draft.getOwnerUuid(), draft.getOwnerName(), ClubMemberRole.OWNER);
        sessions.remove(player.getUniqueId());
        player.sendMessage(ClubLang.get("manager.create.success", "&aClub created successfully!"));
        plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
        return true;
    }

    public boolean hasClub(Player player) {
        return isMemberOfAnyClub(player);
    }

    public boolean inviteMember(Player owner, Player target) {
        Optional<ClubData> clubOpt = getClubByOwner(owner);
        if (!clubOpt.isPresent()) {
            owner.sendMessage(ClubLang.get("manager.invite.create-first", "&cYou must create a club first."));
            return false;
        }
        ClubData club = clubOpt.get();
        if (!club.getOwnerUuid().equals(owner.getUniqueId())) {
            owner.sendMessage(ClubLang.get("manager.invite.owner-only", "&cOnly the club owner can invite members."));
            return false;
        }
        if (owner.getUniqueId().equals(target.getUniqueId())) {
            owner.sendMessage(ClubLang.get("manager.invite.self", "&cYou cannot invite yourself."));
            return false;
        }
        if (isMemberOfAnyClub(target)) {
            owner.sendMessage(ClubLang.get("manager.invite.already-in-club",
                    "&c{player} is already in a club.",
                    "player", target.getName()));
            return false;
        }
        boolean success = repository.insertMember(club.getId(), target.getUniqueId(), target.getName(), ClubMemberRole.MEMBER);
        if (success) {
            owner.sendMessage(ClubLang.get("manager.invite.success-owner",
                    "&a{player} joined your club.",
                    "player", target.getName()));
            target.sendMessage(ClubLang.get("manager.invite.success-target",
                    "&aYou joined {club}!",
                    "club", club.getName()));
        } else {
            owner.sendMessage(ClubLang.get("manager.invite.failed", "&cFailed to add member."));
        }
        return success;
    }

    public boolean kickMember(Player owner, String memberName) {
        Optional<ClubData> clubOpt = getClubByOwner(owner);
        if (!clubOpt.isPresent()) {
            owner.sendMessage(ClubLang.get("manager.kick.create-first", "&cYou must create a club first."));
            return false;
        }
        ClubData club = clubOpt.get();
        if (!club.getOwnerUuid().equals(owner.getUniqueId())) {
            owner.sendMessage(ClubLang.get("manager.kick.owner-only", "&cOnly the club owner can remove members."));
            return false;
        }
        java.util.Optional<dev.zonely.whiteeffect.clubs.model.ClubMember> memberOpt = club.getMembers().stream()
                .filter(m -> m.getUsername().equalsIgnoreCase(memberName))
                .findFirst();
        if (!memberOpt.isPresent()) {
            owner.sendMessage(ClubLang.get("manager.kick.not-found", "&cNo member found with that name."));
            return false;
        }
        dev.zonely.whiteeffect.clubs.model.ClubMember member = memberOpt.get();
        if (member.getUniqueId().equals(owner.getUniqueId())) {
            owner.sendMessage(ClubLang.get("manager.kick.self",
                    "&cYou cannot remove yourself this way. Transfer ownership or disband the club."));
            return false;
        }
        boolean removed = repository.removeMember(club.getId(), member.getUniqueId());
        if (removed) {
            owner.sendMessage(ClubLang.get("manager.kick.success",
                    "&a{player} was removed from the club.",
                    "player", member.getUsername()));
        } else {
            owner.sendMessage(ClubLang.get("manager.kick.failed", "&cFailed to remove member."));
        }
        return removed;
    }

    public boolean leaveClub(Player player) {
        Optional<ClubData> clubOpt = getClubByMember(player);
        if (!clubOpt.isPresent()) {
            player.sendMessage(ClubLang.get("manager.leave.not-in-club", "&cYou are not in any club."));
            return false;
        }
        ClubData club = clubOpt.get();
        if (club.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ClubLang.get("manager.leave.owner",
                    "&cYou are the club owner. Transfer ownership or disband the club first."));
            return false;
        }
        boolean removed = repository.removeMember(club.getId(), player.getUniqueId());
        if (removed) {
            player.sendMessage(ClubLang.get("manager.leave.success",
                    "&aYou left {club}.",
                    "club", club.getName()));
        } else {
            player.sendMessage(ClubLang.get("manager.leave.failed", "&cFailed to remove club membership."));
        }
        return removed;
    }

    public boolean updateColor(Player owner, ClubData club, String colorHex, String colorName) {
        if (!club.getOwnerUuid().equals(owner.getUniqueId())) {
            owner.sendMessage(ClubLang.get("manager.color.owner-only", "&cOnly the club owner can change the club color."));
            return false;
        }
        boolean success = repository.updateColor(club.getId(), colorHex);
        if (success) {
            owner.sendMessage(ClubLang.get("manager.color.success",
                    "&aClub color updated to {color}.",
                    "color", colorName));
        } else {
            owner.sendMessage(ClubLang.get("manager.color.failed", "&cFailed to update color."));
        }
        return success;
    }

    public BannerDesign parseBannerDesign(String json) {
        return BannerDesign.fromJson(json);
    }

    public boolean updateBanner(Player owner, ClubData club, BannerDesign design) {
        if (!club.getOwnerUuid().equals(owner.getUniqueId())) {
            owner.sendMessage(ClubLang.get("manager.banner.owner-only", "&cOnly the club owner can update the banner."));
            return false;
        }
        if (design == null) {
            design = new BannerDesign();
        }
        boolean success = repository.updateBanner(club.getId(), design.toJson());
        if (success) {
            owner.sendMessage(ClubLang.get("manager.banner.success", "&aClub banner updated."));
        } else {
            owner.sendMessage(ClubLang.get("manager.banner.failed", "&cFailed to update banner."));
        }
        return success;
    }

    public void requestNameInput(Player player) {
        ClubCreationSession session = sessions.computeIfAbsent(player.getUniqueId(),
                uuid -> new ClubCreationSession(uuid, player.getName()));
        session.setAwaitingName(true);
        player.closeInventory();
        player.sendMessage(ClubLang.get("manager.name-input.prompt",
                "&ePlease type your club name in chat. Type 'cancel' to abort."));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ClubCreationSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAwaitingName()) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("iptal") || message.equalsIgnoreCase("cancel")) {
            session.setAwaitingName(false);
            player.sendMessage(ClubLang.get("manager.name-input.cancelled", "&cClub name input cancelled."));
            reopenCreationMenu(player);
            return;
        }
        if (message.length() < 3 || message.length() > 16) {
            player.sendMessage(ClubLang.get("manager.name-input.length", "&cClub name must be between 3 and 16 characters."));
            return;
        }
        session.setName(message);
        session.setAwaitingName(false);
        player.sendMessage(ClubLang.get("manager.name-input.saved", "&aClub name saved as '{name}'.", "name", message));
        reopenCreationMenu(player);
    }

    private void reopenCreationMenu(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ClubCreationSession session = sessions.get(player.getUniqueId());
            if (session != null) {
                PlayerMenu menu = session.openMenu(plugin, player);
                menu.register(plugin);
                menu.open();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public static final class ClubCreationSession {
        private final UUID ownerUuid;
        private final String ownerName;
        private String name;
        private String colorHex;
        private String colorName;
        private BannerDesign bannerDesign = new BannerDesign();
        private boolean awaitingName;

        public ClubCreationSession(UUID ownerUuid, String ownerName) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setColor(String colorHex, String colorName) {
            this.colorHex = colorHex;
            this.colorName = colorName;
        }

        public void setBannerBaseColor(String bannerBaseColor) {
            this.bannerDesign.setBaseColor(bannerBaseColor);
        }

        public BannerDesign getBannerDesign() {
            return bannerDesign;
        }

        public void applyBannerDesign(BannerDesign design) {
            if (design != null) {
                this.bannerDesign = design.copy();
            }
        }

        public boolean isAwaitingName() {
            return awaitingName;
        }

        public void setAwaitingName(boolean awaitingName) {
            this.awaitingName = awaitingName;
        }

        public boolean isReady() {
            return name != null && colorHex != null;
        }

        public ClubDraft toDraft(String tag) {
            BannerDesign design = bannerDesign == null ? new BannerDesign() : bannerDesign.copy();
            return new ClubDraft(
                    name,
                    tag,
                    colorHex,
                    design.toJson(),
                    ownerUuid,
                    ownerName
            );
        }

        public PlayerMenu openMenu(Core plugin, Player player) {
            return new ClubCreationMenu(plugin, player, this);
        }

        public String getName() {
            return name;
        }

        public String getColorHex() {
            return colorHex;
        }

        public String getColorName() {
            return colorName;
        }

        public String getBannerBaseColor() {
            return bannerDesign.getBaseColor();
        }

        public String buildTagCandidate() {
            if (name == null) {
                return "CLB";
            }
            String sanitized = name.replaceAll("[^A-Za-z0-9]", "");
            if (sanitized.isEmpty()) {
                sanitized = "CLB";
            }
            sanitized = sanitized.length() > 4 ? sanitized.substring(0, 4) : sanitized;
            return sanitized.toUpperCase();
        }
    }
}
