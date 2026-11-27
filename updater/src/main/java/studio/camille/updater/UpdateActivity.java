package studio.camille.updater;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Accessible native download screen. Accepted updates proceed directly to Android’s installation confirmation. */
public final class UpdateActivity extends Activity {
    private Context localized;
    private Download job;
    private TextView status;
    private ProgressBar progress;
    private Button action;
    private boolean permissionRequested;
    private boolean installationAttempted;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            permissionRequested = state.getBoolean("permissionRequested");
            installationAttempted = state.getBoolean("installationAttempted");
        }
        localized = this;
        String locale = getIntent().getStringExtra("locale");
        if (locale != null && !locale.isEmpty()) {
            Configuration config = new Configuration(getResources().getConfiguration());
            config.setLocales(LocaleList.forLanguageTags(locale));
            localized = new ContextThemeWrapper(createConfigurationContext(config), android.R.style.Theme_Material_Light_NoActionBar);
        }
        Release release = new Release(getIntent().getStringExtra("version"), getIntent().getLongExtra("code", 0),
            getIntent().getStringExtra("url"), getIntent().getStringExtra("sha256"), getIntent().getLongExtra("size", 0));
        if (release.url == null || release.sha256 == null || release.size <= 0 || release.code <= 0) { finish(); return; }
        LinearLayout layout = new LinearLayout(localized);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        // Respect status/navigation bars on Android versions enforcing edge-to-edge.
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(localized); title.setText(R.string.camille_update_title); title.setTextSize(24);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        layout.addView(title);
        status = new TextView(localized); status.setTextSize(18); status.setPadding(0, padding, 0, padding); layout.addView(status);
        progress = new ProgressBar(localized, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); layout.addView(progress);
        action = new Button(localized); action.setMinHeight((int) (48 * getResources().getDisplayMetrics().density)); layout.addView(action);
        Button later = new Button(localized); later.setText(R.string.camille_update_later); later.setOnClickListener(v -> finish()); layout.addView(later);
        ScrollView scroll = new ScrollView(localized); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll);
        job = Download.obtain(this, release);
        action.setOnClickListener(v -> {
            if ("failed".equals(job.state)) { job = Download.obtain(this, release); job.observe(this); render(); }
            else install();
        });
    }
    @Override public void onResume() { super.onResume(); if (job != null) { job.observe(this); render(); } }
    @Override public void onPause() { if (job != null) job.detach(this); super.onPause(); }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putBoolean("permissionRequested", permissionRequested);
        state.putBoolean("installationAttempted", installationAttempted);
        super.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 82 && getPackageManager().canRequestPackageInstalls()) {
            // A recreated process may still be revalidating the cached APK.
            installationAttempted = false;
        }
        // onResume observes the transfer and continues only after verification.
    }
    void render() {
        if (isFinishing() || isDestroyed() || job == null) return;
        boolean ready = "ready".equals(job.state), failed = "failed".equals(job.state);
        progress.setVisibility(ready || failed ? View.GONE : View.VISIBLE);
        progress.setProgress(job.percent);
        progress.setIndeterminate("verifying".equals(job.state));
        action.setVisibility(ready || failed ? View.VISIBLE : View.GONE);
        action.setText(failed ? R.string.camille_update_retry : R.string.camille_update_install);
        if (ready) status.setText(permissionRequested && !getPackageManager().canRequestPackageInstalls()
            ? R.string.camille_update_permission : R.string.camille_update_ready);
        else if (failed) status.setText(R.string.camille_update_failed);
        else if ("verifying".equals(job.state)) status.setText(R.string.camille_update_verifying);
        else status.setText(localized.getString(R.string.camille_update_downloading, job.percent));
        if (ready && !installationAttempted) install();
    }
    private void install() {
        // Set before launching external UI: cancellation, rotation, and resume must
        // not reopen the installer or permission settings without another request.
        installationAttempted = true;
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                permissionRequested = true;
                status.setText(R.string.camille_update_permission);
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), 82);
                return;
            }
            Uri uri = Uri.parse("content://" + getPackageName() + ".camille-updater/update.apk");
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true);
            intent.setClipData(ClipData.newRawUri("APK", uri));
            startActivityForResult(intent, 81);
        } catch (RuntimeException e) { status.setText(R.string.camille_update_install_failed); }
    }
}
