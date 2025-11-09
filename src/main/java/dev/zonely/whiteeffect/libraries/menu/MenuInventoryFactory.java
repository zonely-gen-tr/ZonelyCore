package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.libraries.menu.text.MenuText;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuInventoryFactory {

   private static final Method CREATE_INVENTORY_COMPONENT = resolveInventoryComponentFactory();

   private MenuInventoryFactory() {
   }

   public static Inventory create(InventoryHolder holder, int size, String title) {
      return create(holder, size, MenuText.parse(title));
   }

   public static Inventory create(InventoryHolder holder, int size, MenuText title) {
      if (CREATE_INVENTORY_COMPONENT != null) {
         try {
            return (Inventory)CREATE_INVENTORY_COMPONENT.invoke((Object)null, new Object[]{holder, size, title.component()});
         } catch (IllegalAccessException | InvocationTargetException ignored) {
         }
      }

      return Bukkit.createInventory(holder, size, title.legacy());
   }

   private static Method resolveInventoryComponentFactory() {
      for(Method method : Bukkit.class.getMethods()) {
         if (method.getName().equals("createInventory")
                 && Modifier.isStatic(method.getModifiers())) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3
                    && InventoryHolder.class.isAssignableFrom(params[0])
                    && params[1] == Integer.TYPE
                    && Component.class.isAssignableFrom(params[2])) {
               method.setAccessible(true);
               return method;
            }
         }
      }
      return null;
   }
}
