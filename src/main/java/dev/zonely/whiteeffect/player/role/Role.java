package dev.zonely.whiteeffect.player.role;

import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.cache.RoleCache;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Role {
   private static final List<Role> ROLES = new ArrayList();
   private final int id;
   private final String name;
   private final String prefix;
   private final String permission;
   private final boolean alwaysVisible;
   private final boolean broadcast;

   public Role(String name, String prefix, String permission, boolean alwaysVisible, boolean broadcast) {
      this.id = ROLES.size();
      this.name = StringUtils.formatColors(name);
      this.prefix = StringUtils.formatColors(prefix);
      this.permission = permission;
      this.alwaysVisible = alwaysVisible;
      this.broadcast = broadcast;
   }

   public static String getPrefixed(String name) {
      return getPrefixed(name, false);
   }

   public static String getColored(String name) {
      return getColored(name, false);
   }

   public static String getPrefixed(String name, boolean removeFake) {
      return getTaggedName(name, false, removeFake);
   }

   public static String getColored(String name, boolean removeFake) {
      return getTaggedName(name, true, removeFake);
   }

   private static String getTaggedName(String name, boolean onlyColor, boolean removeFake) {
      String prefix = "&7";
      if (!removeFake && Manager.isFake(name)) {
         prefix = Manager.getFakeRole(name).getPrefix();
         if (onlyColor) {
            prefix = StringUtils.getLastColor(prefix);
         }

         return prefix + Manager.getFake(name);
      } else {
         Object target = Manager.getPlayer(name);
         if (target != null) {
            prefix = getPlayerRole(target, true).getPrefix();
            if (onlyColor) {
               prefix = StringUtils.getLastColor(prefix);
            }

            return prefix + name;
         } else {
            String rs = RoleCache.isPresent(name) ? RoleCache.get(name) : Database.getInstance().getRankAndName(name);
            if (rs != null) {
               prefix = getRoleByName(rs.split(" : ")[0]).getPrefix();
               if (onlyColor) {
                  prefix = StringUtils.getLastColor(prefix);
               }

               name = rs.split(" : ")[1];
               if (!removeFake && Manager.isFake(name)) {
                  name = Manager.getFake(name);
               }

               return prefix + name;
            } else {
               return prefix + name;
            }
         }
      }
   }

   public static Role getRoleByName(String name) {
      Iterator var1 = ROLES.iterator();

      Role role;
      do {
         if (!var1.hasNext()) {
            return (Role)ROLES.get(ROLES.size() - 1);
         }

         role = (Role)var1.next();
      } while(!StringUtils.stripColors(role.getName()).equalsIgnoreCase(name));

      return role;
   }

   public static Role getRoleByPermission(String permission) {
      Iterator var1 = ROLES.iterator();

      Role role;
      do {
         if (!var1.hasNext()) {
            return null;
         }

         role = (Role)var1.next();
      } while(!role.getPermission().equals(permission));

      return role;
   }

   public static Role getPlayerRole(Object player) {
      return getPlayerRole(player, false);
   }

   public static Role getPlayerRole(Object player, boolean removeFake) {
      if (!removeFake && Manager.isFake(Manager.getName(player))) {
         return Manager.getFakeRole(Manager.getName(player));
      } else {
         Iterator var2 = ROLES.iterator();

         Role role;
         do {
            if (!var2.hasNext()) {
               return getLastRole();
            }

            role = (Role)var2.next();
         } while(!role.has(player));

         return role;
      }
   }

   public static Role getLastRole() {
      return (Role)ROLES.get(ROLES.size() - 1);
   }

   public static List<Role> listRoles() {
      return ROLES;
   }

   public int getId() {
      return this.id;
   }

   public String getName() {
      return this.name;
   }

   public String getPrefix() {
      return this.prefix;
   }

   public String getPermission() {
      return this.permission;
   }

   public boolean isDefault() {
      return this.permission.isEmpty();
   }

   public boolean isAlwaysVisible() {
      return this.alwaysVisible;
   }

   public boolean isBroadcast() {
      return this.broadcast;
   }

   public boolean has(Object player) {
      return this.isDefault() || Manager.hasPermission(player, this.permission);
   }
}
