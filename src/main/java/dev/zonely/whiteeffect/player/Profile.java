package dev.zonely.whiteeffect.player;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.booster.Booster;
import dev.zonely.whiteeffect.booster.NetworkBooster;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.container.BoostersContainer;
import dev.zonely.whiteeffect.database.data.container.DeliveriesContainer;
import dev.zonely.whiteeffect.database.data.container.PreferencesContainer;
import dev.zonely.whiteeffect.database.data.container.SelectedContainer;
import dev.zonely.whiteeffect.database.data.container.TitlesContainer;
import dev.zonely.whiteeffect.database.data.interfaces.AbstractContainer;
import dev.zonely.whiteeffect.database.exception.ProfileLoadException;
import dev.zonely.whiteeffect.hook.FriendsHook;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.enums.PlayerVisibility;
import dev.zonely.whiteeffect.player.hotbar.Hotbar;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.titles.TitleManager;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public class Profile {
   private static final Map<String, UUID> UUID_CACHE = new HashMap();
   private static final Map<String, Profile> PROFILES = new ConcurrentHashMap();
   private static final SimpleDateFormat COMPARE_SDF = new SimpleDateFormat("yyyy/MM/dd");
   private String name;
   private Hotbar hotbar;
   private Map<String, Long> lastHit = new HashMap();
   private Map<String, Map<String, DataContainer>> tableMap;
   private Player player;

   public Profile(String name) throws ProfileLoadException {
      this.name = name;
      this.tableMap = Database.getInstance().load(name);
      this.getDataContainer("ZonelyCoreProfile", "lastlogin").set(System.currentTimeMillis());
   }

   public static Profile createOrLoadProfile(String playerName) throws ProfileLoadException {
      Profile profile = PROFILES.getOrDefault(playerName.toLowerCase(), null);
      if (profile == null) {
         profile = new Profile(playerName);
         PROFILES.put(playerName.toLowerCase(), profile);
      }

      return profile;
   }
   public static Profile loadIfExists(String playerName) throws ProfileLoadException {
      Profile profile = PROFILES.getOrDefault(playerName.toLowerCase(), null);
      if (profile == null) {
         playerName = Database.getInstance().exists(playerName);
         if (playerName != null) {
            profile = new Profile(playerName);
         }
      }

      return profile;
   }

   public static Profile getProfile(String playerName) {
      return (Profile)PROFILES.get(playerName.toLowerCase());
   }

   public static Profile unloadProfile(String playerName) {
      UUID_CACHE.remove(playerName.toLowerCase());
      return (Profile)PROFILES.remove(playerName.toLowerCase());
   }

   public static Player findCached(String playerName) {
      UUID uuid = (UUID)UUID_CACHE.get(playerName.toLowerCase());
      return uuid == null ? null : Bukkit.getPlayer(uuid);
   }

   public static boolean isOnline(String playerName) {
      return PROFILES.containsKey(playerName.toLowerCase());
   }

   public static Collection<Profile> listProfiles() {
      return PROFILES.values();
   }

   public void setHit(String name) {
      this.lastHit.put(name, System.currentTimeMillis() + 8000L);
   }

   public void update() { }

   public void refresh() {
      Player player = this.getPlayer();
      if (player != null) {
         player.setMaxHealth(20.0D);
         player.setHealth(20.0D);
         player.setFoodLevel(20);
         player.setExhaustion(0.0F);
         player.setExp(0.0F);
         player.setLevel(0);
         player.setAllowFlight(false);
         player.closeInventory();
         player.spigot().setCollidesWithEntities(true);
         Iterator var2 = player.getActivePotionEffects().iterator();

         while(var2.hasNext()) {
            PotionEffect pe = (PotionEffect)var2.next();
            player.removePotionEffect(pe.getType());
         }

         if (!this.playingGame()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(Core.getLobby());
            player.setAllowFlight(player.hasPermission("zcore.fly"));
            this.getDataContainer("ZonelyCoreProfile", "role").set(StringUtils.stripColors(Role.getPlayerRole(player, true).getName()));
         }

         if (this.hotbar != null) {
            this.hotbar.apply(this);
         }

         this.refreshPlayers();
      }
   }

   public void refreshPlayers() {
      Player player = this.getPlayer();
      if (player != null) {
         if (this.hotbar != null) {
            this.hotbar.getButtons().forEach((button) -> {
               if (button.getAction().getValue().equalsIgnoreCase("online")) {
                  player.getInventory().setItem(button.getSlot(), BukkitUtils.deserializeItemStack(PlaceholderAPI.setPlaceholders(player, button.getIcon())));
               }

            });
         }

         if (!this.playingGame()) {
            Iterator var2 = Bukkit.getOnlinePlayers().iterator();

            while(true) {
               while(true) {
                  while(true) {
                     Player players;
                     Profile profile;
                     do {
                        if (!var2.hasNext()) {
                           return;
                        }

                        players = (Player)var2.next();
                        profile = getProfile(players.getName());
                     } while(profile == null);

                     if (!profile.playingGame()) {
                        boolean friend = FriendsHook.isFriend(player.getName(), players.getName());
                        if ((this.getPreferencesContainer().getPlayerVisibility() == PlayerVisibility.GENEL || Role.getPlayerRole(players).isAlwaysVisible() || friend) && !FriendsHook.isBlacklisted(player.getName(), players.getName())) {
                           if (!player.canSee(players)) {
                              TitleManager.show(this, profile);
                           }

                           player.showPlayer(players);
                        } else {
                           if (player.canSee(players)) {
                              TitleManager.hide(this, profile);
                           }

                           player.hidePlayer(players);
                        }

                        if ((profile.getPreferencesContainer().getPlayerVisibility() == PlayerVisibility.GENEL || Role.getPlayerRole(player).isAlwaysVisible() || friend) && !FriendsHook.isBlacklisted(players.getName(), player.getName())) {
                           if (!players.canSee(player)) {
                              TitleManager.show(profile, this);
                           }

                           players.showPlayer(player);
                        } else {
                           if (players.canSee(player)) {
                              TitleManager.hide(profile, this);
                           }

                           players.hidePlayer(player);
                        }
                     } else {
                        player.hidePlayer(players);
                        players.hidePlayer(player);
                     }
                  }
               }
            }
         }
      }
   }

   public void save() {
      if (this.name != null && this.tableMap != null) {
         Database.getInstance().save(this.name, this.tableMap);
      }
   }

   public void saveSync() {
      if (this.name != null && this.tableMap != null) {
         Database.getInstance().saveSync(this.name, this.tableMap);
      }
   }

   public void destroy() {
      this.name = null;
      this.hotbar = null;
      this.lastHit.clear();
      this.lastHit = null;
      this.tableMap.values().forEach((containerMap) -> {
         containerMap.values().forEach(DataContainer::gc);
         containerMap.clear();
      });
      this.tableMap.clear();
      this.tableMap = null;
   }

   public String getName() {
      return this.name;
   }

   public boolean isOnline() {
      return this.name != null && isOnline(this.name);
   }

   public Player getPlayer() {
      if (this.player == null) {
         this.player = this.name == null ? null : Bukkit.getPlayerExact(this.name);
      }

      return this.player;
   }

   public void setPlayer(Player player) {
      this.player = player;
      UUID_CACHE.put(this.name.toLowerCase(), player.getUniqueId());
   }

   public void setGame(Object ignored) { }

   public Hotbar getHotbar() {
      return this.hotbar;
   }

   public void setHotbar(Hotbar hotbar) {
      this.hotbar = hotbar;
   }

   public boolean playingGame() { return false; }

   public List<Profile> getLastHitters() {
      List<Profile> hitters = this.lastHit.entrySet().stream()
              .filter(entry -> entry.getValue() > System.currentTimeMillis() && isOnline(entry.getKey()))
              .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
              .map(entry -> getProfile(entry.getKey()))
              .collect(Collectors.toList());
      this.lastHit.clear();
      return hitters;
   }

   public Object getScoreboard() { return null; }
   public void setScoreboard(Object ignored) { }

   public void addStats(String table, String... keys) {
      this.addStats(table, 1L, keys);
   }

   public void addStats(String table, long amount, String... keys) {
      String[] var5 = keys;
      int var6 = keys.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         String key = var5[var7];
         if (!table.toLowerCase().contains("murder") && key.startsWith("monthly")) {
            String month = this.getDataContainer(table, "month").getAsString();
            String current = Calendar.getInstance().get(2) + 1 + "/" + Calendar.getInstance().get(1);
            if (!month.equals(current)) {
               Map<String, DataContainer> containerMap = (Map)this.tableMap.get(table);
               containerMap.keySet().forEach((k) -> {
                  if (k.startsWith("monthly")) {
                     ((DataContainer)containerMap.get(k)).set(0L);
                  }

               });
               ((DataContainer)containerMap.get("month")).set(current);
            }
         }

         this.getDataContainer(table, key).addLong(amount);
      }

   }

   public void setStats(String table, long amount, String... keys) {
      String[] var5 = keys;
      int var6 = keys.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         String key = var5[var7];
         this.getDataContainer(table, key).set(amount);
      }

   }

   public void updateDailyStats(String table, String date, long amount, String key) {
      long currentExpire = this.getStats(table, date);
      this.setStats(table, System.currentTimeMillis(), date);
      if (amount != 0L && (this.getStats(table, key) <= 0L || COMPARE_SDF.format(System.currentTimeMillis()).equals(COMPARE_SDF.format(currentExpire)))) {
         this.addStats(table, amount, key);
      } else {
         this.setStats(table, 0L, key);
      }
   }

   public int addCoins(String table, double amount) {
      this.getDataContainer(table, "coins").addDouble(amount);
      return (int)amount;
   }

   public int addCoinsWM(String table, double amount) {
      amount = this.calculateWM(amount);
      this.addCoins(table, amount);
      return (int)amount;
   }

   public double calculateWM(double amount) {
      double add = 0.0D;
      String booster = this.getBoostersContainer().getEnabled();
      if (booster != null) {
         add = amount * Double.parseDouble(booster.split(":")[0]);
      }

      NetworkBooster nb = Booster.getNetworkBooster(Core.minigame);
      if (nb != null) {
         add += amount * nb.getMultiplier();
      }

      return amount > 0.0D && add == 0.0D ? amount : add;
   }

   public void removeCoins(String table, double amount) {
      this.getDataContainer(table, "coins").removeDouble(amount);
   }

   public long getStats(String table, String... keys) {
      long stat = 0L;
      String[] var5 = keys;
      int var6 = keys.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         String key = var5[var7];
         stat += this.getDataContainer(table, key).getAsLong();
      }

      return stat;
   }

   public long getDailyStats(String table, String date, String key) {
      long currentExpire = this.getStats(table, date);
      if (!COMPARE_SDF.format(System.currentTimeMillis()).equals(COMPARE_SDF.format(currentExpire))) {
         this.setStats(table, 0L, key);
      }

      this.setStats(table, System.currentTimeMillis(), date);
      return this.getStats(table, key);
   }

   public double getCoins(String table) {
      return this.getDataContainer(table, "coins").getAsDouble();
   }

   public String getFormatedStats(String table, String... keys) {
      return StringUtils.formatNumber(this.getStats(table, keys));
   }

   public String getFormatedStatsDouble(String table, String key) {
      return StringUtils.formatNumber(this.getDataContainer(table, key).getAsDouble());
   }

   public DeliveriesContainer getDeliveriesContainer() {
      return (DeliveriesContainer)this.getAbstractContainer("ZonelyCoreProfile", "deliveries", DeliveriesContainer.class);
   }

   public PreferencesContainer getPreferencesContainer() {
      return (PreferencesContainer)this.getAbstractContainer("ZonelyCoreProfile", "preferences", PreferencesContainer.class);
   }

   public TitlesContainer getTitlesContainer() {
      return (TitlesContainer)this.getAbstractContainer("ZonelyCoreProfile", "titles", TitlesContainer.class);
   }

   public BoostersContainer getBoostersContainer() {
      return (BoostersContainer)this.getAbstractContainer("ZonelyCoreProfile", "boosters", BoostersContainer.class);
   }

   public SelectedContainer getSelectedContainer() {
      return (SelectedContainer)this.getAbstractContainer("ZonelyCoreProfile", "selected", SelectedContainer.class);
   }

   public String getLanguage() {
      DataContainer container = this.getDataContainer("ZonelyCoreProfile", "lang");
      Object raw = container.get();
      String locale = raw == null ? null : raw.toString();
      if (locale == null || locale.equalsIgnoreCase("null") || locale.equals("0") || locale.trim().isEmpty()) {
         locale = LanguageManager.getDefaultLocale();
         container.set(locale);
      }
      return locale;
   }

   public void setLanguage(String locale) {
      String resolved = locale == null || locale.trim().isEmpty() ? LanguageManager.getDefaultLocale() : locale;
      this.getDataContainer("ZonelyCoreProfile", "lang").set(resolved);
      LanguageManager.invalidateProfileLocale(this);
   }

   public DataContainer getDataContainer(String table, String key) {
      Map<String, DataContainer> tableData = this.tableMap.get(table);
      if (tableData == null) {
         tableData = new HashMap<>();
         this.tableMap.put(table, tableData);
      }

      DataContainer container = tableData.get(key);
      if (container == null) {
         container = new DataContainer(0L);
         tableData.put(key, container);
      }

      return container;
   }

   public <T extends AbstractContainer> T getAbstractContainer(String table, String key, Class<T> containerClass) {
      return this.getDataContainer(table, key).getContainer(containerClass);
   }

   public Map<String, Map<String, DataContainer>> getTableMap() {
      return this.tableMap;
   }
}
