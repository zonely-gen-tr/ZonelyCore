package dev.zonely.whiteeffect.store;

import java.util.List;
import org.bukkit.ChatColor;
import org.junit.Test;
import static org.junit.Assert.*;

public class StoreTextTest {
    @Test public void webEntitiesAndParagraphsBecomeReadableLore() {
        String text="<p>&Ouml;rneğin: Oyun i&ccedil;erisinde verilecek spawnerleri kategori olarak g&ouml;sterebilirsiniz.</p><p>Y&ouml;netim panelinden &#351;ekillendir. &#x131;</p>";
        assertTrue(StoreText.plain(text).startsWith("Örneğin: Oyun içerisinde"));
        assertTrue(StoreText.plain(text).contains("şekillendir. ı"));
        List<String> lore=StoreText.lore(text,"§7",42);
        assertTrue(lore.size()>2);
        for(String line:lore)assertTrue(ChatColor.stripColor(line).length()<=42);
    }
    @Test public void longWordsAndColorsStayBounded() {
        List<String> lore=StoreText.lore("&a"+String.join("",java.util.Collections.nCopies(130,"x")),"§7",42);
        assertEquals(4,lore.size());
        for(String line:lore) { assertTrue(ChatColor.stripColor(line).length()<=42);assertTrue(line.contains("§a")); }
        assertTrue(ChatColor.stripColor(StoreText.title(String.join("",java.util.Collections.nCopies(200,"ü")))) .length()<=64);
    }
}
