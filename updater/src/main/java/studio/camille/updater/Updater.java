package studio.camille.updater;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Updater implements Application.ActivityLifecycleCallbacks {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final Application app;
    private final SharedPreferences preferences;
    private WeakReference<Activity> foreground = new WeakReference<>(null);
    private boolean checking;
    private Release available;
    private AlertDialog dialog;

    private Updater(Application app) {
        this.app = app;
        preferences = app.getSharedPreferences("camille-updater", Context.MODE_PRIVATE);
    }
    static void initialize(Application app) {
        if (!app.getString(R.string.camille_update_app_id).isEmpty()) app.registerActivityLifecycleCallbacks(new Updater(app));
    }
    static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }

    @Override public void onActivityResumed(Activity activity) {
        Intent launcher = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launcher == null || !activity.getComponentName().equals(launcher.getComponent())) return;
        foreground = new WeakReference<>(activity);
        if (System.currentTimeMillis() < preferences.getLong("snooze", 0)) return;
        if (available != null) { offer(); return; }
        if (checking || System.currentTimeMillis() < preferences.getLong("nextCheck", 0)) return;
        checking = true;
        IO.execute(() -> {
            Release result = null; boolean success = false;
            try {
                String id = app.getString(R.string.camille_update_app_id);
                JSONObject document = new JSONObject(Transfer.catalogue(id));
                JSONObject record = document;
                PackageInfo installed = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                String channel = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 ? "debug" : "release";
                result = Release.select(record, id, app.getString(R.string.camille_update_edition), channel,
                        Arrays.asList(Build.SUPPORTED_ABIS), code(installed));
                success = true;
            } catch (Exception ignored) { /* An offline check never interrupts normal app use. */ }
            Release found = result;
            long delay = success ? 6 * 60 * 60 * 1000L : 15 * 60 * 1000L;
            preferences.edit().putLong("nextCheck", System.currentTimeMillis() + delay).apply();
            MAIN.post(() -> { checking = false; available = found; offer(); });
        });
    }
    private void offer() {
        Activity activity = foreground.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || available == null || dialog != null
                || System.currentTimeMillis() < preferences.getLong("snooze", 0)) return;
        Release release = available;
        dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.camille_update_title)
            .setMessage(activity.getString(R.string.camille_update_available, release.version))
            .setPositiveButton(R.string.camille_update_download, (d, which) -> {
                snooze();
                Intent intent = new Intent(activity, UpdateActivity.class)
                    .putExtra("version", release.version).putExtra("code", release.code).putExtra("url", release.url)
                    .putExtra("sha256", release.sha256).putExtra("size", release.size)
                    .putExtra("locale", activity.getResources().getConfiguration().getLocales().toLanguageTags());
                activity.startActivity(intent);
            })
            .setNegativeButton(R.string.camille_update_later, (d, which) -> snooze())
            .setOnCancelListener(d -> snooze()).create();
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
    }
    private void snooze() { preferences.edit().putLong("snooze", System.currentTimeMillis() + 24 * 60 * 60 * 1000L).apply(); }
    @Override public void onActivityPaused(Activity activity) {
        if (foreground.get() == activity) {
            foreground.clear();
            if (dialog != null) { dialog.dismiss(); dialog = null; }
        }
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
