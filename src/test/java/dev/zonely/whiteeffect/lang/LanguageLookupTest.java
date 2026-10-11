package dev.zonely.whiteeffect.lang;

import dev.zonely.whiteeffect.plugin.config.WConfig;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class LanguageLookupTest {
    @Test
    public void missingMessagesAndMenuDefaultsNeverWriteTheLanguageFile() throws Exception {
        Path directory = Files.createTempDirectory("zonely-language-regression");
        Path file = directory.resolve("languages.yml");
        byte[] original = ("default: en_US\nlanguages:\n  en_US:\n"
                + "    existing: '&aConfigured'\n    legacy-menu: 'BOOK : 1 : name>Custom'\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        WConfig fixture = WConfig.getConfig(null, directory.toString(), "languages");
        Field field = LanguageManager.class.getDeclaredField("config");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, fixture);
        try {
            assertEquals("\u00a7aConfigured", LanguageManager.get("existing", "ignored"));
            for (int i = 0; i < 30; i++) {
                assertEquals("Message " + i, LanguageManager.get("help.missing" + i, "Message " + i));
            }
            assertEquals(Arrays.asList("First", "Second"),
                    LanguageManager.getList("missing-list", Arrays.asList("First", "Second")));
            assertEquals(7, LanguageManager.getInt("missing-number", 7));
            assertTrue(LanguageManager.getBoolean("missing-flag", true));
            assertEquals(Arrays.asList(1, 2),
                    LanguageManager.getIntegerList("missing-slots", Arrays.asList(1, 2)));
            LanguageManager.MenuItemDefinition item = LanguageManager.getMenuItem(
                    "en_US", "missing-menu", 13, "BOOK : 1 : name>Demo");
            assertEquals(13, item.slot());
            assertEquals("BOOK : 1 : name>Demo", item.item());
            LanguageManager.MenuItemDefinition legacy = LanguageManager.getMenuItem(
                    "en_US", "legacy-menu", 4, "ignored");
            assertEquals(4, legacy.slot());
            assertEquals("BOOK : 1 : name>Custom", legacy.item());
            assertArrayEquals("Runtime lookups must not serialize languages.yml", original, Files.readAllBytes(file));
            assertTrue(fixture.save());
            assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("Message 29"));
        } finally {
            field.set(null, previous);
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
