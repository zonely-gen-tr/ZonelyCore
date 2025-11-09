package dev.zonely.whiteeffect.libraries.menu.text;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuTextApplier {

    private static final Method DISPLAY_NAME_COMPONENT = resolveDisplayNameComponent();
    private static final Method LORE_COMPONENT = resolveLoreComponent();

    private MenuTextApplier() {
    }

    public static void applyDisplayName(ItemMeta meta, MenuText text) {
        if (meta == null || text == null) {
            return;
        }

        Component component = text.component();
        if (component != null && DISPLAY_NAME_COMPONENT != null) {
            invoke(meta, DISPLAY_NAME_COMPONENT, component);
            return;
        }

        meta.setDisplayName(text.legacy());
    }

    public static void applyLore(ItemMeta meta, List<MenuText> lines) {
        if (meta == null) {
            return;
        }

        if (lines == null || lines.isEmpty()) {
            if (LORE_COMPONENT != null) {
                invoke(meta, LORE_COMPONENT, Collections.emptyList());
            } else {
                meta.setLore(Collections.emptyList());
            }
            return;
        }

        if (LORE_COMPONENT != null) {
            List<Component> components = map(lines, MenuText::component);
            invoke(meta, LORE_COMPONENT, components);
            return;
        }

        List<String> legacy = map(lines, MenuText::legacy);
        meta.setLore(legacy);
    }

    private static <T> List<T> map(List<MenuText> source, Function<MenuText, T> mapper) {
        List<T> target = new ArrayList<>(source.size());
        for (MenuText text : source) {
            target.add(mapper.apply(text));
        }
        return target;
    }

    private static void invoke(ItemMeta meta, Method method, Object argument) {
        try {
            method.invoke(meta, argument);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private static Method resolveDisplayNameComponent() {
        for (String name : new String[]{"displayName", "setDisplayNameComponent"}) {
            Method method = findDeclared(ItemMeta.class, name, Component.class);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static Method resolveLoreComponent() {
        for (String name : new String[]{"lore", "setLoreComponents"}) {
            Method method = findDeclared(ItemMeta.class, name, List.class);
            if (method != null && hasComponentGenerics(method)) {
                return method;
            }
        }
        return null;
    }

    private static Method findDeclared(Class<?> type, String name, Class<?> parameterType) {
        try {
            Method method = type.getMethod(name, parameterType);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean hasComponentGenerics(Method method) {
        Type[] types = method.getGenericParameterTypes();
        if (types.length != 1) {
            return false;
        }
        Type first = types[0];
        if (!(first instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterized = (ParameterizedType) first;
        Type[] arguments = parameterized.getActualTypeArguments();
        if (arguments.length != 1) {
            return false;
        }
        Type argument = arguments[0];
        if (!(argument instanceof Class)) {
            return false;
        }
        Class<?> clazz = (Class<?>) argument;
        return Component.class.isAssignableFrom(clazz);
    }
}
