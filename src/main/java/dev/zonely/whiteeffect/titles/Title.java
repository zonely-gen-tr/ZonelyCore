package dev.zonely.whiteeffect.titles;

import com.mongodb.client.MongoCursor;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.MongoDBDatabase;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

public class Title {
   private static final List<Title> TITLES = new ArrayList();
   private static final String ICON_TEMPLATE = lang(
         "titles.icon.template",
         "%material%:%durability% : 1 : hide>genel : name>&l%highligtedname% : desc>&f\\n%description%\\n \\n&7 &8-> &fTitle: &e%title%\\n \\n%action%"
   );
   private static final String ACTION_SELECT = lang(
         "titles.icon.action.select",
         "&6&l>> &eClick to equip."
   );
   private static final String ACTION_DESELECT = lang(
         "titles.icon.action.deselect",
         "&4&l>> &cClick to unequip."
   );
   private static final String ACTION_LOCKED = lang(
         "titles.icon.action.locked",
         "&4&l>> &cYou do not own this title."
   );
   private final String id;
   private final String icon;
   private final String title;

   public Title(String id, String title, String desc) {
      this.id = id;
      this.icon = ICON_TEMPLATE
            .replace("%description%", desc)
            .replace("%title%", title);
      this.title = title;
   }

   public static void setupTitles() {
      TITLES.clear();
      addDefaultTitle("tbk", "&cBridge Slayer");
      addDefaultTitle("tbw", "&6Bridge Guardian");
      addDefaultTitle("tbp", "&eScore Master");
      addDefaultTitle("swk", "&cCelestial Presence");
      addDefaultTitle("sww", "&bSkybreaker");
      addDefaultTitle("swa", "&6Guardian Angel");
      addDefaultTitle("mmd", "&6Detective");
      addDefaultTitle("mmk", "&4Serial Killer");
      addDefaultTitle("bwk", "&cBed Breaker");
      addDefaultTitle("bww", "&6Sleepy Angel");
      addDefaultTitle("bwp", "&4Nightmare");
      if (Database.getInstance() instanceof MongoDBDatabase) {
         MongoDBDatabase database = (MongoDBDatabase) Database.getInstance();
         MongoCursor<Document> cursor = database.getDatabase()
               .getCollection("ZonelyCoreTitles")
               .find()
               .cursor();

         try {
            while (cursor.hasNext()) {
               Document title = cursor.next();
               TITLES.add(new Title(title.getString("_id"), title.getString("name"), title.getString("description")));
            }
         } finally {
            cursor.close();
         }
      }
   }

   private static void addDefaultTitle(String id, String defaultName) {
      String name = lang("titles.items." + id + ".name", defaultName);
      String description = lang(
            "titles.items." + id + ".description",
            defaultDescription(defaultName),
            "title", name,
            "title_raw", StringUtils.stripColors(name)
      );
      TITLES.add(new Title(id, name, description));
   }

   private static String defaultDescription(String titleName) {
      return "&fUnlock this cosmetic by earning the " + titleName + " &ftitle.\\n&7Equip it from the Profile > Titles menu once unlocked.";
   }

   private static String lang(String key, String def, Object... placeholders) {
      try {
         return LanguageManager.get(key, def, placeholders);
      } catch (Throwable ignored) {
         return StringUtils.formatColors(applyPlaceholders(def, placeholders));
      }
   }

   private static String applyPlaceholders(String input, Object... placeholders) {
      if (input == null) {
         return "";
      }
      if (placeholders == null || placeholders.length == 0) {
         return input;
      }
      String result = input;
      for (int i = 0; i + 1 < placeholders.length; i += 2) {
         Object placeholder = placeholders[i];
         Object value = placeholders[i + 1];
         if (placeholder == null) {
            continue;
         }
         result = result.replace("{" + placeholder + "}", value == null ? "" : value.toString());
      }
      return result;
   }

   public static Title getById(String id) {
      return TITLES.stream().filter(title -> title.getId().equals(id)).findFirst().orElse(null);
   }

   public static Collection<Title> listTitles() {
      return TITLES;
   }

   public String getId() {
      return this.id;
   }

   public String getTitle() {
      return this.title;
   }

   public void give(Profile profile) {
      if (!this.has(profile)) {
         profile.getTitlesContainer().add(this);
      }

   }

   public boolean has(Profile profile) {
      return profile.getTitlesContainer().has(this);
   }

   public ItemStack getIcon(Profile profile) {
      boolean has = this.has(profile);
      Title selected = profile.getSelectedContainer().getTitle();

      String material;
      if (has) {
         material = selected != null && selected.equals(this) ? "MAP" : "EMPTY_MAP";
      } else {
         material = "STAINED_GLASS_PANE";
      }

      String action;
      if (!has) {
         action = ACTION_LOCKED;
      } else if (selected != null && selected.equals(this)) {
         action = ACTION_DESELECT;
      } else {
         action = ACTION_SELECT;
      }

      String baseName = (has ? "&a" : "&4") + StringUtils.stripColors(this.title);
      String highlightedName = (has ? "&a&l" : "&c&l") + StringUtils.stripColors(this.title);
      String durability = has ? "0" : "14";

      return BukkitUtils.deserializeItemStack(
            this.icon
                  .replace("%material%", material)
                  .replace("%durability%", durability)
                  .replace("%name%", baseName)
                  .replace("%highligtedname%", highlightedName)
                  .replace("%action%", action)
      );
   }

}






