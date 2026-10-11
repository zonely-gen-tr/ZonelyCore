package dev.zonely.whiteeffect.store;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StoreVisibilityTest {
    @Test public void enabledPanelRepresentationsAreAccepted() throws Exception {
        for (Object value : new Object[]{1, "1", true, "true", "on", " TRUE "})
            assertTrue(StoreVisibility.isVisible(new JSONObject().put("showInGame", value)));
    }

    @Test public void disabledOrMissingSettingsStayHidden() throws Exception {
        assertFalse(StoreVisibility.isVisible(null));
        assertFalse(StoreVisibility.isVisible(new JSONObject()));
        for (Object value : new Object[]{0, "0", false, "false", "off", "", JSONObject.NULL})
            assertFalse(StoreVisibility.isVisible(new JSONObject().put("showInGame", value)));
    }

    @Test public void UnknownFlagsNeverExposeProducts() throws Exception {
        for (Object value : new Object[]{2, -1, "enabled", new JSONObject()})
            assertFalse(StoreVisibility.isVisible(new JSONObject().put("showInGame", value)));
    }
}
