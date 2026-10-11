package dev.zonely.whiteeffect.hologram;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class HologramTemplateTest {
    @Test
    public void splitNameAndAmountTemplatesRequestAllRequiredLeaderboardEntries() {
        assertEquals(12, LastCreditHologramManager.detectEntrySlots(Arrays.asList(
                "{entry_1}", "{entry_10_name}", "{entry_11_amount}", "{entry_12_amount_raw}")));
        assertEquals(7, LastCreditHologramManager.detectEntrySlots(Collections.singletonList("{entry_7}")));
        assertEquals(3, LastCreditHologramManager.detectEntrySlots(Collections.singletonList("{entry_invalid}")));
    }
}
