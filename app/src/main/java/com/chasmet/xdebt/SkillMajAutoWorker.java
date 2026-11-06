package com.chasmet.xdebt;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Background SkillMajAuto checks, on a best-effort Android schedule. */
public final class SkillMajAutoWorker extends Worker {
    private static final String JOB = "xdebt_skillmajauto_periodic";
    private static final String CHANNEL = "xdebt_updates";

    public SkillMajAutoWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        if (BuildConfig.DEBUG) return;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SkillMajAutoWorker.class, 12, TimeUnit.HOURS)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(JOB, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(JOB);
    }

    static void notifyAvailable(Context context, String version, boolean ready) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Mises à jour XDebt", NotificationManager.IMPORTANCE_DEFAULT));

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ready ? "com.chasmet.xdebt.UPDATE_READY" :
                "com.chasmet.xdebt.NEW_RELEASE");
        PendingIntent open = PendingIntent.getActivity(context, ready ? 652 : 651,
                intent, PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        String title = ready ? "XDebt : mise à jour prête" : "XDebt " + version + " disponible";
        String details = ready ? "Ouvre XDebt pour installer la version vérifiée." :
                "SkillMajAuto a détecté une nouvelle version officielle.";
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ?
                new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.brand_x)
                .setContentTitle(title)
                .setContentText(details)
                .setContentIntent(open)
                .setAutoCancel(true);
        manager.notify(ready ? 652 : 651, builder.build());
    }

    @Override public Result doWork() {
        Context app = getApplicationContext();
        SkillMajAuto updater = new SkillMajAuto(app);
        if (!updater.enabled() || BuildConfig.DEBUG) return Result.success();
        SkillMajAuto.Result result = updater.check();
        if (result.release == null) return Result.success();
        String version = result.release.version;
        android.content.SharedPreferences state =
                app.getSharedPreferences("xdebt_skillmajauto_notifications", Context.MODE_PRIVATE);
        if (!version.equals(state.getString("notified", ""))) {
            notifyAvailable(app, version, false);
            state.edit().putString("notified", version).apply();
        }
        return Result.success();
    }
}
