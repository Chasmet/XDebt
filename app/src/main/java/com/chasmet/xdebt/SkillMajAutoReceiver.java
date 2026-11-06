package com.chasmet.xdebt;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

/** Reacts only to XDebt's own DownloadManager job. */
public final class SkillMajAutoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null ||
                !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()))
            return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        final PendingResult completion = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                SkillMajAuto updater = new SkillMajAuto(app);
                if (!updater.ownsDownload(id)) return;
                File verified = updater.verifiedDownload();
                if (verified != null) {
                    String version = verified.getName()
                            .replaceFirst("^XDebt-", "").replaceFirst("\\.apk$", "");
                    SkillMajAutoWorker.notifyAvailable(app, version, true);
                }
            } finally {
                completion.finish();
            }
        }, "xdebt-skillmajauto-receiver").start();
    }
}
