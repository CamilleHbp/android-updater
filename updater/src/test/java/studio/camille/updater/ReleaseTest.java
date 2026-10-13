package studio.camille.updater;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReleaseTest {
    private JSONObject app(String id, String version, int code, String... names) throws Exception {
        String rid = version + "-" + code;
        JSONArray assets = new JSONArray();
        for (String name : names) assets.put(new JSONObject().put("name", name).put("platform", "android")
            .put("channel", name.endsWith("-debug.apk") ? "debug" : "release")
            .put("size", 100).put("sha256", String.join("", Collections.nCopies(64, "a")))
            .put("url", "/apps/" + id + "/" + rid + "/" + name));
        return new JSONObject().put("id", id).put("latest", rid)
            .put("releases", new JSONArray().put(new JSONObject().put("id", rid).put("version", version).put("assets", assets)));
    }
    private Release select(JSONObject app, String edition, String channel, long code) throws Exception {
        return Release.select(app, app.getString("id"), edition, channel, Arrays.asList("arm64-v8a"), code);
    }
    @Test public void sameVersionNewBuildIsUpdate() throws Exception {
        assertEquals(1273, select(app("vervetwodo", "1.0", 1273, "android-release.apk"), "", "release", 1272).code);
    }
    @Test public void neverDowngradesOrReinstalls() throws Exception {
        JSONObject app = app("groceries", "2.0.0", 33, "android-release.apk");
        assertNull(select(app, "", "release", 33)); assertNull(select(app, "", "release", 34));
    }
    @Test public void followsAndroidPromotionInsteadOfDesktopLatest() throws Exception {
        JSONObject app = app("groceries", "2.0.0", 33, "android-release.apk")
            .put("latest", "9.0-desktop-20261013").put("latestByPlatform", new JSONObject().put("android", "2.0.0-33"));
        assertEquals(33, select(app, "", "release", 32).code);
    }
    @Test public void preservesChannelEditionAndAbi() throws Exception {
        JSONObject app = app("podcini", "12.12.1", 126, "android-free-arm64-release.apk", "android-play-arm64-debug.apk", "android-play-universal-debug.apk");
        assertTrue(select(app, "play", "debug", 125).url.endsWith("android-play-arm64-debug.apk"));
        assertTrue(Release.select(app, "podcini", "play", "debug", Arrays.asList("x86_64"), 125).url.endsWith("universal-debug.apk"));
        assertNull(select(app, "play", "release", 125));
    }
    @Test public void doesNotFallBackToHistoricalOrWrongChannelRelease() throws Exception {
        assertNull(select(app("groceries", "2.0.1", 34, "android-release.apk"), "", "debug", 33));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsExternalDownload() throws Exception {
        JSONObject app = app("groceries", "2.0.1", 34, "android-release.apk");
        app.getJSONArray("releases").getJSONObject(0).getJSONArray("assets").getJSONObject(0).put("url", "https://evil.test/update.apk");
        select(app, "", "release", 33);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsWrongApp() throws Exception {
        Release.select(app("podcini", "1.0", 5, "android-release.apk"), "groceries", "", "release", Collections.emptyList(), 1);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidHash() throws Exception {
        JSONObject app = app("groceries", "2.0.1", 34, "android-release.apk");
        app.getJSONArray("releases").getJSONObject(0).getJSONArray("assets").getJSONObject(0).put("sha256", "bad");
        select(app, "", "release", 33);
    }
    @Test public void rejectsUntrustedConnections() {
        for (String url : Arrays.asList("http://download.camille.studio/a", "https://evil.test/a", "https://download.camille.studio@evil.test/a", "https://download.camille.studio:444/a")) {
            try { Release.requireTrustedUrl(url); fail(url); } catch (IllegalArgumentException expected) {}
        }
    }
}
