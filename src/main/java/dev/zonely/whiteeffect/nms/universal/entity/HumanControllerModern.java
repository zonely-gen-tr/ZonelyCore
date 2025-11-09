package dev.zonely.whiteeffect.nms.universal.entity;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.AbstractEntityController;
import dev.zonely.whiteeffect.reflection.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;


public class HumanControllerModern extends AbstractEntityController {
    private Object citizensNPC;

    @Override
    protected Entity createEntity(Location location, NPC npc) {
        Plugin citizens = Bukkit.getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) {
            Core.getInstance().getLogger().warning("Citizens was not found or is disabled. Modern NPC support is turned off.");
            return null;
        }
        try {
            Class<?> citizensAPI = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = Accessors.getMethod(citizensAPI, "getNPCRegistry").invoke(null);

            Object created;
            try {
                created = Accessors.getMethod(registry.getClass(), "createNPC", EntityType.class, java.util.UUID.class, String.class)
                        .invoke(registry, EntityType.PLAYER, npc.getUUID(), npc.getName());
            } catch (Throwable ignore) {
                created = Accessors.getMethod(registry.getClass(), "createNPC", EntityType.class, String.class)
                        .invoke(registry, EntityType.PLAYER, npc.getName());
            }

            this.citizensNPC = created;
            Accessors.getMethod(created.getClass(), "spawn", Location.class).invoke(created, location);
            Entity entity = (Entity) Accessors.getMethod(created.getClass(), "getEntity").invoke(created);

            if (entity != null) {
                entity.setMetadata("NPC", new FixedMetadataValue(Core.getInstance(), true));
                if (entity instanceof Player) {
                    ((Player) entity).setSleepingIgnored(true);
                }
            }
            return entity;
        } catch (Throwable t) {
            Core.getInstance().getLogger().severe("Failed to create Citizens NPC: " + t.getMessage());
            return null;
        }
    }

    @Override
    public void remove() {
        if (this.citizensNPC != null) {
            try {
                try {
                    Accessors.getMethod(this.citizensNPC.getClass(), "destroy").invoke(this.citizensNPC);
                } catch (Throwable ignore) {
                    Accessors.getMethod(this.citizensNPC.getClass(), "despawn").invoke(this.citizensNPC);
                }
            } catch (Throwable t) {
                Core.getInstance().getLogger().warning("Unable to remove Citizens NPC: " + t.getMessage());
            }
            this.citizensNPC = null;
        }
        super.remove();
    }
}
