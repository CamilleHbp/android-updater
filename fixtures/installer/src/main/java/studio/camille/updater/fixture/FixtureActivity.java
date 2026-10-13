package studio.camille.updater.fixture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import studio.camille.updater.UpdateActivity;
import java.io.*;
import java.security.MessageDigest;

/** Isolated package for verifying real installer approval and data preservation on emulators. */
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences prefs = getSharedPreferences("fixture", 0);
        if (!prefs.contains("sentinel")) prefs.edit().putString("sentinel", "preserve-me").commit();
        TextView view = new TextView(this);
        try {
            view.setText("Installed " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName
                + "; data: " + prefs.getString("sentinel", "missing"));
            view.setTextSize(22); view.setPadding(30, 120, 30, 30); setContentView(view);
            if (getIntent().getBooleanExtra("update", false)) {
                File apk = new File(getCacheDir(), "camille-updater/update.apk");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = new FileInputStream(apk)) { byte[] buffer = new byte[32768]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
                StringBuilder hash = new StringBuilder(); for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                startActivity(new Intent(this, UpdateActivity.class).putExtra("version", "2.0").putExtra("code", 2L)
                    .putExtra("size", apk.length()).putExtra("sha256", hash.toString())
                    .putExtra("url", "https://download.camille.studio/apps/fixture/2.0-2/android-debug.apk")
                    .putExtra("locale", getIntent().getStringExtra("locale")));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
