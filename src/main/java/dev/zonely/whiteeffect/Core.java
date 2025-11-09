package dev.zonely.whiteeffect;

import com.comphenix.protocol.ProtocolLibrary;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.zonely.whiteeffect.booster.Booster;
import org.bstats.bukkit.Metrics;
import dev.zonely.whiteeffect.cmd.Commands;
import dev.zonely.whiteeffect.cmd.LastCreditsCommand;
import dev.zonely.whiteeffect.cmd.MezatOyunVerCommand;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.deliveries.Delivery;
import dev.zonely.whiteeffect.api.ZonelyCoreAPI;
import dev.zonely.whiteeffect.auth.AuthDataSource;
import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.AuthRepository;
import dev.zonely.whiteeffect.auth.command.LoginCommand;
import dev.zonely.whiteeffect.auth.command.EmailCommand;
import dev.zonely.whiteeffect.auth.command.PasswordResetCommand;
import dev.zonely.whiteeffect.auth.command.RegisterCommand;
import dev.zonely.whiteeffect.auth.command.TwoFactorCommand;
import dev.zonely.whiteeffect.auth.listener.AuthListener;
import dev.zonely.whiteeffect.hook.WCoreExpansion;
import dev.zonely.whiteeffect.hook.protocollib.FakeAdapter;
import dev.zonely.whiteeffect.hook.protocollib.HologramAdapter;
import dev.zonely.whiteeffect.hook.protocollib.NPCAdapter;
import dev.zonely.whiteeffect.libraries.MinecraftVersion;
import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.launcher.LauncherSessionService;
import dev.zonely.whiteeffect.listeners.Listeners;
import dev.zonely.whiteeffect.listeners.PluginMessageListener;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.npc.LobbyNPCManager;
import dev.zonely.whiteeffect.hologram.LastCreditHologramManager;
import dev.zonely.whiteeffect.chatbridge.ChatBridgeManager;
import dev.zonely.whiteeffect.inventory.InventorySnapshotManager;
import dev.zonely.whiteeffect.store.LastCreditLeaderboardService;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.league.AlonsoLeagueLevel;
import dev.zonely.whiteeffect.league.AlonsoLeagueSettings;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.titles.Title;
import dev.zonely.whiteeffect.plugin.WPlugin;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.socket.SecureSocketListener;
import dev.zonely.whiteeffect.store.CreditPaperManager;
import dev.zonely.whiteeffect.store.LastCreditPlaceholderService;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;
import dev.zonely.whiteeffect.player.CreditLoadListener;
import dev.zonely.whiteeffect.store.ProductMenuManager;
import dev.zonely.whiteeffect.support.SupportCommand;
import dev.zonely.whiteeffect.support.SupportManager;
import dev.zonely.whiteeffect.utils.ZonelyUpdater;
import dev.zonely.whiteeffect.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import dev.zonely.whiteeffect.cmd.CezaCommand;
import dev.zonely.whiteeffect.listeners.BanLoginListener;
import dev.zonely.whiteeffect.listeners.ChatFilterListener;
import dev.zonely.whiteeffect.utils.PunishmentManager;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.cmd.ReportCommand;
import dev.zonely.whiteeffect.replay.ReplayRecorder;
import dev.zonely.whiteeffect.replay.control.ReplayControlManager;
import dev.zonely.whiteeffect.replay.advanced.AdvancedReplayRecorder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;

import dev.zonely.whiteeffect.store.ProductMenuManager;
import dev.zonely.whiteeffect.cmd.MezatlarCommand;
import dev.zonely.whiteeffect.cmd.MezatVerCommand;
import dev.zonely.whiteeffect.store.menu.AuctionMenuManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

public class Core extends WPlugin {

   public static final List<String> warnings = new ArrayList<>();
   public static final List<String> minigames = Arrays.asList("Sky Wars", "The Bridge", "Katil Kim", "Bed Wars",
         "Build Battle");
   public static boolean validInit;
   public static String minigame = "";
   private static Core instance;
   private static Location lobi;

   private ProductMenuManager productMenuManager;
   private PunishmentManager punishmentManager;
   private ChatBridgeManager chatBridgeManager;
   private InventorySnapshotManager inventorySnapshotManager;

   private LastCreditsMenuManager creditsManager;
   private LastCreditPlaceholderService lastCreditPlaceholderService;
   private LastCreditLeaderboardService lastCreditLeaderboardService;
   private AuthManager authManager;
   private final Map<UUID, ItemStack> openPapers = new ConcurrentHashMap<>();

   private AuctionMenuManager auctionMenuManager;
   private AlonsoLeagueSettings alonsoLeagueSettings;
   private ReportService reportService;

   private boolean socketModuleEnabled;
   private boolean vouchersModuleEnabled;
   private boolean auctionsModuleEnabled;
   private boolean lastCreditsModuleEnabled;
   private boolean reportsModuleEnabled;
   private boolean supportModuleEnabled;
   private boolean authModuleEnabled;
   private boolean supportAllowWithoutAuth;
   private LauncherSessionService launcherSessionService;

   private SupportManager supportManager;

   private SecureSocketListener secureSocketListener;
   private int secureSocketPort = -1;

