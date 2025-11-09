package dev.zonely.whiteeffect.plugin;

import dev.zonely.whiteeffect.plugin.config.FileUtils;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.plugin.config.WWriter;
import dev.zonely.whiteeffect.plugin.logger.WLogger;
import dev.zonely.whiteeffect.reflection.Accessors;
import dev.zonely.whiteeffect.reflection.acessors.FieldAccessor;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class WPlugin extends JavaPlugin {
   private final FileUtils fileUtils = new FileUtils(this);

   public WPlugin() {
      try {
         FieldAccessor<Object> accessor = Accessors.getField(JavaPlugin.class, "logger");
         accessor.set(this, new WLogger(this));
      } catch (Throwable ignored) {
      }
      this.start();
   }

   public abstract void start();

   public abstract void load();

   public abstract void enable();

   public abstract void disable();

   public void onLoad() {
      this.load();
   }

   public void onEnable() {
      this.enable();
   }

   public void onDisable() {
      this.disable();
   }

   public WConfig getConfig(String name) {
      return this.getConfig("", name);
   }

   public WConfig getConfig(String path, String name) {
      return WConfig.getConfig(this, "plugins/" + this.getName() + "/" + path, name);
   }

   public WWriter getWriter(File file) {
      return this.getWriter(file, "");
   }

   public WWriter getWriter(File file, String header) {
      return new WWriter((WLogger)this.getLogger(), file, header);
   }

   public FileUtils getFileUtils() {
      return this.fileUtils;
   }
}
