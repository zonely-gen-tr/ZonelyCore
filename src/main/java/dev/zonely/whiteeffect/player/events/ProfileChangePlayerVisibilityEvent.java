package dev.zonely.whiteeffect.player.events;

import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.enums.PlayerVisibility;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProfileChangePlayerVisibilityEvent extends Event {
   private static final HandlerList HANDLER_LIST = new HandlerList();
   private final Profile profile;
   private final PlayerVisibility playerVisibility;

   public ProfileChangePlayerVisibilityEvent(Profile profile) {
      this.profile = profile;
      this.playerVisibility = profile.getPreferencesContainer().getPlayerVisibility();
   }

   public static HandlerList getHandlerList() {
      return HANDLER_LIST;
   }

   public Player getPlayer() {
      return this.profile.getPlayer();
   }

   public Profile getProfile() {
      return this.profile;
   }

   public PlayerVisibility getPlayerVisibility() {
      return this.playerVisibility;
   }

   public HandlerList getHandlers() {
      return HANDLER_LIST;
   }
}
