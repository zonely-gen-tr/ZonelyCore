package dev.zonely.whiteeffect.plugin.logger;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLogger;

public class WLogger extends PluginLogger {
   private final Plugin plugin;
   private final String prefix;
   private final CommandSender sender;

   public WLogger(Plugin plugin) {
      super(plugin);
      this.plugin = plugin;
      this.prefix = "[" + plugin.getName() + "] ";
      this.sender = Bukkit.getConsoleSender();
   }

   public WLogger(WLogger parent, String prefix) {
      super(parent.plugin);
      this.plugin = parent.plugin;
      this.prefix = parent.prefix + prefix;
      this.sender = Bukkit.getConsoleSender();
   }

   public void run(Level level, String method, Runnable runnable) {
      try {
         runnable.run();
      } catch (Exception var5) {
         this.log(level, method.replace("${n}", this.plugin.getName()).replace("${v}", this.plugin.getDescription().getVersion()), var5);
      }

   }

   public void log(LogRecord logRecord) {
      WLogger.WLevel level = WLogger.WLevel.fromName(logRecord.getLevel().getName());
      if (level != null) {
         String message = logRecord.getMessage();
         if (!message.equals("Default system encoding may have misread config.yml from plugin jar")) {
            StringBuilder result = new StringBuilder(this.prefix + "§f" + message);
            if (logRecord.getThrown() != null) {
               result.append("\n").append(logRecord.getThrown().getLocalizedMessage());
               StackTraceElement[] var5 = logRecord.getThrown().getStackTrace();
               int var6 = var5.length;

               for(int var7 = 0; var7 < var6; ++var7) {
                  StackTraceElement ste = var5[var7];
                  result.append("\n").append(ste.toString());
               }
            }

            this.sender.sendMessage(level.format(result.toString()));
         }
      }
   }

   public WLogger getModule(String module) {
      return new WLogger(this, module + ": ");
   }

   private static enum WLevel {
      INFO("§6"),
      WARNING("§6"),
      SEVERE("§c");

      private final String color;

      private WLevel(String color) {
         this.color = color;
      }

      public static WLogger.WLevel fromName(String name) {
         WLogger.WLevel[] var1 = values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            WLogger.WLevel level = var1[var3];
            if (level.name().equalsIgnoreCase(name)) {
               return level;
            }
         }

         return null;
      }

      public String format(String message) {
         return this.color + message;
      }
   }
}
