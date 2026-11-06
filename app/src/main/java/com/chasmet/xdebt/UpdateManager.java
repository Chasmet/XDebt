package com.chasmet.xdebt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * XDebt self-updater for GitHub-installed APKs.
 *
 * Release APKs must be signed with the SAME permanent certificate as the
 * installed APK, have the SAME applicationId and a HIGHER versionCode.
 * Never delete app data or uninstall to perform an upgrade.
 *
 * Google Play installations are updated through the Play Store instead.
 * Android always requires user confirmation for package installation.
 */
public final class UpdateManager {
    private static final String API =
            "https://api.github.com/repos/Chasmet/XDebt/releases/latest";
    private static final String PREF_AUTO = "automatic_update_checks";
    private static final String PREF_DOWNLOAD_ID = "update_download_id";
    private static final String PREF_DOWNLOAD_PATH = "update_download_path";
    private static final String PREF_RELEASE_VERSION = "update_release_version";

    private final MainActivity activity;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BroadcastReceiver receiver;
    private boolean checking;
    private boolean installLaunched;

    private static final class Release {
        String version;
        String apkUrl;
    }

    public UpdateManager(MainActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("xdebt_updates", Context.MODE_PRIVATE);
    }

    public boolean automaticChecksEnabled() {
        return preferences.getBoolean(PREF_AUTO, true);
    }

