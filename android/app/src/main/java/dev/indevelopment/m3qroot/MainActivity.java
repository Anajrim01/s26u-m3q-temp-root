package dev.indevelopment.m3qroot;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

public final class MainActivity extends AppCompatActivity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 0x4d33;
    private static final String KSU_MANAGER_PACKAGE = "me.weishu.kernelsu";
    private static final String SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api";
    private static final int STATUS_SUCCESS = 0xff18753c;
    private static final int STATUS_WORKING = 0xff9a6700;
    private static final int STATUS_WARNING = 0xffb3261e;
    private static final int STATUS_NEUTRAL = 0xff5f6b76;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean shizukuPermissionPending = new AtomicBoolean();
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
                if (!shizukuPermissionPending.compareAndSet(true, false)) return;
                ui.post(() -> {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        append("Shizuku shell permission granted");
                        beginExploit(true);
                    } else {
                        abortPendingRun("Shizuku permission denied; exploit was not run.");
                    }
                });
            };

    private M3qRootEngine engine;
    private MaterialCardView statusCard;
    private MaterialCardView diagnosticsCard;
    private TextView status;
    private TextView statusDetail;
    private TextView dashboard;
    private TextView log;
    private MaterialButton run;
    private MaterialButton reapplyModules;
    private MaterialButton restartZygote;
    private MaterialButton statusRefresh;
    private MaterialButton diagnosticsToggle;
    private boolean diagnosticsVisible;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.page_scroll));
        bindViews();
        bindActions();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (running.get()) {
                        append("The app cannot be closed while the kernel operation is running.");
                    } else {
                        finish();
                    }
                });

        engine = new M3qRootEngine(this, new M3qRootEngine.Listener() {
            @Override
            public void onStatus(String text, int color) {
                setStatus(text, color);
            }

            @Override
            public void onLog(String line) {
                append(line);
            }
        });

        append("==== device diagnostics ====");
        append("Model: " + Build.MODEL);
        append("Kernel: " + System.getProperty("os.version", "unknown"));
        append("Firmware: " + Build.FINGERPRINT);

        if (!engine.isSupported()) {
            setStatus("Unsupported firmware", STATUS_WARNING);
            setStatusDetail("This app only runs on SM-S948B AZG5 firmware.");
            run.setEnabled(false);
            append("Only the exact SM-S948B AZG5 build is supported.");
        } else {
            setStatus(getString(R.string.status_checking), STATUS_WORKING);
            setStatusDetail(getString(R.string.status_checking_detail));
        }
    }

    private void bindViews() {
        statusCard = findViewById(R.id.status_card);
        diagnosticsCard = findViewById(R.id.diagnostics_card);
        status = findViewById(R.id.status);
        statusDetail = findViewById(R.id.status_detail);
        dashboard = findViewById(R.id.dashboard);
        log = findViewById(R.id.log);
        log.setMovementMethod(new ScrollingMovementMethod());
        run = findViewById(R.id.run);
        reapplyModules = findViewById(R.id.reapply_modules);
        restartZygote = findViewById(R.id.restart_zygote);
        statusRefresh = findViewById(R.id.status_refresh);
        diagnosticsToggle = findViewById(R.id.diagnostics_toggle);
    }

    private static void applySystemBarInsets(View view) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            target.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + bars.bottom);
            return windowInsets;
        });
        view.requestApplyInsets();
    }

    private void bindActions() {
        run.setOnClickListener(v -> onRunClicked());
        reapplyModules.setOnClickListener(v -> confirmModuleReload());
        restartZygote.setOnClickListener(v -> confirmSoftBoot());
        statusRefresh.setOnClickListener(v -> worker.execute(this::refreshRootState));
        findViewById(R.id.root_manager).setOnClickListener(v ->
                openPackage(KSU_MANAGER_PACKAGE,
                        "KernelSU Manager is not installed."));
        findViewById(R.id.shizuku_manager).setOnClickListener(v ->
                openPackage(SHIZUKU_MANAGER_PACKAGE,
                        "Shizuku Manager is not installed."));
        findViewById(R.id.share_log).setOnClickListener(v -> shareLastLog());
        diagnosticsToggle.setOnClickListener(v -> toggleDiagnostics());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (engine != null && engine.isSupported() && !running.get()) {
            worker.execute(this::refreshRootState);
        }
    }

    private void toggleDiagnostics() {
        diagnosticsVisible = !diagnosticsVisible;
        diagnosticsCard.setVisibility(diagnosticsVisible ? View.VISIBLE : View.GONE);
        diagnosticsToggle.setText(diagnosticsVisible
                ? R.string.hide_diagnostics : R.string.show_diagnostics);
        if (diagnosticsVisible) {
            scrollLogToBottom();
        }
    }

    private void onRunClicked() {
        if (!running.compareAndSet(false, true)) return;
        worker.execute(() -> {
            M3qRootEngine.RootState current = engine.checkRoot(false);
            if (current.terminationUnconfirmed()) {
                finishUnconfirmedRun();
                return;
            }
            if (current.ready()) {
                finishRun(current);
                return;
            }
            if (current.bootstrap()) {
                append("Bootstrap root detected · activating KernelSU without rerunning the exploit.");
                setStatus("Activating KernelSU", STATUS_WORKING);
                setStatusDetail("Finishing KernelSU configuration without repeating kernel writes.");
                ui.post(this::lockUiForRun);
                int code = engine.activateKernelSu();
                append("KernelSU activation exit=" + code);
                if (code == M3qRootEngine.EXIT_TERMINATION_UNCONFIRMED) {
                    finishUnconfirmedRun();
                    return;
                }
                finishRun(engine.checkRoot(true));
                return;
            }
            if (engine.hasAttemptedThisBoot()) {
                running.set(false);
                ui.post(() -> {
                    run.setVisibility(View.VISIBLE);
                    run.setEnabled(false);
                    setStatus("Already run this boot", STATUS_WARNING);
                    setStatusDetail("For safety, it cannot be run again before reboot.");
                    append("Blocked a retry with the same boot ID.");
                    renderDashboard(current);
                });
                return;
            }
            running.set(false);
            ui.post(this::confirmFreshRoot);
        });
    }

    private void confirmFreshRoot() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.root_dialog_title)
                .setMessage(R.string.root_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.root_dialog_confirm,
                        (dialog, which) -> startExploit())
                .show();
    }

    private void startExploit() {
        if (!running.compareAndSet(false, true)) return;
        lockUiForRun();
        append("==== fresh-root start ====");

        if (ShizukuShell.isRunning()) {
            int uid = ShizukuShell.uid();
            append("Shizuku detected: uid=" + uid + " · tracefs fast path");
            if (!ShizukuShell.isGranted()) {
                setStatus("Shizuku permission required", STATUS_WORKING);
                setStatusDetail("Approve the Shizuku permission request that appears.");
                try {
                    shizukuPermissionPending.set(true);
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                } catch (RuntimeException error) {
                    shizukuPermissionPending.set(false);
                    abortPendingRun("Shizuku permission request error: " + error.getMessage());
                }
                return;
            }
            beginExploit(true);
            return;
        }

        append("Shizuku is not running; using the exact Image physical-P0 path.");
        beginExploit(false);
    }

    private void beginExploit(boolean useShizuku) {
        long settleMillis = M3qRootEngine.bootSettleRemainingMillis();
        if (settleMillis > 0) {
            long settleSeconds = (settleMillis + 999) / 1000;
            abortPendingRun("Wait about " + settleSeconds
                    + " seconds after boot for system stabilization, then try again. This boot's attempt was not consumed.");
            return;
        }
        if (!engine.markAttemptForThisBoot()) {
            abortPendingRun("Kernel execution was refused because boot state could not be verified.");
            return;
        }
        setStatus("Activating temporary root", STATUS_WORKING);
        setStatusDetail(useShizuku
                ? "Checking safety conditions through the Shizuku connection."
                : "Checking device security state before applying temporary root.");
        worker.execute(() -> {
            int code = engine.runFreshRoot(useShizuku);
            append("fresh-root exit=" + code);
            if (code == M3qRootEngine.EXIT_TERMINATION_UNCONFIRMED) {
                finishUnconfirmedRun();
                return;
            }
            finishRun(engine.checkRoot(true));
        });
    }

    private void confirmModuleReload() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.soft_root_dialog_title)
                .setMessage(R.string.soft_root_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.soft_root_dialog_confirm,
                        (dialog, which) -> startModuleReload())
                .show();
    }

    private void startModuleReload() {
        if (!running.compareAndSet(false, true)) return;
        lockUiForRun();
        setStatus("Reloading KernelSU modules", STATUS_WORKING);
        setStatusDetail("Running the KernelSU module startup stages again.");
        append("==== KernelSU module reapply start ====");
        worker.execute(() -> {
            M3qRootEngine.RootState state = engine.checkRoot(false);
            if (!state.ready()) {
                append("KernelSU temporary root is not active; nothing was run.");
                finishMaintenance(126, state, "", "");
                return;
            }
            int code = engine.reapplyKernelSuModules();
            append("module reapply exit=" + code);
            if (code == M3qRootEngine.EXIT_TERMINATION_UNCONFIRMED) {
                finishUnconfirmedRun();
                return;
            }
            finishMaintenance(code, engine.checkRoot(false),
                    "Modules reloaded",
                    "KernelSU modules were reapplied. Run Soft boot next.");
        });
    }

    private void confirmSoftBoot() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lsposed_dialog_title)
                .setMessage(R.string.lsposed_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.lsposed_dialog_confirm,
                        (dialog, which) -> startSoftBoot())
                .show();
    }

    private void startSoftBoot() {
        if (!running.compareAndSet(false, true)) return;
        lockUiForRun();
        setStatus("Preparing soft boot", STATUS_WORKING);
        setStatusDetail("Restarting the Android app environment (Zygote).");
        append("Requesting a Zygote restart. This app will also exit if it succeeds.");
        worker.execute(() -> {
            M3qRootEngine.RootState state = engine.checkRoot(false);
            if (!state.ready()) {
                append("KernelSU temporary root is not active; nothing was run.");
                finishMaintenance(126, state, "", "");
                return;
            }
            int code = engine.restartZygote();
            append("zygote restart exit=" + code);
            if (code == M3qRootEngine.EXIT_TERMINATION_UNCONFIRMED) {
                finishUnconfirmedRun();
                return;
            }
            finishMaintenance(code, engine.checkRoot(false),
                    "Soft boot requested",
                    "Check the LSPosed manager for active status in a moment.");
        });
    }

    private void finishMaintenance(int code, M3qRootEngine.RootState state,
                                   String successText, String successDetail) {
        running.set(false);
        ui.post(() -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            statusRefresh.setEnabled(true);
            renderRootState(state);
            if (code == 0) {
                setStatus(successText, STATUS_SUCCESS);
                setStatusDetail(successDetail);
                return;
            }
            String reason = switch (code) {
                case 124 -> "Could not confirm that the operation completed.";
                case 125 -> "KernelSU configuration verification failed.";
                case 126 -> "KernelSU root permission is required.";
                default -> "Command failed. code=" + code;
            };
            setStatus("Operation failed", STATUS_WARNING);
            setStatusDetail(reason + " Check the status again.");
        });
    }

    private void lockUiForRun() {
        run.setEnabled(false);
        reapplyModules.setEnabled(false);
        restartZygote.setEnabled(false);
        statusRefresh.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void abortPendingRun(String message) {
        append(message);
        running.set(false);
        ui.post(() -> {
            run.setVisibility(View.VISIBLE);
            run.setEnabled(engine.isSupported());
            reapplyModules.setEnabled(false);
            restartZygote.setEnabled(false);
            statusRefresh.setEnabled(true);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setStatus("Not run", STATUS_NEUTRAL);
            setStatusDetail(message);
        });
    }

    private void refreshRootState() {
        M3qRootEngine.RootState state = engine.checkRoot(false);
        ui.post(() -> renderRootState(state));
    }

    private void renderRootState(M3qRootEngine.RootState state) {
        if (state.terminationUnconfirmed()) {
            run.setVisibility(View.VISIBLE);
            setStatus("Operation status unknown", STATUS_WARNING);
            setStatusDetail("For safety, reboot the device before checking again.");
            run.setText(R.string.run_reboot_check);
            run.setEnabled(false);
        } else if (state.ready()) {
            setStatus("Temporary root active", STATUS_SUCCESS);
            setStatusDetail("KernelSU 3.3.0 · cleared on reboot");
            run.setVisibility(View.GONE);
        } else if (state.bootstrap()) {
            run.setVisibility(View.VISIBLE);
            setStatus("Root ready", STATUS_WORKING);
            setStatusDetail("Only KernelSU activation remains.");
            run.setText(R.string.run_kernel_su_activate);
            run.setEnabled(true);
        } else if (engine.hasAttemptedThisBoot()) {
            run.setVisibility(View.VISIBLE);
            setStatus("Already run this boot", STATUS_WARNING);
            setStatusDetail("For safety, it cannot be run again before reboot.");
            run.setText(R.string.run_reboot_retry);
            run.setEnabled(false);
        } else {
            run.setVisibility(View.VISIBLE);
            setStatus("Temporary root inactive", STATUS_NEUTRAL);
            setStatusDetail("Supported device confirmed · ready to run");
            run.setText(R.string.root_activate);
            run.setEnabled(engine.isSupported());
        }
        boolean maintenanceReady = state.ready() && !running.get();
        reapplyModules.setEnabled(maintenanceReady);
        restartZygote.setEnabled(maintenanceReady);
        statusRefresh.setEnabled(!running.get());
        renderDashboard(state);
    }

    private void renderDashboard(M3qRootEngine.RootState state) {
        boolean shizukuRunning = ShizukuShell.isRunning();
        boolean shizukuGranted = ShizukuShell.isGranted();
        int shizukuUid = ShizukuShell.uid();
        String shizuku = !shizukuRunning ? "Not connected"
                : !shizukuGranted ? "Permission required"
                : (shizukuUid == 2000 || shizukuUid == 0)
                ? "Connected" : "Permission limited";
        String rootState = state.terminationUnconfirmed() ? "Check required"
                : state.ready() ? "Active"
                : state.bootstrap() ? "Ready" : "Inactive";
        String manager = getPackageManager().getLaunchIntentForPackage(
                KSU_MANAGER_PACKAGE) == null ? "Install required" : "Installed";
        String attempted = engine.hasAttemptedThisBoot() ? "Completed" : "Not run";
        dashboard.setText(getString(R.string.dashboard_format,
                engine.isSupported() ? "Supported" : "Not supported",
                shizuku, rootState, manager, attempted));
    }

    private void finishRun(M3qRootEngine.RootState state) {
        if (state.terminationUnconfirmed()) {
            finishUnconfirmedRun();
            return;
        }
        running.set(false);
        ui.post(() -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            statusRefresh.setEnabled(true);
            if (state.ready()) {
                setStatus("Temporary root active", STATUS_SUCCESS);
                setStatusDetail("KernelSU 3.3.0 · cleared on reboot");
                run.setVisibility(View.GONE);
                openPackage(KSU_MANAGER_PACKAGE,
                        "KernelSU Manager is not installed.");
            } else if (state.bootstrap()) {
                run.setVisibility(View.VISIBLE);
                setStatus("Root ready", STATUS_WORKING);
                setStatusDetail("You can try activating KernelSU again.");
                run.setText(R.string.run_kernel_su_reactivate);
                run.setEnabled(true);
            } else {
                run.setVisibility(View.VISIBLE);
                setStatus("Temporary root activation failed", STATUS_WARNING);
                setStatusDetail("Reboot the device and check the status again.");
                run.setEnabled(false);
            }
            reapplyModules.setEnabled(state.ready());
            restartZygote.setEnabled(state.ready());
            renderDashboard(state);
        });
    }

    private void finishUnconfirmedRun() {
        running.set(false);
        append("Process control was lost, so termination could not be proven. Do not retry before reboot.");
        ui.post(() -> {
            run.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setStatus("Operation status unknown", STATUS_WARNING);
            setStatusDetail("Do not run the same operation again before reboot.");
            run.setText(R.string.run_reboot_check);
            run.setEnabled(false);
            reapplyModules.setEnabled(false);
            restartZygote.setEnabled(false);
            statusRefresh.setEnabled(true);
        });
    }

    private void openPackage(String packageName, String missingMessage) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            append(missingMessage);
            setStatus("Cannot open manager app", STATUS_NEUTRAL);
            setStatusDetail(missingMessage);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void shareLastLog() {
        File file = engine.lastRootLog();
        if (!file.isFile()) {
            append("There is no run log to save yet.");
            setStatus("No diagnostic report", STATUS_NEUTRAL);
            setStatusDetail("Run temporary root once before creating a report.");
            return;
        }
        try {
            String text = LogRedactor.redact(readLogTail(file));
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                    .putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(share, getString(R.string.share_chooser)));
        } catch (IOException error) {
            append("Could not read log: " + error.getMessage());
        }
    }

    private String readLogTail(File file) throws IOException {
        final int limit = 64 * 1024;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long skipped = Math.max(0, input.length() - limit);
            input.seek(skipped);
            byte[] bytes = new byte[(int) Math.min(limit, input.length())];
            input.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (skipped == 0) return text;
            return "[Beginning and truncated first line omitted]\n"
                    + LogRedactor.dropPartialFirstLine(text);
        }
    }

    private void setStatus(String text, int semanticColor) {
        ui.post(() -> {
            int color = resolveStatusColor(semanticColor);
            status.setText(text);
            status.setTextColor(color);
            statusCard.setStrokeColor(color);
        });
    }

    private int resolveStatusColor(int semanticColor) {
        if (semanticColor == STATUS_SUCCESS) return getColor(R.color.m3q_success);
        if (semanticColor == STATUS_WORKING) return getColor(R.color.m3q_warning);
        if (semanticColor == STATUS_WARNING) return getColor(R.color.m3q_error);
        return getColor(R.color.m3q_neutral);
    }

    private void setStatusDetail(String text) {
        ui.post(() -> statusDetail.setText(text));
    }

    private void append(String line) {
        ui.post(() -> {
            log.append(line + "\n");
            if (diagnosticsVisible) {
                scrollLogToBottom();
            }
        });
    }

    private void scrollLogToBottom() {
        log.post(() -> {
            if (log.getLayout() == null) return;
            int scroll = log.getLayout().getLineTop(log.getLineCount())
                    - log.getHeight();
            log.scrollTo(0, Math.max(0, scroll));
        });
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        worker.shutdown();
        super.onDestroy();
    }
}