   private ZonelyCoreAPI api;

   public Map<UUID, ItemStack> getOpenPapers() {
      return openPapers;
   }

   public static Location getLobby() {
      return lobi;
   }

   public AuctionMenuManager getAuctionMenuManager() {
      return auctionMenuManager;
   }

   public SupportManager getSupportManager() {
      return supportManager;
   }

   public LauncherSessionService getLauncherSessionService() {
      return launcherSessionService;
   }

   public AuthManager getAuthManager() {
      return authManager;
   }

   public AlonsoLeagueSettings getAlonsoLeagueSettings() {
      return this.alonsoLeagueSettings;
   }

   public static void setLobby(Location location) {
      lobi = location;
   }

   public static Core getInstance() {
      return instance;
   }

   public void start() {
      instance = this;
   }

   public void load() {
   }

   public void enable() {
      this.saveDefaultConfig();
      this.validatePrimaryConfig();

      new Metrics(this, 27917);

      this.loadAlonsoLeagueSettings();
      LanguageManager.init(this);

      lobi = ((World) Bukkit.getWorlds().get(0)).getSpawnLocation();

      if (Bukkit.getSpawnRadius() != 0) {
         Bukkit.setSpawnRadius(0);
      }

      dev.zonely.whiteeffect.nms.NMS.setupNMS();

      try {
         BufferedReader reader = new BufferedReader(
               new InputStreamReader(this.getResource("blacklist.txt"), StandardCharsets.UTF_8));
         String plugin;

         try {
            while ((plugin = reader.readLine()) != null) {
               if (Bukkit.getPluginManager().getPlugin(plugin.split(" ")[0]) != null) {
                  warnings.add(" - " + plugin);
               }
            }
         } catch (Throwable var7) {
            try {
               reader.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
            throw var7;
         }

         reader.close();
      } catch (IOException var8) {
         this.getLogger().log(Level.SEVERE, "Could not load blacklist.txt: ", var8);
      }

      if (!warnings.isEmpty()) {
         CommandSender sender = Bukkit.getConsoleSender();
         StringBuilder sb = new StringBuilder("\u00A7eYou are using a blocked plugin on this server;");
         Iterator<String> var3 = warnings.iterator();
         while (var3.hasNext()) {
            String warning = var3.next();
            sb.append("\n\u00A7f- \u00A7c").append(warning);
         }
         sb.append("\n ");
         sender.sendMessage(sb.toString());
         warnings.clear();
      }

      if (warnings.isEmpty()) {
         try {
            SimpleCommandMap simpleCommandMap = (SimpleCommandMap) Bukkit.getServer().getClass()
                  .getDeclaredMethod("getCommandMap").invoke(Bukkit.getServer());
            Field field = simpleCommandMap.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) field.get(simpleCommandMap);
            if (knownCommands != null) {
               knownCommands.remove("rl");
               knownCommands.remove("op");
               knownCommands.remove("reload");
               knownCommands.remove("bukkit:rl");
               knownCommands.remove("minecraft:op");
               knownCommands.remove("bukkit:reload");
            }
         } catch (Throwable var5) {
            this.getLogger().log(Level.INFO, "Unable to unregister reload/OP commands (Paper protection - skipped).");
         }

         String placeholderVersion = PlaceholderAPIPlugin.getInstance().getDescription().getVersion();
         if (!"2.10.5".equals(placeholderVersion)) {
            Bukkit.getConsoleSender().sendMessage(
                  "\u00A7eWarning: This plugin was built for \u00A7aPAPI 2.10.5\u00A7e; current version: \u00A7c"
                        + placeholderVersion + "\u00A7e. Continuing anyway.");
         }

         PlaceholderAPI.registerExpansion(new WCoreExpansion());

         try {
            Database.setupDatabase(
                  this.getConfig().getString("database.type"),
                  this.getConfig().getString("database.mysql.host"),
                  this.getConfig().getString("database.mysql.port"),
                  this.getConfig().getString("database.mysql.name"),
                  this.getConfig().getString("database.mysql.username"),
                  this.getConfig().getString("database.mysql.password"),
                  this.getConfig().getBoolean("database.mysql.hikari", false),
                  this.getConfig().getBoolean("database.mysql.mariadb", false),
                  this.getConfig().getString("database.mongodb.url", ""));
         } catch (Throwable t) {
            throw t;
         }

         this.socketModuleEnabled = this.getConfig().getBoolean("modules.socket", true);
         this.vouchersModuleEnabled = this.getConfig().getBoolean("modules.vouchers", true);
         this.auctionsModuleEnabled = this.getConfig().getBoolean("modules.auctions", true);
         this.reportsModuleEnabled = this.getConfig().getBoolean("modules.reports", true);
         this.lastCreditsModuleEnabled = this.getConfig().getBoolean("modules.last-credits", true);
         this.supportModuleEnabled = this.getConfig().getBoolean("modules.support", true);
         this.supportAllowWithoutAuth = this.getConfig("config").getBoolean("support.allow-without-auth", false);
         this.authModuleEnabled = this.getConfig().getBoolean("modules.auth", false);
         this.launcherSessionService = new LauncherSessionService(this);
         this.secureSocketPort = this.getConfig().getInt("socket-port", 454);

         this.productMenuManager = new ProductMenuManager(this);
         PluginCommand storeCommand = this.getCommand("store");
         if (storeCommand != null) {
            storeCommand.setExecutor(new dev.zonely.whiteeffect.cmd.StoreCommand(this));
         }

         this.punishmentManager = new PunishmentManager();
         PluginCommand punishCommand = this.getCommand("punish");
         if (punishCommand != null) {
            punishCommand.setExecutor(new CezaCommand(punishmentManager));
         }

         if (this.reportsModuleEnabled) {
            this.reportService = new ReportService(this, this.punishmentManager);
            ReplayRecorder.start(this);
            ReplayControlManager.init(this);
            AdvancedReplayRecorder.initialize(this);

            if (this.getCommand("report") != null) {
               ReportCommand reportCmd = new ReportCommand(this, this.reportService);
               this.getCommand("report").setExecutor(reportCmd);
            }
            if (this.getCommand("reports") != null) {
               ReportCommand reportCmd = new ReportCommand(this, this.reportService);
               this.getCommand("reports").setExecutor(reportCmd);
            }
         } else {
            this.reportService = null;
            registerDisabledCommand("report", "commands.reports.disabled", "{prefix}&cReports module is disabled.");
            registerDisabledCommand("reports", "commands.reports.disabled", "{prefix}&cReports module is disabled.");
            this.getLogger().info("[Reports] Module disabled via config.");
         }

         new CreditLoadListener(this);

         try {
            getServer().getPluginManager().registerEvents(
                  new ChatFilterListener(this.punishmentManager, this.getConfig("config")),
                  this);
            getServer().getPluginManager().registerEvents(
                  new BanLoginListener(this.punishmentManager),
                  this);
         } catch (Exception e) {
            this.getLogger().log(Level.SEVERE, "Listener registration failed but startup will continue.", e);
         }

         if (this.lastCreditsModuleEnabled) {
            this.creditsManager = new LastCreditsMenuManager(this);
            this.lastCreditPlaceholderService = new LastCreditPlaceholderService(this);
            this.lastCreditPlaceholderService.start();
            this.lastCreditLeaderboardService = new LastCreditLeaderboardService(this);
            PluginCommand lastCreditsCommand = this.getCommand("lastcredits");
            if (lastCreditsCommand != null) {
               lastCreditsCommand.setExecutor(new LastCreditsCommand(this));
            }
         } else {
            this.creditsManager = null;
            this.lastCreditPlaceholderService = null;
            this.lastCreditLeaderboardService = null;
            registerDisabledCommand("lastcredits", "commands.last-credits.disabled",
                  "{prefix}&cLast credits module is disabled.");
            this.getLogger().info("[LastCredits] Module disabled via config.");
         }

         if (this.vouchersModuleEnabled) {
            new CreditPaperManager(this);
         } else {
            registerDisabledCommand("creditvoucher", "commands.credit-voucher.disabled",
                  "{prefix}&cCredit vouchers are disabled.");
            this.getLogger().info("[Vouchers] Module disabled via config.");
         }

         if (this.auctionsModuleEnabled) {
            this.auctionMenuManager = new AuctionMenuManager(this);

            PluginCommand auctionsCommand = getCommand("auctions");
            if (auctionsCommand != null) {
               auctionsCommand.setExecutor(new MezatlarCommand(this));
            }
            PluginCommand auctionCommand = getCommand("auction");
            if (auctionCommand != null) {
               auctionCommand.setExecutor(new MezatVerCommand(this));
            }
            PluginCommand auctionGiveCommand = getCommand("auctiongive");
            if (auctionGiveCommand != null) {
               auctionGiveCommand.setExecutor(new MezatOyunVerCommand(this));
            }
         } else {
            this.auctionMenuManager = null;
            registerDisabledCommand("auctions", "commands.auctions.disabled",
                  "{prefix}&cAuctions module is disabled.");
            registerDisabledCommand("auction", "commands.auctions.disabled",
                  "{prefix}&cAuctions module is disabled.");
            registerDisabledCommand("auctiongive", "commands.auctions.disabled",
                  "{prefix}&cAuctions module is disabled.");
            this.getLogger().info("[Auctions] Module disabled via config.");
         }

         if (this.authModuleEnabled) {
            this.authManager = new AuthManager(this);
            this.getServer().getPluginManager().registerEvents(new AuthListener(this.authManager), this);

            PluginCommand loginCommand = this.getCommand("login");
            if (loginCommand != null) {
               LoginCommand loginExecutor = new LoginCommand(this.authManager);
               loginCommand.setExecutor(loginExecutor);
               loginCommand.setTabCompleter(loginExecutor);
            }

            PluginCommand registerCommand = this.getCommand("register");
            if (registerCommand != null) {
               RegisterCommand registerExecutor = new RegisterCommand(this.authManager);
               registerCommand.setExecutor(registerExecutor);
               registerCommand.setTabCompleter(registerExecutor);
            }

            PluginCommand emailCommand = this.getCommand("email");
            if (emailCommand != null) {
               EmailCommand emailExecutor = new EmailCommand(this.authManager);
               emailCommand.setExecutor(emailExecutor);
               emailCommand.setTabCompleter(emailExecutor);
            }

            PluginCommand twoFactorCommand = this.getCommand("twofactor");
            if (twoFactorCommand != null) {
               TwoFactorCommand twoFactorExecutor = new TwoFactorCommand(this.authManager);
               twoFactorCommand.setExecutor(twoFactorExecutor);
               twoFactorCommand.setTabCompleter(twoFactorExecutor);
            }

            PluginCommand passwordResetCommand = this.getCommand("passwordreset");
            if (passwordResetCommand != null) {
               PasswordResetCommand passwordResetExecutor = new PasswordResetCommand(this.authManager);
               passwordResetCommand.setExecutor(passwordResetExecutor);
               passwordResetCommand.setTabCompleter(passwordResetExecutor);
            }
         } else {
            this.authManager = null;
            registerDisabledCommand("login", "auth.disabled", "{prefix}&cAuthentication module is disabled.");
            registerDisabledCommand("register", "auth.disabled", "{prefix}&cAuthentication module is disabled.");
            registerDisabledCommand("email", "auth.email.disabled", "{prefix}&cEmail verification is disabled.");
            registerDisabledCommand("twofactor", "auth.disabled", "{prefix}&cAuthentication module is disabled.");
            registerDisabledCommand("passwordreset", "auth.password-reset.disabled",
                  "{prefix}&cPassword reset feature is disabled.");
            this.getLogger().info("[Auth] Module disabled via config.");
         }

         if (this.supportModuleEnabled) {
            AuthManager activeAuthManager = this.authManager;
            AuthRepository fallbackRepository = null;
            if (activeAuthManager == null && this.supportAllowWithoutAuth) {
               try {
                  AuthDataSource dataSource = AuthDataSource.fromConfig(this.getConfig("config"));
                  fallbackRepository = new AuthRepository(Database.getInstance(), dataSource.getColumns());
                  this.getLogger().info(
                        "[Support] Auth module disabled; using direct database lookup for support identities.");
               } catch (Exception ex) {
                  this.getLogger().log(Level.WARNING,
                        "[Support] Unable to initialise auth datasource for support. Support module will be disabled.",
                        ex);
               }
            }

            if (activeAuthManager == null && fallbackRepository == null) {
               this.getLogger().warning(
                     "[Support] Support system requires authentication data. Enable auth module or set support.allow-without-auth=true with valid datasource.");
               this.supportManager = null;
               registerDisabledCommand("support", "commands.support.disabled",
                     "{prefix}&cSupport module is disabled.");
            } else {
               int defaultCategoryId = this.getConfig("config").getInt("support.default-category-id", -1);
               this.supportManager = new SupportManager(this, defaultCategoryId, activeAuthManager, fallbackRepository);
               this.supportManager.start();
               PluginCommand supportCommand = this.getCommand("support");
               if (supportCommand != null) {
                  SupportCommand supportExecutor = new SupportCommand(this, this.supportManager);
                  supportCommand.setExecutor(supportExecutor);
                  supportCommand.setTabCompleter(supportExecutor);
               }
            }
         } else {
            this.supportManager = null;
            registerDisabledCommand("support", "commands.support.disabled", "{prefix}&cSupport module is disabled.");
            this.getLogger().info("[Support] Module disabled via config.");
         }

         if (this.socketModuleEnabled) {
            if (!startSecureSocketListener()) {
               this.getLogger().warning("[SecureSocket] Listener did not start. Check token/port configuration.");
            }
         } else {
            this.getLogger().info("[SecureSocket] Module disabled via config.");
         }

         NPCLibrary.setupNPCs(this);
         HologramLibrary.purgeOrphanedHolograms();
         HologramLibrary.setupHolograms(this);
         this.setupRoles();
         FakeManager.setupFake();
         Title.setupTitles();
         Booster.setupBoosters();
         Delivery.setupDeliveries();
         Commands.setupCommands();
         Listeners.setupListeners();

         this.chatBridgeManager = new ChatBridgeManager(this);
         this.chatBridgeManager.start();

         this.inventorySnapshotManager = new InventorySnapshotManager(this);
         this.inventorySnapshotManager.start();

         LobbyNPCManager.init(this);
         if (this.lastCreditsModuleEnabled && this.lastCreditLeaderboardService != null) {
            LastCreditHologramManager.init(this, this.lastCreditLeaderboardService);
         }

         this.api = new ZonelyCoreAPI(this);

         ProtocolLibrary.getProtocolManager().addPacketListener(new FakeAdapter());
         try {
            ProtocolLibrary.getProtocolManager().addPacketListener(new NPCAdapter());
         } catch (Throwable ignored) {
         }
         try {
            ProtocolLibrary.getProtocolManager().addPacketListener(new HologramAdapter());
         } catch (Throwable ignored) {
         }

         this.getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
         this.getServer().getMessenger().registerOutgoingPluginChannel(this, "zonelycore:main");
         this.getServer().getMessenger().registerIncomingPluginChannel(this, "zonelycore:main",
               new PluginMessageListener());

         Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> (new ZonelyUpdater(this, 9)).run());

         validInit = true;
         Bukkit.getConsoleSender().sendMessage("\u00A7e");
         Bukkit.getConsoleSender().sendMessage("\u00A76       _____                _       ");
         Bukkit.getConsoleSender().sendMessage("\u00A7e      |__  /___  _ __   ___| |_   _ ");
         Bukkit.getConsoleSender().sendMessage("\u00A74        / // _ \\| '_ \\ / _ \\ | | | |");
         Bukkit.getConsoleSender().sendMessage("\u00A79       / /| (_) | | | |  __/ | |_| |");
         Bukkit.getConsoleSender().sendMessage("\u00A7b      /____\\___/|_| |_|\\___|_|\\__, |");
         Bukkit.getConsoleSender().sendMessage("\u00A76                              |___/ ");
         Bukkit.getConsoleSender().sendMessage("");
         Bukkit.getConsoleSender().sendMessage("\u00A76  Thank you for using ZONELY.GEN.TR plugins.");
         Bukkit.getConsoleSender().sendMessage("\u00A7e  This product is designed exclusively for Zonely Web Management.");
         Bukkit.getConsoleSender().sendMessage("\u00A7c");
         Bukkit.getConsoleSender().sendMessage("\u00A7e  Product Author: \u00A76WhiteEffect (Orcun Donmez)");
         Bukkit.getConsoleSender().sendMessage("\u00A7e  Discord Server: \u00A76https://zonely.gen.tr/discord");
         Bukkit.getConsoleSender().sendMessage("\u00A7e  Origin: \u00A76Turkey, Istanbul");
         Bukkit.getConsoleSender().sendMessage("\u00A7e");
         Bukkit.getConsoleSender().sendMessage(
               "\u00A7e  Running Plugin: \u00A76ZonelyCore v" + getInstance().getDescription().getVersion() + "\u00A7e");
         Bukkit.getConsoleSender().sendMessage("\u00A7e");
      }
   }

   public void disable() {
      if (validInit) {
         Bukkit.getOnlinePlayers().forEach((player) -> {
            Profile profile = Profile.unloadProfile(player.getName());
            if (profile != null) {
               profile.saveSync();
               this.getLogger().info("Saved " + profile.getName());
               profile.destroy();
            }
         });

         if (chatBridgeManager != null) {
            chatBridgeManager.shutdown();
         }
         if (inventorySnapshotManager != null) {
            inventorySnapshotManager.shutdown();
         }

         if (supportManager != null) {
            supportManager.shutdown();
            supportManager = null;
         }

         if (authManager != null) {
            authManager.shutdown();
            authManager = null;
         }

         Database.getInstance().close();
      }

      LobbyNPCManager.shutdown();
      LastCreditHologramManager.shutdown();

      if (lastCreditLeaderboardService != null) {
         lastCreditLeaderboardService.shutdown();
      }

      if (lastCreditPlaceholderService != null) {
         lastCreditPlaceholderService.stop();
      }

      stopSecureSocketListener();
      this.secureSocketPort = -1;
      ZonelyCoreAPI.reset();
      this.api = null;

      try {
         dev.zonely.whiteeffect.replay.ReplayRecorder rec = dev.zonely.whiteeffect.replay.ReplayRecorder.get();
         if (rec != null)
            rec.shutdown();
      } catch (Throwable ignored) {
      }
      try {
         ReplayControlManager.shutdownAll();
      } catch (Throwable ignored) {
      }
      try {
         AdvancedReplayRecorder recorder = AdvancedReplayRecorder.get();
         if (recorder != null) {
            recorder.shutdown();
         }
      } catch (Throwable ignored) {
      }

      File update = new File("plugins/ZonelyCore/update", "ZonelyCore.jar");

      if (update.exists()) {
         try {
            this.getFileUtils().deleteFile(new File("plugins/ZonelyCore.jar"));
            this.getFileUtils().copyFile(new FileInputStream(update), new File("plugins/ZonelyCore.jar"));
            this.getFileUtils().deleteFile(update.getParentFile());
            this.getLogger().info("ZonelyCore updated.");
         } catch (Exception var3) {
            var3.printStackTrace();
         }
      }

      this.getLogger().info("Plugin deactivated.");
   }

   public ProductMenuManager getProductMenuManager() {
      return this.productMenuManager;
   }

   public PunishmentManager getPunishmentManager() {
      return this.punishmentManager;
   }

   public ChatBridgeManager getChatBridgeManager() {
      return this.chatBridgeManager;
   }

   public LastCreditsMenuManager getCreditsManager() {
      return creditsManager;
   }

   public LastCreditPlaceholderService getLastCreditPlaceholderService() {
      return lastCreditPlaceholderService;
   }

   public LastCreditLeaderboardService getLastCreditLeaderboardService() {
      return lastCreditLeaderboardService;
   }

   public ReportService getReportService() {
      return this.reportService;
   }

   public boolean isSocketEnabled() {
      return this.socketModuleEnabled;
   }

   public boolean isVouchersEnabled() {
      return this.vouchersModuleEnabled;
   }

   public boolean isAuctionsEnabled() {
      return this.auctionsModuleEnabled;
   }

   public boolean isLastCreditsEnabled() {
      return this.lastCreditsModuleEnabled;
   }

   public boolean isReportsEnabled() {
      return this.reportsModuleEnabled;
   }

   public ZonelyCoreAPI getApi() {
      return this.api;
   }

   public SecureSocketListener getSecureSocketListener() {
      return this.secureSocketListener;
   }

   public int getSecureSocketPort() {
      return this.secureSocketPort;
   }

   public boolean isSecureSocketActive() {
      return this.secureSocketListener != null && this.secureSocketListener.isRunning();
   }

   public synchronized boolean startSecureSocketListener() {
      if (!this.socketModuleEnabled) {
         return false;
      }
      if (this.secureSocketListener != null && this.secureSocketListener.isRunning()) {
         return true;
      }

      String token = this.getConfig().getString("web-token", "");
      if (token == null || token.trim().isEmpty()) {
         this.getLogger().warning("[SecureSocket] web-token is empty; listener will not start.");
         return false;
      }

      int port = this.secureSocketPort > 0 ? this.secureSocketPort : this.getConfig().getInt("socket-port", 454);
      try {
         ServerSocket serverSocket = new ServerSocket(port);
         SecureSocketListener listener = new SecureSocketListener(this, serverSocket, token, port);
         listener.start();
         this.secureSocketListener = listener;
         this.secureSocketPort = port;
         this.getLogger().info("[SecureSocket] Listening started (socket-port: " + port + ")");
         return true;
      } catch (Exception e) {
         this.getLogger().log(Level.SEVERE, "[SecureSocket] Unable to bind socket port: " + port, e);
         this.secureSocketListener = null;
         return false;
      }
   }

   public synchronized void stopSecureSocketListener() {
      if (this.secureSocketListener != null) {
         try {
            this.secureSocketListener.shutdown();
         } catch (Throwable ignored) {
         }
         this.secureSocketListener = null;
      }
   }

   public synchronized boolean restartSecureSocketListener() {
      stopSecureSocketListener();
      return startSecureSocketListener();
   }

   private void registerDisabledCommand(String commandName, String messageKey, String fallbackMessage) {
      PluginCommand command = this.getCommand(commandName);
      if (command == null) {
         return;
      }
      String prefix = LanguageManager.get("prefix.lobby", "&3Lobby &8>> ");
      command.setExecutor((sender, cmd, label, args) -> {
         LanguageManager.send(sender, messageKey, fallbackMessage, "prefix", prefix);
         return true;
      });
   }

   public void reloadPluginResources() {
      WConfig config = this.getConfig("config");
      config.reload();
      this.validatePrimaryConfig();
      LanguageManager.reload(this);
      this.loadAlonsoLeagueSettings();
   }

   private void validatePrimaryConfig() {
      WConfig config = this.getConfig("config");
      File configFile = config.getFile();
      YamlConfiguration current = config.getRawConfig();
      YamlConfiguration defaults = this.loadDefaultConfigTemplate();
      if (configFile == null || current == null || defaults == null) {
         return;
      }

      List<String> missingKeys = new ArrayList<>();
      List<String> wrongTypes = new ArrayList<>();
      this.collectConfigIssues(defaults, current, "", missingKeys, wrongTypes);

      if (missingKeys.isEmpty() && wrongTypes.isEmpty()) {
         return;
      }

      this.getLogger().warning(String.format(
            "[Config] Detected invalid entries in config.yml. Missing=%s, WrongType=%s. Regenerating while preserving database settings.",
            missingKeys.isEmpty() ? "-" : String.join(", ", missingKeys),
            wrongTypes.isEmpty() ? "-" : String.join(", ", wrongTypes)));

      try {
         Path backup = configFile.toPath().resolveSibling("config.yml.bak");
         Files.copy(configFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
         this.getLogger().info("[Config] Existing config.yml backed up to " + backup.getFileName());
      } catch (IOException ex) {
         this.getLogger().log(Level.WARNING, "[Config] Failed to backup config.yml before regeneration.", ex);
      }

      ConfigurationSection databaseSection = current.getConfigurationSection("database");
      if (databaseSection != null) {
         defaults.set("database", null);
         defaults.createSection("database", databaseSection.getValues(true));
      }

      try {
         defaults.save(configFile);
         config.reload();
         this.getLogger().info("[Config] config.yml regenerated successfully.");
      } catch (IOException ex) {
         this.getLogger().log(Level.SEVERE, "[Config] Failed to regenerate config.yml.", ex);
      }
   }

   private void collectConfigIssues(ConfigurationSection defaults, YamlConfiguration current, String path,
         List<String> missingKeys, List<String> wrongTypes) {
      for (String key : defaults.getKeys(false)) {
         String fullPath = path.isEmpty() ? key : path + "." + key;
         if (fullPath.equals("database") || fullPath.startsWith("database.")) {
            continue;
         }

         if (defaults.isConfigurationSection(key)) {
            ConfigurationSection expectedSection = defaults.getConfigurationSection(key);
            ConfigurationSection currentSection = current.getConfigurationSection(fullPath);
            if (expectedSection == null) {
               continue;
            }
            if (currentSection == null) {
               if (!current.contains(fullPath)) {
                  missingKeys.add(fullPath);
               } else {
                  wrongTypes.add(fullPath + " (expected section)");
               }
               continue;
            }
            collectConfigIssues(expectedSection, current, fullPath, missingKeys, wrongTypes);
            continue;
         }

         if (!current.contains(fullPath)) {
            missingKeys.add(fullPath);
            continue;
         }

         Object expectedValue = defaults.get(key);
         Object currentValue = current.get(fullPath);
         if (!isConfigValueCompatible(expectedValue, currentValue)) {
            wrongTypes.add(fullPath);
         }
      }
   }

   private boolean isConfigValueCompatible(Object expectedValue, Object currentValue) {
      if (expectedValue == null || currentValue == null) {
         return true;
      }
      if (expectedValue instanceof ConfigurationSection) {
         return currentValue instanceof ConfigurationSection;
      }
      if (expectedValue instanceof List) {
         return currentValue instanceof List;
      }
      if (expectedValue instanceof Number) {
         return currentValue instanceof Number;
      }
      if (expectedValue instanceof Boolean) {
         return currentValue instanceof Boolean;
      }
      if (expectedValue instanceof String) {
         return currentValue instanceof String;
      }
      return expectedValue.getClass().isInstance(currentValue);
   }

   private YamlConfiguration loadDefaultConfigTemplate() {
      InputStream stream = this.getResource("config.yml");
      if (stream == null) {
         this.getLogger().warning("[Config] Default config.yml resource not found; skipping validation.");
         return null;
      }

      try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
         YamlConfiguration defaults = new YamlConfiguration();
         defaults.load(reader);
         return defaults;
      } catch (IOException | InvalidConfigurationException ex) {
         this.getLogger().log(Level.SEVERE, "[Config] Unable to read default config.yml template.", ex);
         return null;
      }
   }

   private void loadAlonsoLeagueSettings() {
      WConfig config = this.getConfig("config");

      List<String> baseDescription = new ArrayList<>(config.getStringList("alonsoleagues.description.base"));
      if (baseDescription.isEmpty()) {
         baseDescription.addAll(Arrays.asList(
               "&8[Seviye]&e",
               "",
               "&fBu seviyeye geçiş yapmak için",
               "&foyun sunucularında oyun kazanıp,",
               "&fkullanıcı öldürerek ve oynama süresiyle",
               "&fkilidini açabilirsin.",
               "",
               "&7 &8■ &fGörünüm: {appearance} &f{player}"
         ));
      }

      List<String> progressDescription = new ArrayList<>(config.getStringList("alonsoleagues.description.progress"));
      if (progressDescription.isEmpty()) {
         progressDescription.addAll(Arrays.asList(
               "&7 &8■ &fGereken Tecrübe Puanı: &c{current}/{required} {bar}&7 (%{percent})",
               "",
               "&7 &8■ &fDurum: &cDEVAM EDİYOR!"
         ));
      }

      List<String> completedDescription = new ArrayList<>(
            config.getStringList("alonsoleagues.description.completed"));
      if (completedDescription.isEmpty()) {
         completedDescription.addAll(Arrays.asList(
               "",
               "&7 &8■ &fDurum: &aTAMAMLANMIŞ!"
         ));
      }

      int progressBarLength = config.getInt("alonsoleagues.progress-bar.length", 10);
      String progressBarSymbol = config.getString("alonsoleagues.progress-bar.symbol");
      if (progressBarSymbol == null || progressBarSymbol.isEmpty()) {
         progressBarSymbol = "|";
      }
      progressBarSymbol = StringUtils.formatColors(progressBarSymbol);

      String progressColor = config.getString("alonsoleagues.progress-bar.progress-color", "&b");
      String remainingColor = config.getString("alonsoleagues.progress-bar.remaining-color", "&7");
      progressColor = StringUtils.formatColors(progressColor);
      remainingColor = StringUtils.formatColors(remainingColor);

      List<AlonsoLeagueLevel> levels = new ArrayList<>();
      ConfigurationSection section = config.getSection("alonsoleagues.levels");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            ConfigurationSection levelSection = section.getConfigurationSection(key);
            if (levelSection == null) {
               continue;
            }

            int slot = levelSection.getInt("slot", -1);
            String icon = levelSection.getString("icon", "");
            String name = levelSection.getString("name", key);
            String completedName = levelSection.getString("completed-name", name);
            String appearance = levelSection.getString("appearance", completedName);
            int requiredPoints = levelSection.getInt("required-points", 0);
            boolean glow = levelSection.getBoolean("glow-on-complete", true);

            if (slot < 0 || icon == null || icon.isEmpty()) {
               this.getLogger().warning("AlonsoLeagues level \"" + key + "\" skipped due to missing data.");
               continue;
            }

            levels.add(new AlonsoLeagueLevel(slot, requiredPoints, icon, name, completedName, appearance, glow));
         }
      }

      if (levels.isEmpty()) {
         levels.addAll(this.getDefaultAlonsoLeagueLevels());
      }

      this.alonsoLeagueSettings = new AlonsoLeagueSettings(
            baseDescription,
            progressDescription,
            completedDescription,
            progressBarLength,
            progressBarSymbol,
            progressColor,
            remainingColor,
            levels
      );
   }

   private List<AlonsoLeagueLevel> getDefaultAlonsoLeagueLevels() {
      List<AlonsoLeagueLevel> defaults = new ArrayList<>();

      defaults.add(new AlonsoLeagueLevel(10, 0, "173 : 1",
            "&a&lKömür I", "&a&lKömür I", "&8Kömür I", true));
      defaults.add(new AlonsoLeagueLevel(11, 1000, "173 : 1",
            "&8&lKömür II", "&a&lKömür II", "&8Kömür II", true));
      defaults.add(new AlonsoLeagueLevel(12, 1200, "173 : 1",
            "&8&lKömür III", "&a&lKömür III", "&8Kömür III", true));

      defaults.add(new AlonsoLeagueLevel(14, 1450, "42 : 1",
            "&f&lDemir I", "&a&lDemir I", "&fDemir I", true));
      defaults.add(new AlonsoLeagueLevel(15, 1750, "42 : 1",
            "&f&lDemir II", "&a&lDemir II", "&fDemir II", true));
      defaults.add(new AlonsoLeagueLevel(16, 2100, "42 : 1",
            "&f&lDemir III", "&a&lDemir III", "&fDemir III", true));

      defaults.add(new AlonsoLeagueLevel(19, 2500, "152 : 1",
            "&c&lKızıltaş I", "&a&lKızıltaş I", "&cKızıltaş I", true));
      defaults.add(new AlonsoLeagueLevel(20, 2950, "152 : 1",
            "&c&lKızıltaş II", "&a&lKızıltaş II", "&cKızıltaş II", true));
      defaults.add(new AlonsoLeagueLevel(21, 3450, "152 : 1",
            "&c&lKızıltaş III", "&a&lKızıltaş III", "&cKızıltaş III", true));

      defaults.add(new AlonsoLeagueLevel(23, 4000, "41 : 1",
            "&c&lAltın I", "&a&lAltın I", "&6Altın I", true));
      defaults.add(new AlonsoLeagueLevel(24, 4600, "41 : 1",
            "&c&lAltın II", "&a&lAltın II", "&6Altın II", true));
      defaults.add(new AlonsoLeagueLevel(25, 5250, "41 : 1",
            "&c&lAltın III", "&a&lAltın III", "&6Altın III", true));

      defaults.add(new AlonsoLeagueLevel(28, 5950, "57 : 1",
            "&c&lElmas I", "&b&lElmas I", "&bElmas I", true));
      defaults.add(new AlonsoLeagueLevel(29, 6700, "57 : 1",
            "&c&lElmas II", "&b&lElmas II", "&bElmas II", true));
      defaults.add(new AlonsoLeagueLevel(30, 7500, "57 : 1",
            "&c&lElmas III", "&b&lElmas III", "&bElmas III", true));

      defaults.add(new AlonsoLeagueLevel(32, 8350, "49 : 1",
            "&c&lObsidyen I", "&5&lObsidyen I", "&5Obsidyen I", true));
      defaults.add(new AlonsoLeagueLevel(33, 9250, "49 : 1",
            "&c&lObsidyen II", "&5&lObsidyen II", "&5Obsidyen II", true));
      defaults.add(new AlonsoLeagueLevel(34, 10200, "49 : 1",
            "&c&lObsidyen III", "&5&lObsidyen III", "&5Obsidyen III", true));

      defaults.add(new AlonsoLeagueLevel(37, 11200, "133 : 1",
            "&c&lZümrüt I", "&2&lZümrüt I", "&2Zümrüt I", true));
      defaults.add(new AlonsoLeagueLevel(38, 12250, "133 : 1",
            "&c&lZümrüt II", "&2&lZümrüt II", "&2Zümrüt II", true));
      defaults.add(new AlonsoLeagueLevel(39, 13350, "133 : 1",
            "&c&lZümrüt III", "&2&lZümrüt III", "&2Zümrüt III", true));

      defaults.add(new AlonsoLeagueLevel(41, 14500, "159:2 : 1",
            "&c&lAmetist I", "&d&lAmetist I", "&dAmetist I", true));
      defaults.add(new AlonsoLeagueLevel(42, 15700, "159:2 : 1",
            "&c&lAmetist II", "&d&lAmetist II", "&dAmetist II", true));
      defaults.add(new AlonsoLeagueLevel(43, 16950, "159:2 : 1",
            "&c&lAmetist III", "&d&lAmetist III", "&dAmetist III", true));

      return defaults;
   }

   private void setupRoles() {
      WConfig config = this.getConfig("config");
      Iterator<String> var2 = config.getSection("groups").getKeys(false).iterator();

      while (var2.hasNext()) {
         String key = var2.next();
         String name = config.getString("groups." + key + ".name");
         String prefix = config.getString("groups." + key + ".prefix");
         String permission = config.getString("groups." + key + ".permission");
         boolean broadcast = config.getBoolean("groups." + key + ".broadcast", true);
         boolean alwaysVisible = config.getBoolean("groups." + key + ".alwaysvisible", false);

         Role.listRoles().add(new Role(name, prefix, permission, alwaysVisible, broadcast));
      }

      if (Role.listRoles().isEmpty()) {
         Role.listRoles().add(new Role("&7Oyuncu", "&7", "", false, false));
      }
   }
}