    public void setAutomaticChecksEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_AUTO, enabled).apply();
    }

    private void alert(String title, String message) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        new AlertDialog.Builder(activity)
                .setTitle(title).setMessage(message)
                .setPositiveButton("Fermer", null).show();
    }

    private static int compareVersion(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("[.\\-]");
        String[] b = right.replaceFirst("^[vV]", "").split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            long x = i < a.length ? versionNumber(a[i]) : 0L;
            long y = i < b.length ? versionNumber(b[i]) : 0L;
            int comparison = Long.compare(x, y);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static long versionNumber(String token) {
        try {
            if (!token.matches("[0-9]{1,12}")) return 0;
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String getReleaseApk(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "");
            String uri = asset.optString("browser_download_url", "");
            if (name.matches("(?i)^XDebt[-_].+\\.apk$") &&
                    uri.startsWith("https://github.com/Chasmet/XDebt/releases/download/")) {
                return uri;
            }
        }
        return null;
    }

    public void check(boolean manual) {
        if (checking || executor.isShutdown()) return;
        checking = true;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            String error = null;
            Release found = null;
            try {
                connection = (HttpURLConnection) new URL(API).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "XDebt-Android-Updater");
                int status = connection.getResponseCode();
                if (status == 404) {
                    error = "Aucune version signée publiée sur GitHub pour le moment.";
                } else if (status != 200) {
                    error = "GitHub a répondu : " + status;
                } else {
                    StringBuilder body = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            connection.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (body.length() + line.length() > 250000) {
                                throw new IllegalStateException("Réponse GitHub trop grande.");
                            }
                            body.append(line);
                        }
                    }
                    JSONObject json = new JSONObject(body.toString());
                    if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                        error = "Aucune version stable disponible.";
                    } else {
                        String tag = json.optString("tag_name", "");
                        if (tag.isEmpty()) throw new IllegalStateException("Version absente.");
                        if (compareVersion(tag, BuildConfig.VERSION_NAME) > 0) {
                            String apk = getReleaseApk(json.optJSONArray("assets"));
                            if (apk == null) {
                                error = "Version " + tag + " publiée sans APK installable.";
                            } else {
                                found = new Release();
                                found.version = tag;
                                found.apkUrl = apk;
                            }
                        } else if (manual) {
                            error = "XDebt est à jour : " + BuildConfig.VERSION_NAME;
                        }
                    }
                }
            } catch (Exception e) {
                error = "Vérification impossible : " + e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
                checking = false;
            }

            Release release = found;
            String info = error;
            activity.runOnUiThread(() -> {
                if (release != null) {
                    if (activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                            .setTitle("XDebt " + release.version)
                            .setMessage("Une mise à jour est disponible. Elle conservera vos données " +
                                    "uniquement si l'APK possède le même certificat de signature. " +
                                    "L'installation demande votre confirmation Android.")
                            .setNegativeButton("Plus tard", null)
                            .setPositiveButton("Télécharger", (dialog, which) -> download(release))
                            .show();
                } else if (manual && info != null) {
                    alert("Mises à jour", info);
                }
            });
        });
    }

    private void download(Release release) {
        try {
            File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) throw new IllegalStateException("Stockage Android inaccessible.");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Impossible de préparer le dossier de téléchargements.");
            }
            File file = new File(directory, "XDebt-update-" +
                    release.version.replaceAll("[^0-9A-Za-z._-]", "") + ".apk");
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("Impossible de remplacer l'ancien téléchargement.");
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apkUrl));
            request.setTitle("XDebt " + release.version);
            request.setDescription("Mise à jour de XDebt");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity,
                    Environment.DIRECTORY_DOWNLOADS, file.getName());
            DownloadManager manager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Gestionnaire de téléchargement absent.");
            long id = manager.enqueue(request);
            preferences.edit()
                    .putLong(PREF_DOWNLOAD_ID, id)
                    .putString(PREF_DOWNLOAD_PATH, file.getAbsolutePath())
                    .putString(PREF_RELEASE_VERSION, release.version)
                    .apply();
            installLaunched = false;
            listen();
            Toast.makeText(activity, "Téléchargement de XDebt lancé.", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            alert("Mise à jour impossible", error.getMessage());
        }
    }

    private void listen() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long expected = preferences.getLong(PREF_DOWNLOAD_ID, -1L);
                if (expected < 0 || intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -2L) != expected) return;
                resumePendingInstall();
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
    }

    public void resumePendingInstall() {
        long id = preferences.getLong(PREF_DOWNLOAD_ID, -1L);
        String path = preferences.getString(PREF_DOWNLOAD_PATH, null);
        if (id < 0 || path == null) return;
        listen();
        DownloadManager manager =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return;
        try (android.database.Cursor cursor =
                     manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                clear();
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                File apk = new File(path);
                if (!apk.isFile() || apk.length() == 0) {
                    clear();
                    alert("Téléchargement incomplet", "Le fichier APK est absent.");
                    return;
                }
                if (!installLaunched) install(apk);
            } else if (status == DownloadManager.STATUS_FAILED) {
                clear();
                alert("Échec du téléchargement", "Relance la vérification des mises à jour.");
            }
        } catch (Exception e) {
            alert("Téléchargement", e.getMessage());
        }
    }

    private static Signature[] signatures(PackageInfo packageInfo) {
        if (packageInfo == null) return new Signature[0];
        if (Build.VERSION.SDK_INT >= 28 && packageInfo.signingInfo != null) {
            return packageInfo.signingInfo.hasMultipleSigners()
                    ? packageInfo.signingInfo.getApkContentsSigners()
                    : packageInfo.signingInfo.getSigningCertificateHistory();
        }
        return packageInfo.signatures == null ? new Signature[0] : packageInfo.signatures;
    }

    private boolean compatibleApk(File file) {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (archive == null || !activity.getPackageName().equals(archive.packageName)) {
            alert("APK non compatible", "Le téléchargement ne correspond pas à XDebt.");
            return false;
        }
        int currentCode = BuildConfig.VERSION_CODE;
        long newCode = Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode() : archive.versionCode;
        if (newCode <= currentCode) {
            alert("Version incompatible", "Cette version n'est pas plus récente que la version installée.");
            return false;
        }
        PackageInfo installed;
        try {
            installed = pm.getPackageInfo(activity.getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException e) {
            alert("Mise à jour impossible", "Application installée introuvable.");
            return false;
        }
        Signature[] oldKeys = signatures(installed);
        Signature[] newKeys = signatures(archive);
        if (oldKeys.length == 0 || newKeys.length == 0 ||
                !Arrays.equals(oldKeys, newKeys)) {
            alert("Signature différente",
                    "Cette version n'a pas la même clé de signature que l'application installée. " +
                            "Pour protéger tes données, XDebt refuse de remplacer l'application. " +
                            "Ne la désinstalle pas sans sauvegarde.");
            return false;
        }
        return true;
    }

    private void install(File file) {
        if (!compatibleApk(file)) {
            // Keep file for troubleshooting; never uninstall or erase user data.
            clear();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 &&
                !activity.getPackageManager().canRequestPackageInstalls()) {
            alert("Autorisation Android",
                    "Autorise XDebt à installer les mises à jour depuis cette source. " +
                            "Reviens ensuite dans l'application et relance la mise à jour.");
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            permission.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permission);
            return;
        }
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".updates", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installLaunched = true;
            activity.startActivity(intent);
            // Retain pending metadata until installation succeeds; returning
            // to the app can retry without deleting the APK or local data.
        } catch (Exception e) {
            installLaunched = false;
            alert("Installation impossible", e.getMessage());
        }
    }

    private void clear() {
        preferences.edit()
                .remove(PREF_DOWNLOAD_ID)
                .remove(PREF_DOWNLOAD_PATH)
                .remove(PREF_RELEASE_VERSION).apply();
        installLaunched = false;
    }

    public void destroy() {
        if (receiver != null) {
            try {
                activity.unregisterReceiver(receiver);
            } catch (Exception ignored) { }
            receiver = null;
        }
        executor.shutdownNow();
    }
}
