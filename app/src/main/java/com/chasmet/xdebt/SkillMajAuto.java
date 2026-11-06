package com.chasmet.xdebt;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * SkillMajAuto for official XDebt releases. Never uninstalls or clears user data.
 * A release must have the same application ID, a greater versionCode and the
 * identical signing certificate; Android must explicitly approve installation.
 */
public final class SkillMajAuto {
    private static final String LATEST =
            "https://api.github.com/repos/Chasmet/XDebt/releases/latest";
    private static final String ASSETS =
            "https://github.com/Chasmet/XDebt/releases/download/";
    private static final String PREFS = "xdebt_skillmajauto";
    private final Context context;
    private final SharedPreferences prefs;

    public static final class Release {
        public final String version;
        public final String url;
        public final String fileName;
        Release(String version, String url, String fileName) {
            this.version = version; this.url = url; this.fileName = fileName;
        }
    }

    public static final class Result {
        public final String message;
        public final Release release;
        Result(String message, Release release) {
            this.message = message; this.release = release;
        }
    }

    public SkillMajAuto(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean enabled() { return prefs.getBoolean("auto", true); }
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto", enabled).apply();
        if (enabled && !BuildConfig.DEBUG) SkillMajAutoWorker.schedule(context);
        else SkillMajAutoWorker.cancel(context);
    }

    public static boolean newer(String remote, String local) {
        if (remote == null || local == null ||
                !remote.matches("[0-9]+\\.[0-9]+\\.[0-9]+") ||
                !local.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        String[] r = remote.split("\\."), l = local.split("\\.");
        for (int i = 0; i < 3; i++) {
            try {
                long a = Long.parseLong(r[i]), b = Long.parseLong(l[i]);
                if (a != b) return a > b;
            } catch (NumberFormatException ex) { return false; }
        }
        return false;
    }

    public Result check() {
        if (BuildConfig.DEBUG) return new Result(
            "SkillMajAuto est installé. Les mises à jour en place ne sont disponibles "
            + "que pour les versions XDebt officielles signées, pas pour XDebt (Test).", null);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LATEST).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "XDebt-SkillMajAuto/1.0");
            int status = connection.getResponseCode();
            if (status == 404) return new Result(
                    "Aucune Release signée n'a encore été publiée pour XDebt.", null);
            if (status != 200) return new Result("GitHub HTTP " + status, null);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > 250000)
                        throw new IllegalStateException("Réponse GitHub trop volumineuse");
                    output.write(buffer, 0, read);
                }
            }
            JSONObject json = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            if (json.optBoolean("draft") || json.optBoolean("prerelease"))
                return new Result("Pas de Release stable.", null);
            String version = json.optString("tag_name", "").replaceFirst("^[vV]", "");
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
                return new Result("Version de Release incorrecte.", null);
            if (!newer(version, BuildConfig.VERSION_NAME))
                return new Result("Version à jour : " + BuildConfig.VERSION_NAME, null);

            String name = "XDebt-" + version + ".apk";
            String prefix = ASSETS + "v" + version + "/";
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String assetName = asset.optString("name");
                String url = asset.optString("browser_download_url");
                if (name.equals(assetName) && (prefix + name).equals(url))
                    return new Result("Mise à jour " + version + " disponible.",
                            new Release(version, url, name));
            }
            return new Result("Release " + version + " : APK officiel signé absent.", null);
        } catch (Exception ex) {
            return new Result("Vérification impossible : " + ex.getClass().getSimpleName(), null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public long download(Release release) {
        if (release == null ||
                !release.fileName.equals("XDebt-" + release.version + ".apk") ||
                !release.url.equals(ASSETS + "v" + release.version + "/" + release.fileName))
            throw new IllegalArgumentException("Lien de mise à jour non autorisé");
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) throw new IllegalStateException("Stockage indisponible");
        if (!directory.exists() && !directory.mkdirs())
            throw new IllegalStateException("Dossier de téléchargement inaccessible");
        DownloadManager manager = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("DownloadManager indisponible");
        long previous = prefs.getLong("download_id", -1L);
        if (previous >= 0) manager.remove(previous);
        File target = new File(directory, release.fileName);
        if (target.exists() && !target.delete())
            throw new IllegalStateException("Ancien APK impossible à remplacer");
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.url));
        request.setTitle("XDebt " + release.version);
        request.setDescription("SkillMajAuto · mise à jour officielle");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(context,
                Environment.DIRECTORY_DOWNLOADS, release.fileName);
        long id = manager.enqueue(request);
        prefs.edit().putLong("download_id", id).putString("file", release.fileName).apply();
        return id;
    }

    public boolean ownsDownload(long id) {
        return id >= 0 && id == prefs.getLong("download_id", -1L);
    }

    public boolean hasPendingDownload() {
        return prefs.getLong("download_id", -1L) >= 0;
    }

    public void clearPending() {
        prefs.edit().remove("download_id").remove("file").apply();
    }

    public File verifiedDownload() {
        long id = prefs.getLong("download_id", -1L);
        if (id < 0) return null;
        DownloadManager manager = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return null;
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            if (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    != DownloadManager.STATUS_SUCCESSFUL) return null;
            String name = prefs.getString("file", "");
            if (!name.matches("XDebt-[0-9]+\\.[0-9]+\\.[0-9]+\\.apk")) return null;
            File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) return null;
            File file = new File(directory, name).getCanonicalFile();
            if (!file.getParentFile().equals(directory.getCanonicalFile()) ||
                    !file.isFile() || file.length() < 10000) return null;
            return compatible(file) ? file : null;
        } catch (Exception ignored) { return null; }
    }

    @SuppressWarnings("deprecation")
    public boolean compatible(File file) {
        if (BuildConfig.DEBUG) return false;
        try {
            PackageManager pm = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            PackageInfo candidate = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
            if (candidate == null || !context.getPackageName().equals(candidate.packageName))
                return false;
            long current = Build.VERSION.SDK_INT >= 28 ?
                    installed.getLongVersionCode() : installed.versionCode;
            long next = Build.VERSION.SDK_INT >= 28 ?
                    candidate.getLongVersionCode() : candidate.versionCode;
            if (next <= current) return false;
            Signature[] oldSignatures = signatures(installed);
            Signature[] newSignatures = signatures(candidate);
            return oldSignatures != null && newSignatures != null &&
                    oldSignatures.length > 0 &&
                    Arrays.equals(oldSignatures, newSignatures);
        } catch (Exception ignored) { return false; }
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28)
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        return info.signatures;
    }

    public void install(File apk, Activity activity) {
        if (apk == null || !compatible(apk))
            throw new IllegalStateException(
                    "APK refusé : signature, package ou version incompatible. "
                    + "Aucune donnée n'a été supprimée.");
        if (Build.VERSION.SDK_INT >= 26 &&
                !context.getPackageManager().canRequestPackageInstalls()) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())));
            return;
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".updates", apk);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
    }
}
