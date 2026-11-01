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
    private WeakReference<Activity> foreground = new WeakReference<>(null);
    private static Updater instance;
    private boolean checking;
    private boolean manualCheck;
    private Release available;
    private AlertDialog dialog;

    private Updater(Application app) {
        this.app = app;
        app.getSharedPreferences("camille-updater", Context.MODE_PRIVATE).edit()
            .remove("snooze").remove("nextCheck").remove("nextCheckV2").apply();
    }
    static void initialize(Application app) {
        if (instance == null && !app.getString(R.string.camille_update_app_id).isEmpty()) {
            instance = new Updater(app);
            app.registerActivityLifecycleCallbacks(instance);
        }
    }
    static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }

    @Override public void onActivityResumed(Activity activity) {
        Intent launcher = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launcher == null || !activity.getComponentName().equals(launcher.getComponent())) return;
        foreground = new WeakReference<>(activity);
        // Fetch the current catalogue on every entry, including after Later or failure.
        if (checking || dialog != null) return;
        check();
    }
    static void checkNow(Activity activity) {
        initialize(activity.getApplication());
        if (instance == null) return;
        instance.foreground = new WeakReference<>(activity);
        instance.manualCheck = true;
        android.widget.Toast.makeText(activity, R.string.camille_update_checking, android.widget.Toast.LENGTH_SHORT).show();
        if (!instance.checking) instance.check();
    }
    private void check() {
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
            } catch (Exception error) {
                android.util.Log.w("CamilleUpdater", "Update catalogue check failed; retrying on next foreground entry", error);
            }
            Release found = result;
            boolean succeeded = success;
            MAIN.post(() -> {
                checking = false;
                available = found;
                Activity activity = foreground.get();
                if (manualCheck && activity != null && !activity.isFinishing() && !activity.isDestroyed() && found == null) {
                    android.widget.Toast.makeText(activity, succeeded ? R.string.camille_update_current : R.string.camille_update_check_failed,
                        android.widget.Toast.LENGTH_LONG).show();
                }
                manualCheck = false;
                offer();
            });
        });
    }
    private void offer() {
        Activity activity = foreground.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || available == null || dialog != null) return;
        Release release = available;
        dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.camille_update_title)
            .setMessage(activity.getString(R.string.camille_update_available, release.version))
            .setPositiveButton(R.string.camille_update_download, (d, which) -> {
                Intent intent = new Intent(activity, UpdateActivity.class)
                    .putExtra("version", release.version).putExtra("code", release.code).putExtra("url", release.url)
                    .putExtra("sha256", release.sha256).putExtra("size", release.size)
                    .putExtra("locale", activity.getResources().getConfiguration().getLocales().toLanguageTags());
                activity.startActivity(intent);
            })
            .setNegativeButton(R.string.camille_update_later, (d, which) -> {}).create();
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
    }
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
