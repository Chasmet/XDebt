package com.chasmet.xdebt;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI entry point for SkillMajAuto: checking, downloading and prompting the
 * Android package installer. Updates never uninstall the existing package.
 */
public final class UpdateManager {
    private final MainActivity activity;
    private final SkillMajAuto updater;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean checking = false;
    private boolean promptShowing = false;

    public UpdateManager(MainActivity activity) {
        this.activity = activity;
        this.updater = new SkillMajAuto(activity);
        if (updater.enabled()) SkillMajAutoWorker.schedule(activity);
    }

    public boolean automaticChecksEnabled() { return updater.enabled(); }

    public void setAutomaticChecksEnabled(boolean enabled) {
        updater.setEnabled(enabled);
    }

    private void dialog(String title, String message) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                .setPositiveButton("Fermer", null).show();
    }

    public void check(boolean manual) {
        if (checking || executor.isShutdown()) return;
        checking = true;
        executor.execute(() -> {
            SkillMajAuto.Result result = updater.check();
            checking = false;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (result.release == null) {
                    if (manual) dialog("SkillMajAuto", result.message);
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setTitle("SkillMajAuto · XDebt " + result.release.version)
                        .setMessage("Une nouvelle version officielle est disponible. "
                                + "Elle sera téléchargée, puis contrôlée avant installation. "
                                + "Android demandera votre autorisation. Les données de XDebt "
                                + "seront conservées si la signature est identique.")
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Télécharger", (d, which) -> {
                            try {
                                updater.download(result.release);
                                Toast.makeText(activity,
                                        "Téléchargement SkillMajAuto lancé.",
                                        Toast.LENGTH_LONG).show();
                            } catch (Exception ex) {
                                dialog("Échec SkillMajAuto", ex.getMessage());
                            }
                        }).show();
            });
        });
    }

    /** Recovery after process death, installer permission prompt or app resume. */
    public void resumePendingInstall() {
        if (!updater.hasPendingDownload() || promptShowing) return;
        File verified = updater.verifiedDownload();
        if (verified == null) return;
        promptShowing = true;
        new AlertDialog.Builder(activity)
                .setTitle("SkillMajAuto · APK vérifié")
                .setMessage("Le téléchargement est terminé. La version, "
                        + "le nom du paquet et la signature sont compatibles. "
                        + "Installer XDebt sans effacer les données ?")
                .setNegativeButton("Plus tard", (d, w) -> promptShowing = false)
                .setOnCancelListener(d -> promptShowing = false)
                .setPositiveButton("Installer", (d, w) -> {
                    promptShowing = false;
                    try {
                        updater.install(verified, activity);
                    } catch (Exception ex) {
                        dialog("Installation refusée", ex.getMessage());
                    }
                }).show();
    }

    public void destroy() {
        executor.shutdownNow();
    }
}
