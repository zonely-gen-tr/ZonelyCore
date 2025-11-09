package dev.zonely.whiteeffect.libraries.menu.text;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MenuText {

    private static final Pattern FONT_COLON_PREFIX = Pattern.compile("^font:([^|]+)\\|", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_BRACE_PREFIX = Pattern.compile("^\\{font=([^}]+)}", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_TAG_PREFIX = Pattern.compile("^<font:([^>]+)>", Pattern.CASE_INSENSITIVE);
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final MenuText EMPTY = new MenuText(Component.empty(), "");

    private final Component component;
    private final String legacy;

    private MenuText(Component component, String legacy) {
        this.component = component;
        this.legacy = legacy;
    }

    public static MenuText empty() {
        return EMPTY;
    }

    public static MenuText parse(String input) {
        if (input == null || input.isEmpty()) {
            return EMPTY;
        }

        Extraction extraction = extractFontPrefix(input);
        String content = extraction.text();
        Component component;
        if (content.indexOf('\u00a7') >= 0 && content.indexOf('&') == -1) {
            component = LEGACY_SECTION.deserialize(content);
        } else {
            component = LEGACY_AMPERSAND.deserialize(content);
        }
        if (extraction.font() != null) {
            component = applyFont(component, extraction.font());
        }

        String legacy = LEGACY_SECTION.serialize(component);
        return new MenuText(component, legacy);
    }

    public Component component() {
        return component;
    }

    public String legacy() {
        return legacy;
    }

    private static Component applyFont(Component component, Key font) {
        return component.applyFallbackStyle(style -> style.font(font));
    }

    private static Extraction extractFontPrefix(String raw) {
        String working = raw;
        Key font = null;

        Matcher colonMatcher = FONT_COLON_PREFIX.matcher(working);
        if (colonMatcher.find()) {
            font = toKey(colonMatcher.group(1));
            working = working.substring(colonMatcher.end());
            return new Extraction(font, working);
        }

        Matcher braceMatcher = FONT_BRACE_PREFIX.matcher(working);
        if (braceMatcher.find()) {
            font = toKey(braceMatcher.group(1));
            working = working.substring(braceMatcher.end());
            return new Extraction(font, working);
        }

        Matcher tagMatcher = FONT_TAG_PREFIX.matcher(working);
        if (tagMatcher.find()) {
            font = toKey(tagMatcher.group(1));
            working = working.substring(tagMatcher.end());
            return new Extraction(font, working);
        }

        return new Extraction(null, working);
    }

    private static Key toKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            if (trimmed.indexOf(':') == -1) {
                return Key.key("minecraft", normalizeKey(trimmed));
            }
            String[] split = trimmed.split(":", 2);
            return Key.key(normalizeNamespace(split[0]), normalizeKey(split[1]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeNamespace(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    private static String normalizeKey(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    private static final class Extraction {
        private final Key font;
        private final String text;

        private Extraction(Key font, String text) {
            this.font = font;
            this.text = Objects.requireNonNullElse(text, "");
        }

        private Key font() {
            return font;
        }

        private String text() {
            return text;
        }
    }
}
