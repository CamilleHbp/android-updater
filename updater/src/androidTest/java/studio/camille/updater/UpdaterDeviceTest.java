package studio.camille.updater;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UpdaterDeviceTest {
    final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    final Context context = instrumentation.getTargetContext();
    Activity activity;
    Release release = new Release("99.0", 999999, "https://invalid.test/update.apk", new String(new char[64]).replace('\0', 'a'), 100);

    @After public void cleanup() {
        if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
        Download.current = null;
        context.getSharedPreferences("camille-updater", 0).edit().clear().commit();
    }
    private void offer(String language) throws Exception {
        activity = instrumentation.startActivitySync(new Intent(context, TestHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            try {
                Configuration config = new Configuration(activity.getResources().getConfiguration());
                config.setLocales(LocaleList.forLanguageTags(language));
                activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
                Constructor<Updater> constructor = Updater.class.getDeclaredConstructor(Application.class); constructor.setAccessible(true);
                Updater updater = constructor.newInstance(activity.getApplication());
                Field foreground = Updater.class.getDeclaredField("foreground"); foreground.setAccessible(true); foreground.set(updater, new WeakReference<>(activity));
                Field available = Updater.class.getDeclaredField("available"); available.setAccessible(true); available.set(updater, release);
                Method method = Updater.class.getDeclaredMethod("offer"); method.setAccessible(true); method.invoke(updater);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        instrumentation.waitForIdleSync();
    }
    @Test public void englishOfferAndSnooze() throws Exception {
        offer("en");
        onView(withText("Version 99.0 is available. Download and install it now?")).inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withText("Later")).inRoot(isDialog()).perform(click());
        assertTrue(context.getSharedPreferences("camille-updater", 0).getLong("snooze", 0) > System.currentTimeMillis());
    }
    @Test public void frenchOffer() throws Exception {
        offer("fr");
        onView(withText("Télécharger la mise à jour")).inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withText("Plus tard")).inRoot(isDialog()).perform(click());
    }
    private void screen(boolean ready) throws Exception {
        if (ready) {
            Constructor<Download> constructor = Download.class.getDeclaredConstructor(Release.class); constructor.setAccessible(true);
            Download.current = constructor.newInstance(release); Download.current.state = "ready";
        }
        Intent intent = new Intent(context, UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("version", release.version).putExtra("code", release.code).putExtra("url", release.url)
            .putExtra("sha256", release.sha256).putExtra("size", release.size).putExtra("locale", "en");
        activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
    }
    @Test public void failedDownloadCanRetry() throws Exception {
        screen(false);
        Updater.IO.submit(() -> {}).get(); instrumentation.waitForIdleSync();
        onView(withText("Try again")).check(matches(isDisplayed()));
        onView(withText("Try again")).perform(click());
        Updater.IO.submit(() -> {}).get(); instrumentation.waitForIdleSync();
        onView(withText("Try again")).check(matches(isDisplayed()));
    }
    @Test public void readyScreenRequiresExplicitInstallTap() throws Exception {
        screen(true);
        onView(withText("Install update")).check(matches(isDisplayed()));
        onView(withText("Your update is ready. Android will ask you to confirm installation.")).check(matches(isDisplayed()));
    }
    @Test public void rejectsInstalledVersionAndWrongPackageBeforeInstallation() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        File apk = new File(context.getApplicationInfo().sourceDir);
        try { Download.validate(context, apk, new Release(info.versionName, Updater.code(info), "", "", apk.length())); fail("must reject reinstall"); }
        catch (java.io.IOException expected) {}
        try { Download.validate(context, apk, release); fail("must reject wrong version"); }
        catch (java.io.IOException expected) {}
    }
}
