package com.chasmet.xdebt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    public interface Callback {
        void done(JSONObject data, String error);
    }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public ApiClient(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("xdebt_config", Context.MODE_PRIVATE);
    }
    public String base() {
        return prefs.getString("base_url", "");
    }
    public String token() {
        return prefs.getString("auth_token", "");
    }
    public void setBase(String value) {
        String url = value.trim().replaceAll("/+$", "");
        if (!url.startsWith("https://") || url.length() < 12 || url.contains(" ") ||
                url.contains("?") || url.contains("#")) {
            throw new IllegalArgumentException("Une adresse HTTPS valide est obligatoire.");
        }
        prefs.edit().putString("base_url", url).apply();
    }
    public void setToken(String token) {
        prefs.edit().putString("auth_token", token).apply();
    }
    public void clearToken() {
        prefs.edit().remove("auth_token").apply();
    }
    public void call(String method, String path, JSONObject body, Callback callback) {
        final String baseUrl = base();
        final String bearer = token();
        executor.execute(() -> {
            JSONObject response = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                if (baseUrl.isEmpty()) throw new IllegalStateException("Configurez l'adresse du serveur.");
                connection = (HttpURLConnection) new URL(baseUrl + "/xdebt/api" + path).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "XDebt-Android/1");
                if (!bearer.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + bearer);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
                }
                int status = connection.getResponseCode();
                InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int n;
                        while ((n = input.read(buffer)) != -1) {
                            out.write(buffer, 0, n);
                            if (out.size() > 400000) throw new IllegalStateException("Réponse trop volumineuse.");
                        }
                        response = new JSONObject(out.toString("UTF-8"));
                    }
                } else response = new JSONObject();
                if (status >= 400) {
                    String code = response.optString("error", "HTTP " + status);
                    error = code.replace('_', ' ') + " (" + status + ")";
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Connexion impossible" : e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            JSONObject result = response;
            String problem = error;
            activity.runOnUiThread(() -> callback.done(result, problem));
        });
    }
    public void shutdown() {
        executor.shutdownNow();
    }
}
