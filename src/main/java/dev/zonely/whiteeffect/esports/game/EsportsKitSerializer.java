package dev.zonely.whiteeffect.esports.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;

public final class EsportsKitSerializer {

    private static final Method GET_OFFHAND_METHOD;
    private static final Method SET_OFFHAND_METHOD;

    static {
        Method getter = null;
        Method setter = null;
        try {
            getter = PlayerInventory.class.getMethod("getItemInOffHand");
        } catch (NoSuchMethodException ignored) {
        }
        try {
            setter = PlayerInventory.class.getMethod("setItemInOffHand", ItemStack.class);
        } catch (NoSuchMethodException ignored) {
        }
        GET_OFFHAND_METHOD = getter;
        SET_OFFHAND_METHOD = setter;
    }

    private EsportsKitSerializer() {
    }

    public static JsonObject capture(Player player) {
        PlayerInventory inv = player.getInventory();
        JsonObject root = new JsonObject();
        root.add("contents", new JsonPrimitive(encode(inv.getContents())));
        root.add("armor", new JsonPrimitive(encode(inv.getArmorContents())));
        root.add("extra", new JsonPrimitive(encode(new ItemStack[]{getOffhand(inv)})));
        return root;
    }

    public static void apply(JsonObject kit, Player player) {
        if (kit == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (kit.has("contents")) {
            inv.setContents(decode(kit.get("contents").getAsString(), inv.getSize()));
        }
        if (kit.has("armor")) {
            inv.setArmorContents(decode(kit.get("armor").getAsString(), inv.getArmorContents().length));
        }
        if (kit.has("extra")) {
            ItemStack[] extra = decode(kit.get("extra").getAsString(), 1);
            if (extra.length > 0) {
                setOffhand(inv, extra[0]);
            }
        }
    }

    public static ItemStack[] readContents(JsonObject kit, int expectedSize) {
        int size = Math.max(0, expectedSize);
        if (kit == null || !kit.has("contents") || kit.get("contents").isJsonNull()) {
            return new ItemStack[size];
        }
        return decode(kit.get("contents").getAsString(), size);
    }

    public static ItemStack[] readArmor(JsonObject kit) {
        if (kit == null || !kit.has("armor") || kit.get("armor").isJsonNull()) {
            return new ItemStack[4];
        }
        ItemStack[] decoded = decode(kit.get("armor").getAsString(), 4);
        return decoded.length == 4 ? decoded : Arrays.copyOf(decoded, 4);
    }

    public static ItemStack readOffhand(JsonObject kit) {
        if (kit == null || !kit.has("extra") || kit.get("extra").isJsonNull()) {
            return null;
        }
        ItemStack[] decoded = decode(kit.get("extra").getAsString(), 1);
        return decoded.length > 0 ? decoded[0] : null;
    }

    public static JsonObject serialize(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        JsonObject root = new JsonObject();
        root.add("contents", new JsonPrimitive(encode(contents != null ? contents : new ItemStack[0])));
        root.add("armor", new JsonPrimitive(encode(normalizeArmor(armor))));
        root.add("extra", new JsonPrimitive(encode(new ItemStack[]{offhand})));
        return root;
    }

    private static ItemStack[] normalizeArmor(ItemStack[] armor) {
        if (armor == null) {
            return new ItemStack[4];
        }
        return armor.length == 4 ? armor : Arrays.copyOf(armor, 4);
    }

    private static String encode(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(outputStream)) {
            data.writeInt(items.length);
            for (ItemStack item : items) {
                data.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to serialize kit", ex);
        }
    }

    private static ItemStack[] decode(String data, int expected) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[expected];
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream objectInputStream = new BukkitObjectInputStream(inputStream)) {
            int length = objectInputStream.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) objectInputStream.readObject();
            }
            return items;
        } catch (ClassNotFoundException | IOException ex) {
            throw new IllegalStateException("Unable to deserialize kit", ex);
        }
    }

    private static ItemStack getOffhand(PlayerInventory inventory) {
        if (GET_OFFHAND_METHOD == null) {
            return null;
        }
        try {
            return (ItemStack) GET_OFFHAND_METHOD.invoke(inventory);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static void setOffhand(PlayerInventory inventory, ItemStack item) {
        if (SET_OFFHAND_METHOD == null) {
            return;
        }
        try {
            SET_OFFHAND_METHOD.invoke(inventory, item);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }
}
