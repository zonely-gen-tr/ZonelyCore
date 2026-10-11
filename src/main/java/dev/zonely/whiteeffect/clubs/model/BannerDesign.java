package dev.zonely.whiteeffect.clubs.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BannerDesign {

    public static final int MAX_LAYERS = 6;

    private String baseColor;
    private final List<Layer> layers;
    private static final Method SET_BASE_COLOR_METHOD;

    static {
        Method method = null;
        try {
            method = BannerMeta.class.getMethod("setBaseColor", DyeColor.class);
        } catch (NoSuchMethodException ignored) {
        }
        SET_BASE_COLOR_METHOD = method;
    }

    public BannerDesign() {
        this("WHITE", new ArrayList<>());
    }

    public BannerDesign(String baseColor, List<Layer> layers) {
        this.baseColor = baseColor == null ? "WHITE" : baseColor.toUpperCase();
        this.layers = layers == null ? new ArrayList<>() : new ArrayList<>(layers);
    }

    public static BannerDesign fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new BannerDesign();
        }
        try {
            JsonObject object = new JsonParser().parse(json).getAsJsonObject();
            String base = object.has("base") ? object.get("base").getAsString() : "WHITE";
            List<Layer> layers = new ArrayList<>();
            if (object.has("patterns") && object.get("patterns").isJsonArray()) {
                JsonArray arr = object.getAsJsonArray("patterns");
                for (JsonElement element : arr) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject entry = element.getAsJsonObject();
                    String pattern = entry.has("pattern") ? entry.get("pattern").getAsString() : null;
                    String color = entry.has("color") ? entry.get("color").getAsString() : "WHITE";
                    if (pattern != null) {
                        layers.add(new Layer(pattern, color));
                        if (layers.size() >= MAX_LAYERS) {
                            break;
                        }
                    }
                }
            }
            return new BannerDesign(base, layers);
        } catch (Exception ignored) {
            return new BannerDesign();
        }
    }

    public String getBaseColor() {
        return baseColor;
    }

    public void setBaseColor(String baseColor) {
        if (baseColor != null && !baseColor.trim().isEmpty()) {
            this.baseColor = baseColor.toUpperCase();
        }
    }

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public boolean canAddLayer() {
        return layers.size() < MAX_LAYERS;
    }

    public void addLayer(Layer layer) {
        if (layer == null || layers.size() >= MAX_LAYERS) {
            return;
        }
        layers.add(layer);
    }

    public void removeLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            layers.remove(index);
        }
    }

    public void clearLayers() {
        layers.clear();
    }

    public BannerDesign copy() {
        List<Layer> copy = new ArrayList<>();
        for (Layer layer : layers) {
            copy.add(new Layer(layer.getPattern(), layer.getColor()));
        }
        return new BannerDesign(baseColor, copy);
    }

    public JsonObject toJsonObject() {
        JsonObject root = new JsonObject();
        root.addProperty("base", baseColor);
        JsonArray array = new JsonArray();
        for (Layer layer : layers) {
            JsonObject entry = new JsonObject();
            entry.addProperty("pattern", layer.getPattern());
            entry.addProperty("color", layer.getColor());
            array.add(entry);
        }
        root.add("patterns", array);
        return root;
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    public ItemStack createPreviewItem() {
        Material material = resolveBannerMaterial(baseColor);
        ItemStack banner = new ItemStack(material);
        if (banner.getItemMeta() instanceof BannerMeta) {
            BannerMeta meta = (BannerMeta) banner.getItemMeta();
            applyBaseColor(meta, resolveColor(baseColor));
            for (Layer layer : layers) {
                PatternType type = parsePattern(layer.getPattern());
                if (type == null) {
                    continue;
                }
                meta.addPattern(new Pattern(resolveColor(layer.getColor()), type));
            }
            banner.setItemMeta(meta);
        }
        return banner;
    }

    public static void applyBaseColor(BannerMeta meta, DyeColor color) {
        if (SET_BASE_COLOR_METHOD != null) {
            try {
                SET_BASE_COLOR_METHOD.invoke(meta, color);
                return;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        meta.addPattern(new Pattern(color, PatternType.BASE));
    }

    private Material resolveBannerMaterial(String baseColor) {
        Material material = MaterialResolver.resolveMaterial(baseColor + "_BANNER");
        if (material == null) {
            material = MaterialResolver.resolveMaterial("WHITE_BANNER");
        }
        if (material == null) {
            material = MaterialResolver.resolveMaterial("BANNER");
        }
        if (material == null) {
            material = MaterialResolver.resolveMaterial("STANDING_BANNER");
        }
        if (material == null) {
            material = MaterialResolver.resolveMaterial("WALL_BANNER");
        }
        if (material == null) {
            material = Material.PAPER;
        }
        return material;
    }

    public static DyeColor resolveColor(String name) {
        if (name == null || name.trim().isEmpty()) {
            return DyeColor.WHITE;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if ("SILVER".equals(normalized) || "LIGHTGREY".equals(normalized) || "LIGHT-GREY".equals(normalized)) {
            normalized = "LIGHT_GRAY";
        }
        try {
            return DyeColor.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            if ("LIGHT_GRAY".equals(normalized)) {
                try {
                    return DyeColor.valueOf("SILVER");
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DyeColor.WHITE;
        }
    }

    private PatternType parsePattern(String name) {
        if (name == null) {
            return null;
        }
        try {
            return PatternType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static final class Layer {
        private final String pattern;
        private final String color;

        public Layer(String pattern, String color) {
            this.pattern = pattern == null ? "BASE" : pattern.toUpperCase();
            this.color = color == null ? "WHITE" : color.toUpperCase();
        }

        public String getPattern() {
            return pattern;
        }

        public String getColor() {
            return color;
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("pattern", pattern);
            object.addProperty("color", color);
            return object;
        }
    }
}
