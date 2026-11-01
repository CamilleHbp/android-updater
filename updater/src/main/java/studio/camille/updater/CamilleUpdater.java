package studio.camille.updater;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

/** Explicit checks show checking, up-to-date, or failure feedback. */
public final class CamilleUpdater {
    private CamilleUpdater() {}
    public static void checkNow(Activity activity) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (android.os.Build.VERSION.SDK_INT < 26) {
                String id = activity.getString(R.string.camille_update_app_id);
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://download.camille.studio/?q=" + android.net.Uri.encode(id)));
                try { activity.startActivity(intent); }
                catch (android.content.ActivityNotFoundException error) {
                    android.widget.Toast.makeText(activity, R.string.camille_update_check_failed, android.widget.Toast.LENGTH_LONG).show();
                }
                return;
            }
            Updater.checkNow(activity);
        });
    }
}
