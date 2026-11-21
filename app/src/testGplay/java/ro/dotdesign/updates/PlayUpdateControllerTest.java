package ro.dotdesign.updates;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager;
import com.google.android.play.core.install.model.AppUpdateType;
import dev.goodwy.rphone.R;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class, qualifiers = "en-rUS")
public class PlayUpdateControllerTest {
    private Activity activity;
    private FakeAppUpdateManager play;
    private UpdateRuntime runtime;
    private PlayUpdateController controller;
    private boolean phoneBusy;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        ConnectivityManager connectivity = activity.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(connectivity).setNetworkCapabilities(connectivity.getActiveNetwork(),
                capabilities);

        play = new FakeAppUpdateManager(activity);
        int installed = (int) activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
        play.setUpdateAvailable(installed + 1);
        runtime = new UpdateRuntime(activity, "unused", activity.getPackageName(), "", play);
        ReflectionHelpers.setField(runtime, "attempted", true);
        ReflectionHelpers.setField(runtime, "lastAttempt", SystemClock.elapsedRealtime());
        controller = new PlayUpdateController(activity, runtime, () -> phoneBusy,
                R.string.app_update_title, R.string.app_update_required, R.string.app_update_now,
                R.string.app_update_later,
                R.string.app_update_optional, 0xff00ac78, R.string.app_update_news);
    }

    @After public void tearDown() {
        controller.onDestroy();
    }

    @Test public void requiredCardHasOneActionAndReturnsAfterPhoneUseAndForegrounding() {
        setRequired(true);
        controller.onResume();
        AlertDialog first = currentDialog();
        assertEquals(List.of(activity.getString(R.string.app_update_now)), labels(first));

        phoneBusy = true;
        tick();
        assertFalse(first.isShowing());
        phoneBusy = false;
        tick();
        AlertDialog afterPhoneUse = currentDialog();
        assertNotSame(first, afterPhoneUse);

        controller.onPause();
        assertFalse(afterPhoneUse.isShowing());
        controller.onResume();
        assertNotSame(afterPhoneUse, currentDialog());
    }

    @Test public void cancelingPlayImmediateFlowShowsRequiredCardAgain() {
        setRequired(true);
        controller.onResume();
        AlertDialog first = currentDialog();
        buttons(first).get(0).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(play.isImmediateFlowVisible());
        play.userRejectsUpdate();
        assertTrue(controller.onActivityResult(6142, Activity.RESULT_CANCELED));
        tick();
        assertNotSame(first, currentDialog());
    }

    @Test public void optionalDismissalIsThrottledButDoesNotThrottleRequiredUpdate() {
        setRequired(false);
        controller.onResume();
        AlertDialog optional = currentDialog();
        List<Button> actions = buttons(optional);
        assertEquals(2, actions.size());
        assertEquals(activity.getString(R.string.app_update_later), actions.get(1).getText().toString());
        actions.get(1).performClick();
        tick();
        assertFalse(optional.isShowing());
        setRequired(true);
        tick();
        assertEquals(List.of(activity.getString(R.string.app_update_now)), labels(currentDialog()));
    }

    @Test public void recommendedUpdateNowAlsoPrefersImmediate() {
        setRequired(false);
        controller.onResume();
        buttons(currentDialog()).get(0).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(play.isImmediateFlowVisible());
    }

    @Test public void updateNowInstallsAnAlreadyDownloadedRequiredBuild() {
        setRequired(true);
        assertTrue(play.startUpdateFlowForResult(play.getAppUpdateInfo().getResult(), activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(), 42));
        play.userAcceptsUpdate();
        play.downloadStarts();
        play.downloadCompletes();
        controller.onResume();
        AlertDialog card = currentDialog();
        assertEquals(List.of(activity.getString(R.string.app_update_now)), labels(card));
        buttons(card).get(0).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(play.isInstallSplashScreenVisible());
        assertFalse(card.isShowing());
    }

    @Test public void acceptedFlexibleFallbackInstallsAfterCallWithoutAnotherPrompt() {
        int offered = play.getAppUpdateInfo().getResult().availableVersionCode();
        play.setUpdateAvailable(offered, AppUpdateType.FLEXIBLE);
        setRequired(false);
        controller.onResume();
        AlertDialog card = currentDialog();
        buttons(card).get(0).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(play.isConfirmationDialogVisible());
        play.userAcceptsUpdate();
        assertTrue(controller.onActivityResult(6142, Activity.RESULT_OK));
        phoneBusy = true;
        play.downloadStarts();
        play.downloadCompletes();
        tick();
        assertFalse(play.isInstallSplashScreenVisible());
        phoneBusy = false;
        tick();
        assertTrue(play.isInstallSplashScreenVisible());
        assertFalse(card.isShowing());
        assertSame(card, ShadowAlertDialog.getLatestAlertDialog());
    }

    private void setRequired(boolean required) {
        ReflectionHelpers.setField(runtime, "snapshot", new UpdateRuntime.Snapshot(
                play.getAppUpdateInfo().getResult(), new UpdateRuntime.Policy(required, new String[0]),
                SystemClock.elapsedRealtime()));
    }

    private AlertDialog currentDialog() {
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private void tick() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    }

    private static List<String> labels(AlertDialog dialog) {
        return buttons(dialog).stream().map(button -> button.getText().toString()).toList();
    }

    private static List<Button> buttons(AlertDialog dialog) {
        List<Button> buttons = new ArrayList<>();
        collectButtons(dialog.getWindow().getDecorView(), buttons);
        return buttons;
    }

    private static void collectButtons(View view, List<Button> buttons) {
        if (view instanceof Button button && view.isShown()) buttons.add(button);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), buttons);
        }
    }
}
