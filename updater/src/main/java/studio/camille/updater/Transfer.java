package studio.camille.updater;

import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

final class Transfer {
    interface Progress { void update(int percent); }

    static HttpsURLConnection open(String url) throws IOException {
        Release.requireTrustedUrl(url);
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Unexpected HTTP status");
            return connection;
        } catch (IOException e) { connection.disconnect(); throw e; }
    }

    static String catalogue(String appId) throws Exception {
        HttpsURLConnection connection = open(Release.ORIGIN + "/api/v1/" + appId + ".json");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output, 1024 * 1024, -1, null);
            return output.toString("UTF-8");
        } finally { connection.disconnect(); }
    }

    static void download(Release release, File target, Progress progress) throws Exception {
        HttpsURLConnection connection = open(release.url);
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(target)) {
            copy(input, output, release.size, release.size, progress);
        } finally { connection.disconnect(); }
        verify(target, release.size, release.sha256);
    }

    static void copy(InputStream input, OutputStream output, long limit, long expected, Progress progress) throws IOException {
        byte[] buffer = new byte[32768];
        long total = 0, deadline = System.nanoTime() + 600_000_000_000L;
        int count, previous = -1;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit || System.nanoTime() > deadline) throw new IOException("Transfer limit exceeded");
            output.write(buffer, 0, count);
            int percent = expected > 0 ? (int) (total * 100 / expected) : 0;
            if (progress != null && percent != previous) { progress.update(percent); previous = percent; }
        }
        if (expected >= 0 && total != expected) throw new IOException("Truncated download");
    }

    static void verify(File file, long size, String expectedHash) throws Exception {
        if (file.length() != size) throw new IOException("Wrong size");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!hash.toString().equalsIgnoreCase(expectedHash)) throw new IOException("Checksum mismatch");
    }
}
