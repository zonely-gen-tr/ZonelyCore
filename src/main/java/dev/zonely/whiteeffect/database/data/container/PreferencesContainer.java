package dev.zonely.whiteeffect.database.data.container;

import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.interfaces.AbstractContainer;
import dev.zonely.whiteeffect.player.enums.BloodAndGore;
import dev.zonely.whiteeffect.player.enums.PlayerVisibility;
import dev.zonely.whiteeffect.player.enums.PrivateMessages;
import dev.zonely.whiteeffect.player.enums.ProtectionLobby;
import org.json.simple.JSONObject;

public class PreferencesContainer extends AbstractContainer {
   public PreferencesContainer(DataContainer dataContainer) {
      super(dataContainer);
   }

   public void changePlayerVisibility() {
      JSONObject preferences = this.dataContainer.getAsJsonObject();
      preferences.put("pv", PlayerVisibility.getByOrdinal((Long)preferences.get("pv")).next().ordinal());
      this.dataContainer.set(preferences.toString());
      preferences.clear();
   }

   public void changePrivateMessages() {
      JSONObject preferences = this.dataContainer.getAsJsonObject();
      preferences.put("pm", PrivateMessages.getByOrdinal((Long)preferences.get("pm")).next().ordinal());
      this.dataContainer.set(preferences.toString());
      preferences.clear();
   }

   public void changeBloodAndGore() {
      JSONObject preferences = this.dataContainer.getAsJsonObject();
      preferences.put("bg", BloodAndGore.getByOrdinal((Long)preferences.get("bg")).next().ordinal());
      this.dataContainer.set(preferences.toString());
      preferences.clear();
   }

   public void changeProtectionLobby() {
      JSONObject preferences = this.dataContainer.getAsJsonObject();
      preferences.put("pl", ProtectionLobby.getByOrdinal((Long)preferences.get("pl")).next().ordinal());
      this.dataContainer.set(preferences.toString());
      preferences.clear();
   }

   public PlayerVisibility getPlayerVisibility() {
      return PlayerVisibility.getByOrdinal((Long)this.dataContainer.getAsJsonObject().get("pv"));
   }

   public PrivateMessages getPrivateMessages() {
      return PrivateMessages.getByOrdinal((Long)this.dataContainer.getAsJsonObject().get("pm"));
   }

   public BloodAndGore getBloodAndGore() {
      return BloodAndGore.getByOrdinal((Long)this.dataContainer.getAsJsonObject().get("bg"));
   }

   public ProtectionLobby getProtectionLobby() {
      return ProtectionLobby.getByOrdinal((Long)this.dataContainer.getAsJsonObject().get("pl"));
   }
}
