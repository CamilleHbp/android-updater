package studio.camille.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;

/** One process-wide transfer survives activity recreation without retaining an activity. */
final class Download {
    static Download current;
    final Release release;
    volatile int percent;
    volatile String state = "downloading";
    private WeakReference<UpdateActivity> observer = new WeakReference<>(null);

    private Download(Release release) { this.release = release; }
    static synchronized Download obtain(Context context, Release release) {
        if (current != null && current.release.sha256.equals(release.sha256) && !"failed".equals(current.state)) return current;
        Download job = new Download(release);
        current = job;
        Context app = context.getApplicationContext();
        Updater.IO.execute(() -> job.run(app));
        return job;
    }
    void observe(UpdateActivity activity) { observer = new WeakReference<>(activity); }
    void detach(UpdateActivity activity) { if (observer.get() == activity) observer.clear(); }
    private void changed() {
        Updater.MAIN.post(() -> { UpdateActivity activity = observer.get(); if (activity != null) activity.render(); });
    }
    private void run(Context context) {
        File dir = new File(context.getCacheDir(), "camille-updater");
        File part = new File(dir, "update.part");
        File apk = new File(dir, "update.apk");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create cache");
            boolean cached = false;
            if (apk.isFile()) {
                try { Transfer.verify(apk, release.size, release.sha256); cached = true; }
                catch (Exception ignored) { if (!apk.delete()) throw new IOException("Cannot clear cache"); }
            }
            if (!cached) Transfer.download(release, part, value -> { percent = value; changed(); });
            state = "verifying"; changed();
            validate(context, cached ? apk : part, release);
            if (!cached && !part.renameTo(apk)) throw new IOException("Cannot save update");
            state = "ready";
        } catch (Exception ignored) { part.delete(); apk.delete(); state = "failed"; }
        changed();
    }
    @SuppressWarnings("deprecation")
    static void validate(Context context, File apk, Release release) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)
                || Updater.code(candidate) != release.code || Updater.code(candidate) <= Updater.code(installed)
                || !release.version.equals(candidate.versionName) || candidate.applicationInfo == null
                || candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT)
            throw new IOException("Incompatible update");
        Signature[] incoming = Build.VERSION.SDK_INT >= 28 ? candidate.signingInfo.getApkContentsSigners() : candidate.signatures;
        Signature[] existing = Build.VERSION.SDK_INT >= 28 ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (incoming == null || existing == null || incoming.length == 0
                || !new HashSet<>(Arrays.asList(incoming)).equals(new HashSet<>(Arrays.asList(existing))))
            throw new IOException("Signing certificate mismatch");
        // Certificate rotation is deliberately fail-closed; Android also checks the signature during installation.
    }
}
