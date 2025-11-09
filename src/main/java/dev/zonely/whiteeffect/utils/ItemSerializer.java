package dev.zonely.whiteeffect.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.*;
import java.util.Base64;

public class ItemSerializer {
    public static String serialize(ItemStack item) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos);
        boos.writeObject(item);
        boos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public static ItemStack deserialize(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream bois = new BukkitObjectInputStream(bais);
        ItemStack item = (ItemStack) bois.readObject();
        bois.close();
        return item;
    }
}