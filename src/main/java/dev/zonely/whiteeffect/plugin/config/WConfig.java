package dev.zonely.whiteeffect.plugin.config;

import dev.zonely.whiteeffect.plugin.WPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;

public class WConfig {
   private static final Map<String, WConfig> cache = new HashMap();
   private final WPlugin plugin;
   private final File file;
   private YamlConfiguration config;

   private WConfig(WPlugin plugin, String path, String name) {
      this.plugin = plugin;
      this.file = new File(path + "/" + name + ".yml");
      if (!this.file.exists()) {
         this.file.getParentFile().mkdirs();
         InputStream in = plugin.getResource(name + ".yml");
         if (in != null) {
            plugin.getFileUtils().copyFile(in, this.file);
         } else {
            try {
               this.file.createNewFile();
            } catch (IOException var7) {
               plugin.getLogger().log(Level.SEVERE, "Unexpected error while copying file. \"" + this.file.getName() + "\": ", var7);
            }
         }
      }

      try {
         this.config = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(this.file), "UTF-8"));
      } catch (IOException var6) {
         plugin.getLogger().log(Level.SEVERE, "Unexpected error while creating configuration. \"" + this.file.getName() + "\": ", var6);
      }

   }

   public static WConfig getConfig(WPlugin plugin, String path, String name) {
      if (!cache.containsKey(path + "/" + name)) {
         cache.put(path + "/" + name, new WConfig(plugin, path, name));
      }

      return (WConfig)cache.get(path + "/" + name);
   }

   public boolean createSection(String path) {
      this.config.createSection(path);
      return this.save();
   }

   public boolean set(String path, Object obj) {
      this.config.set(path, obj);
      return this.save();
   }

   public boolean contains(String path) {
      return this.config.contains(path);
   }

   public Object get(String path) {
      return this.config.get(path);
   }

   public int getInt(String path) {
      return this.config.getInt(path);
   }

   public int getInt(String path, int def) {
      return this.config.getInt(path, def);
   }

   public double getDouble(String path) {
      return this.config.getDouble(path);
   }

   public double getDouble(String path, double def) {
      return this.config.getDouble(path, def);
   }

   public String getString(String path) {
      return this.config.getString(path);
   }

   public String getString(String path, String def) {
      return this.config.getString(path, def);
   }

   public boolean getBoolean(String path) {
      return this.config.getBoolean(path);
   }

   public boolean getBoolean(String path, boolean def) {
      return this.config.getBoolean(path, def);
   }

   public List<String> getStringList(String path) {
      return this.config.getStringList(path);
   }

   public List<Integer> getIntegerList(String path) {
      return this.config.getIntegerList(path);
   }

   public Set<String> getKeys(boolean flag) {
      return this.config.getKeys(flag);
   }

   public ConfigurationSection getSection(String path) {
      return this.config.getConfigurationSection(path);
   }

   public void reload() {
      try {
         this.config = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(this.file), "UTF-8"));
      } catch (IOException var2) {
         this.plugin.getLogger().log(Level.SEVERE, "Unexpected error while loading configuration. \"" + this.file.getName() + "\": ", var2);
      }

   }

   public boolean save() {
      try {
         this.config.save(this.file);
         return true;
      } catch (IOException var2) {
         this.plugin.getLogger().log(Level.SEVERE, "Unexpected error while saving configuration. \"" + this.file.getName() + "\": ", var2);
         return false;
      }
   }

   public File getFile() {
      return this.file;
   }

   public YamlConfiguration getRawConfig() {
      return this.config;
   }
}
