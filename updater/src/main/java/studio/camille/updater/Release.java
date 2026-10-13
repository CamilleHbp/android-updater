package studio.camille.updater;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.List;

/** Strict adapter for the version 1 Camille Studio download catalogue. */
final class Release {
    static final String ORIGIN = "https://download.camille.studio";
    final String version, url, sha256;
    final long code, size;

    Release(String version, long code, String url, String sha256, long size) {
        this.version = version; this.code = code; this.url = url; this.sha256 = sha256; this.size = size;
    }

    static Release select(JSONObject app, String appId, String edition, String channel,
                          List<String> abis, long installed) throws Exception {
        if (!appId.equals(app.getString("id"))) throw new IllegalArgumentException("Wrong app");
        JSONObject platforms = app.optJSONObject("latestByPlatform");
        String latest = platforms == null ? app.getString("latest") : platforms.optString("android", "");
        JSONArray releases = app.getJSONArray("releases");
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (!latest.equals(release.getString("id"))) continue;
            String version = release.getString("version");
            if (!latest.matches(java.util.regex.Pattern.quote(version) + "-[0-9]+")) return null;
            long code = Long.parseLong(latest.substring(version.length() + 1));
            if (code <= installed) return null;
            String prefix = "android-" + (edition.isEmpty() ? "" : edition + "-");
            List<String> names = edition.isEmpty()
                ? java.util.Arrays.asList(prefix + channel + ".apk")
                : abis.contains("arm64-v8a")
                    ? java.util.Arrays.asList(prefix + "arm64-" + channel + ".apk", prefix + "universal-" + channel + ".apk")
                    : java.util.Arrays.asList(prefix + "universal-" + channel + ".apk");
            JSONArray assets = release.getJSONArray("assets");
            for (String name : names) for (int k = 0; k < assets.length(); k++) {
                JSONObject asset = assets.getJSONObject(k);
                if (!name.equals(asset.getString("name")) || !"android".equals(asset.getString("platform"))
                        || !channel.equals(asset.getString("channel"))) continue;
                String path = "/apps/" + appId + "/" + latest + "/" + name;
                String url = asset.getString("url");
                if (!url.equals(path) && !url.equals(ORIGIN + path)) throw new IllegalArgumentException("Untrusted URL");
                String hash = asset.getString("sha256");
                long size = asset.getLong("size");
                if (!hash.matches("[a-fA-F0-9]{64}") || size <= 0 || size > 1024L * 1024 * 1024)
                    throw new IllegalArgumentException("Invalid integrity metadata");
                return new Release(version, code, ORIGIN + path, hash, size);
            }
            return null;
        }
        return null;
    }

    static void requireTrustedUrl(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || !"download.camille.studio".equals(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Untrusted endpoint");
    }
}
