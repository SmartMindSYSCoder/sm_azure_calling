package com.sm.azure_calling;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.app.ActivityManager;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import com.azure.android.communication.common.CommunicationTokenCredential;
import com.azure.android.communication.common.CommunicationTokenRefreshOptions;
import com.azure.android.communication.ui.calling.CallComposite;
import com.azure.android.communication.ui.calling.CallCompositeBuilder;
import com.azure.android.communication.ui.calling.models.CallCompositeCallScreenHeaderViewData;
import com.azure.android.communication.ui.calling.models.CallCompositeCustomButtonViewData;

import com.azure.android.communication.ui.calling.models.CallCompositeCallScreenOptions;
import com.azure.android.communication.ui.calling.models.CallCompositeGroupCallLocator;
import com.azure.android.communication.ui.calling.models.CallCompositeJoinLocator;
import com.azure.android.communication.ui.calling.models.CallCompositeLocalOptions;
import com.azure.android.communication.ui.calling.models.CallCompositeMultitaskingOptions;
import com.azure.android.communication.ui.calling.models.CallCompositeTeamsMeetingLinkLocator;
import java.util.ArrayList;
import java.util.List;
public class AzureCallingPlugin implements
        FlutterPlugin,
        MethodChannel.MethodCallHandler,
        ActivityAware {

  private MethodChannel channel;
  private Context applicationContext;
  private Activity activity;
  private CallComposite callComposite;

  private Application.ActivityLifecycleCallbacks lifecycleCallbacks;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    applicationContext = binding.getApplicationContext();
    channel = new MethodChannel(binding.getBinaryMessenger(), "azure_calling");
    channel.setMethodCallHandler(this);
  }

  // ActivityAware
  @Override public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) { activity = binding.getActivity(); }
  @Override public void onDetachedFromActivityForConfigChanges() {

    activity = null; }
  @Override public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) { activity = binding.getActivity(); }
  @Override public void onDetachedFromActivity() {
    if (callComposite != null) { callComposite.dismiss(); }

    activity = null; }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    channel = null;
    applicationContext = null;
    if (callComposite != null) { callComposite.dismiss(); }

  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    if ("getPlatformVersion".equals(call.method)) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
      return;
    }

    if ("startCall".equals(call.method)) {
      String token = null;
      String meetingLink = null;
      String groupId = null;
      String displayName = null;
      String title = "";
      String subTitle = "";
      boolean skipSetupScreen = false;
      boolean cameraOn = false;
      boolean microphoneOn = false;

      if (call.arguments instanceof Map) {
        Map<?, ?> args = (Map<?, ?>) call.arguments;
        token = (String) args.get("token");
        meetingLink = (String) args.get("meetingLink");
        groupId = (String) args.get("groupId");
        displayName = (String) args.get("displayName");
        title = args.get("title") != null ? (String) args.get("title") : "";
        subTitle = args.get("subTitle") != null ? (String) args.get("subTitle") : "";
        skipSetupScreen = args.get("skipSetupScreen") instanceof Boolean ? (Boolean) args.get("skipSetupScreen") : false;
        cameraOn = args.get("cameraOn") instanceof Boolean ? (Boolean) args.get("cameraOn") : false;
        microphoneOn = args.get("microphoneOn") instanceof Boolean ? (Boolean) args.get("microphoneOn") : false;
      }

      try {
        startCall(token, meetingLink, groupId, displayName, title, subTitle, skipSetupScreen, cameraOn, microphoneOn);
        result.success(null);
      } catch (Exception e) {
        result.error("START_CALL_FAILED", e.getMessage(), null);
      }
      return;
    }

    else if("endCall".equals(call.method)){


      try {
        endCall();
        result.success(null);
      } catch (Exception e) {
        result.error("END_CALL_FAILED", e.getMessage(), null);
      }
      return;

    }

    result.notImplemented();
  }

  // Install once
  private void installLifecycleGuard() {
    if (!(applicationContext instanceof Application)) return;
    if (lifecycleCallbacks != null) return;

    lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
      @Override public void onActivityCreated(Activity a, Bundle b) { applyAcsWindowRules(a); }
      @Override public void onActivityResumed(Activity a)          { applyAcsWindowRules(a); }
      @Override public void onActivityStarted(Activity a)          { }
      @Override public void onActivityPaused(Activity a)           { }
      @Override public void onActivityStopped(Activity a)          { }
      @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
      @Override public void onActivityDestroyed(Activity a)        { }
    };
    ((Application) applicationContext).registerActivityLifecycleCallbacks(lifecycleCallbacks);
  }

  /** Apply edge-to-edge with safe padding AND start watching for bottom sheets to pad them (inset+100dp). */
  private void applyAcsWindowRules(Activity a) {
    // Target only ACS CallComposite activities (class name typically contains "communication")
    final String cls = a.getClass().getName().toLowerCase();
    if (!cls.contains("communication")) return;

    final Window w = a.getWindow();

    // Draw edge-to-edge but let our root view add safe padding so content doesn't sit under bars.
    WindowCompat.setDecorFitsSystemWindows(w, false);
    w.setStatusBarColor(Color.TRANSPARENT);
    w.setNavigationBarColor(Color.TRANSPARENT);
    w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    final WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, w.getDecorView());
    if (ic != null) {
      ic.show(WindowInsetsCompat.Type.systemBars());
      ic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);
    }

    final ViewGroup content = a.findViewById(android.R.id.content);
    if (content == null) return;

    content.post(() -> {
      final View root = content.getChildCount() > 0 ? content.getChildAt(0) : content;

      // Only attach once
      if (Boolean.TRUE.equals(root.getTag(0xAA11AA11))) return;

      final int baseL = root.getPaddingLeft();
      final int baseT = root.getPaddingTop();
      final int baseR = root.getPaddingRight();
      final int baseB = root.getPaddingBottom();

      OnApplyWindowInsetsListener l = (v, insets) -> {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        Insets ime  = insets.getInsets(WindowInsetsCompat.Type.ime());

        int padLeft   = baseL + bars.left;
        int padTop    = baseT + bars.top; // status/cutout -> protects top/AppBar
        int padRight  = baseR + bars.right;
        int padBottom = baseB + Math.max(bars.bottom, ime.bottom); // protects bottom nav & IME

        v.setPadding(padLeft, padTop, padRight, padBottom);
        return insets;
      };
      ViewCompat.setOnApplyWindowInsetsListener(root, l);
      ViewCompat.requestApplyInsets(root);
      root.setTag(0xAA11AA11, Boolean.TRUE);

      // Re-assert E2E a few times (some SDKs flip it)
      final int[] left = {6};
      root.post(new Runnable() {
        @Override public void run() {
          WindowCompat.setDecorFitsSystemWindows(w, false);
          if (--left[0] > 0) root.postDelayed(this, 120);
        }
      });

      // Start watching for Material BottomSheet and pad it (system inset + 100dp)
      hookBottomSheetExtraPadding(w.getDecorView());
    });
  }

  /** Adds system bottom inset + 100dp to any Material BottomSheet when it appears (once). */
  private void hookBottomSheetExtraPadding(View decor) {
    if (decor == null) return;

    final int[] attempts = {0};
    final Runnable finder = new Runnable() {
      @Override public void run() {
        attempts[0]++;

        // Use the Material ID constant directly (do NOT resolve via app resources).
        View sheetView = decor.findViewById(com.google.android.material.R.id.design_bottom_sheet);

        if (sheetView != null) {
          // Avoid double-hook
          if (!Boolean.TRUE.equals(sheetView.getTag(0xBB22BB22))) {
            ViewCompat.setOnApplyWindowInsetsListener(sheetView, (v, insets) -> {
              Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
              Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

              int extraPx = dpToPx(v.getContext(), 200);
              int bottom = Math.max(sys.bottom, ime.bottom) + extraPx;

              v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
              return insets;
            });
            ViewCompat.requestApplyInsets(sheetView);
            sheetView.setTag(0xBB22BB22, Boolean.TRUE);
            Log.d("AzureCallingPlugin", "✓ BottomSheet padded with system inset + 100dp");
          }
          // Keep a light watcher for future sheets too
          decor.postDelayed(this, 500);
          return;
        }

        // Retry until a sheet shows up; keep lightweight polling
        if (attempts[0] < 200) { // ~100s max, negligible cost
          decor.postDelayed(this, 500);
        }
      }
    };

    decor.postDelayed(finder, 250);
  }

  private int dpToPx(Context c, int dp) {
    return Math.round(dp * c.getResources().getDisplayMetrics().density);
  }



  private void hideAcsTaskFromRecentsRobust(Context ctx) {
    final ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
    if (am == null) return;

    final String[] candidateClassNames = new String[] {
            // Try common ACS class names across versions. Adjust if your merged manifest shows a different one.
            "com.azure.android.communication.ui.calling.presentation.activity.CallCompositeActivity",
            "com.azure.android.communication.ui.calling.presentation.CallCompositeActivity"
    };
    final String[] candidatePackageHints = new String[] {
            "azure", "communication", "calling", "acs"
    };

    final int[] tries = {0};
    final Runnable r = new Runnable() {
      @Override public void run() {
        tries[0]++;

        boolean changed = false;

        for (ActivityManager.AppTask t : am.getAppTasks()) {
          ActivityManager.RecentTaskInfo info = t.getTaskInfo();
          if (info == null) continue;

          String baseCls = info.baseActivity != null ? info.baseActivity.getClassName() : null;
          String topCls  = info.topActivity  != null ? info.topActivity.getClassName()  : null;
          String basePkg = info.baseActivity != null ? info.baseActivity.getPackageName() : null;

          // Log what we see (one-time when first try or if you want always, keep this)
          if (tries[0] <= 3) {
            Log.d("AzureCallingPlugin",
                    "Task check: base=" + baseCls + " top=" + topCls + " pkg=" + basePkg);
          }

          boolean looksLikeAcs =
                  // 1) exact class match (best)
                  matchesAny(baseCls, candidateClassNames) || matchesAny(topCls, candidateClassNames)
                          // 2) package hint match (fallback)
                          || containsAnyIgnoreCase(basePkg, candidatePackageHints)
                          || containsAnyIgnoreCase(baseCls, candidatePackageHints)
                          || containsAnyIgnoreCase(topCls,  candidatePackageHints);

          if (looksLikeAcs) {
            try {
              t.setExcludeFromRecents(true);
              changed = true;
              Log.i("AzureCallingPlugin", "✓ Marked ACS task excluded from recents: base=" + baseCls);
            } catch (Throwable ex) {
              Log.w("AzureCallingPlugin", "setExcludeFromRecents failed: " + ex);
            }
          }
        }

        // Retry briefly because ACS may create/mutate its task after launch.
        if (!changed && tries[0] < 12) { // ~2.4s total
          new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 200);
        }
      }

      private boolean matchesAny(String value, String[] candidates) {
        if (value == null) return false;
        for (String c : candidates) if (value.equals(c)) return true;
        return false;
      }
      private boolean containsAnyIgnoreCase(String value, String[] needles) {
        if (value == null) return false;
        String v = value.toLowerCase();
        for (String n : needles) if (v.contains(n)) return true;
        return false;
      }
    };

    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, 200);
  }


  // --- Start ACS call ---
  private void startCall(
          String token,
          String meetingLink,
          String groupId,
          String displayName,
          String title,
          String subTitle,
          boolean skipSetupScreen,
          boolean cameraOn,
          boolean microphoneOn
  ) {
    if (applicationContext == null) throw new IllegalStateException("Application context is null");
    if (activity == null) throw new IllegalStateException("Activity is null (plugin must be attached to an Activity)");

    CommunicationTokenRefreshOptions refreshOptions =
            new CommunicationTokenRefreshOptions((Callable<String>) () -> token, true);
    CommunicationTokenCredential credential = new CommunicationTokenCredential(refreshOptions);

    CallCompositeJoinLocator locator;
    if (groupId != null && !groupId.isEmpty()) {
      locator = new CallCompositeGroupCallLocator(UUID.fromString(groupId));
    } else {
      locator = new CallCompositeTeamsMeetingLinkLocator(meetingLink);
    }

    int themeId = applicationContext.getResources()
            .getIdentifier("AcsEdgeSafe", "style", applicationContext.getPackageName());

    CallCompositeBuilder builder = new CallCompositeBuilder()
            .applicationContext(applicationContext)
            .credential(credential)
            .displayName(displayName)
            .multitasking(new CallCompositeMultitaskingOptions(true, true));

    if (themeId != 0) {
      builder.theme(themeId);
      Log.d("AzureCallingPlugin", "AcsEdgeSafe theme applied (id=" + themeId + ")");
    }

     callComposite = builder.build();

    callComposite.addOnErrorEventHandler(e -> {
      Log.e("ACS_UI", "Join error: " + e.getErrorCode(), e.getCause());
    });


    List<CallCompositeCustomButtonViewData> headerCustomButtons = new ArrayList<>();


    headerCustomButtons.add(
            new CallCompositeCustomButtonViewData(
                    "chat",
                    R.drawable.chat,

                    "Chat",
                    eventArgs -> {
                      callComposite.sendToBackground();
                      // process my button onClick
                    }
            )
    );
    CallCompositeCallScreenHeaderViewData header = new CallCompositeCallScreenHeaderViewData();
    header.setTitle(title);
    header.setSubtitle(subTitle).setCustomButtons(headerCustomButtons);

    CallCompositeCallScreenOptions callScreenOptions = new CallCompositeCallScreenOptions();
    callScreenOptions.setHeaderViewData(header);

    final CallCompositeLocalOptions localOptions = new CallCompositeLocalOptions()
            .setSkipSetupScreen(skipSetupScreen)
            .setCameraOn(cameraOn)
            .setMicrophoneOn(microphoneOn)
            .setCallScreenOptions(callScreenOptions);

    installLifecycleGuard();

    activity.runOnUiThread(() -> {
      callComposite.launch(activity, locator, localOptions);
//      hideAcsTaskFromRecentsRobust(applicationContext);
    });

  }

  private void endCall(){
    if (callComposite != null) { callComposite.dismiss(); }

  }

}
