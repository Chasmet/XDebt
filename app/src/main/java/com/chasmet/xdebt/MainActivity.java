package com.chasmet.xdebt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int BLACK = Color.rgb(7, 9, 11);
    private static final int PANEL = Color.rgb(21, 25, 29);
    private static final int TEXT = Color.rgb(242, 245, 247);
    private static final int MUTED = Color.rgb(157, 169, 180);
    private static final int CYAN = Color.rgb(0, 227, 233);
    private static final int PHOTO_REQUEST = 9001;
    private final NumberFormat euros = NumberFormat.getCurrencyInstance(Locale.FRANCE);
    private LinearLayout root, body;
    private ApiClient api;
    private JSONObject profile;
    private boolean profileOwner;
    private int screenVersion = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        root = findViewById(R.id.root_container);
        api = new ApiClient(this);
        if (api.base().isEmpty() || api.token().isEmpty()) showAuth();
        else showCommunity();
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private String money(long cents) {
        return euros.format(cents / 100d);
    }
    private GradientDrawable shape(int fill, int stroke, int round) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(round));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout panel() {
        LinearLayout panel = column();
        panel.setBackground(shape(PANEL, Color.rgb(40, 48, 55), 16));
        panel.setPadding(dp(15), dp(13), dp(15), dp(13));
        return panel;
    }
    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        parent.addView(view, p);
    }
    private Button button(String title, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinHeight(dp(44));
        b.setTextColor(primary ? BLACK : TEXT);
        b.setBackground(shape(primary ? CYAN : PANEL, primary ? Color.TRANSPARENT : Color.rgb(60, 69, 77), 13));
        b.setOnClickListener(click);
        return b;
    }
    private EditText field(String hint, int inputType) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setTextColor(TEXT);
        f.setHintTextColor(MUTED);
        f.setSingleLine(true);
        f.setTextSize(15);
        f.setInputType(inputType);
        f.setPadding(dp(12), dp(11), dp(12), dp(11));
        f.setBackground(shape(PANEL, Color.rgb(48, 56, 62), 10));
        return f;
    }
    private LinearLayout page(String title, boolean navigation) {
        ++screenVersion;
        root.removeAllViews();
        LinearLayout header = row();
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        TextView brand = text("XDebt", 26, TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(45), 1));
        TextView settings = text("⚙", 25, MUTED, false);
        settings.setPadding(dp(12), 0, dp(4), 0);
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings);
        add(root, header, 0);
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(36, 43, 49));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = column();
        body.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add(body, text(title, 23, TEXT, true), 0);
        if (navigation) {
            LinearLayout tabs = row();
            tabs.setPadding(dp(12), dp(9), dp(12), dp(10));
            nav(tabs, "Communauté", () -> showCommunity());
            nav(tabs, "Mes factures", () -> showProfile("", true));
            nav(tabs, "Profil", () -> showProfile("", true));
            root.addView(tabs, new LinearLayout.LayoutParams(-1, dp(62)));
        }
        return body;
    }
    private void nav(LinearLayout tabs, String label, Runnable action) {
        Button b = button(label, false, v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        tabs.addView(b, p);
    }
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    private void failure(String error) {
        if (error != null) toast("XDebt : " + error);
    }
    private void showAuth() {
        page("Votre espace XDebt", false);
        add(body, text("Connectez votre serveur XDebt et créez votre profil.", 14, MUTED, false), 10);
        EditText server = field("https://adresse-de-votre-serveur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(api.base());
        add(body, server, 20);
        add(body, button("Enregistrer l'adresse du serveur", false, v -> {
            try { api.setBase(server.getText().toString()); toast("Serveur enregistré"); }
            catch (Exception e) { toast(e.getMessage()); }
        }), 8);
        EditText name = field("Nom public", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = field("Adresse e-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = field("Mot de passe (10 caractères minimum)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(body, text("Créer un compte ou se connecter", 18, TEXT, true), 28);
        add(body, name, 14);
        add(body, email, 8);
        add(body, password, 8);
        add(body, text("Votre nom et votre profil seront publics. Les factures ne le seront que si vous les publiez.", 12, MUTED, false), 9);
        add(body, button("Créer un compte gratuit", true, v -> auth(true, server, name, email, password)), 18);
        add(body, button("J'ai déjà un compte", false, v -> auth(false, server, name, email, password)), 9);
    }
    private void auth(boolean register, EditText server, EditText name, EditText email, EditText password) {
        try { api.setBase(server.getText().toString()); }
        catch (Exception e) { toast(e.getMessage()); return; }
        JSONObject payload = new JSONObject();
        try {
            if (register) payload.put("display_name", name.getText().toString().trim());
            payload.put("email", email.getText().toString().trim());
            payload.put("password", password.getText().toString());
        } catch (Exception ignored) { return; }
        api.call("POST", register ? "/register" : "/login", payload, (response, error) -> {
            if (error != null) { failure(error); return; }
            api.setToken(response.optString("token"));
            showCommunity();
        });
    }
    private void showCommunity() {
        LinearLayout target = page("Communauté XDebt", true);
        int stamp = screenVersion;
        add(target, text("Des profils publics et une entraide transparente. Aucun paiement n'est disponible en V1.", 13, MUTED, false), 9);
        EditText search = field("Rechercher un profil...", InputType.TYPE_CLASS_TEXT);
        add(target, search, 18);
        LinearLayout results = column();
        add(target, results, 10);
        add(results, text("Chargement des profils...", 14, MUTED, false), 8);
        api.call("GET", "/profiles", null, (data, error) -> {
            if (stamp != screenVersion) return;
            if (error != null) { results.removeAllViews(); add(results, text(error, 14, MUTED, false), 10); return; }
            JSONArray profiles = data.optJSONArray("profiles");
            populateProfiles(results, profiles, "");
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    populateProfiles(results, profiles, s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        });
    }
    private void populateProfiles(LinearLayout target, JSONArray profiles, String filter) {
        target.removeAllViews();
        if (profiles == null) return;
        int count = 0;
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("display_name", "");
            if (!name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
            count++;
            LinearLayout card = panel();
            LinearLayout head = row();
            portrait(head, p, 50);
            LinearLayout details = column();
            details.setPadding(dp(12), 0, 0, 0);
            add(details, text(name, 17, TEXT, true), 0);
            add(details, text(p.optString("bio", "Profil XDebt"), 12, MUTED, false), 4);
            head.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            add(card, head, 0);
            add(card, text("Montant déclaré public : " + money(p.optLong("remaining_cents")) +
              "  ·  " + p.optInt("visible_invoice_count") + " facture(s)", 12, CYAN, false), 12);
            card.setOnClickListener(v -> showProfile(p.optString("id"), false));
            add(target, card, 10);
        }
        if (count == 0) add(target, text("Aucun profil trouvé.", 14, MUTED, false), 12);
    }
    private void portrait(LinearLayout parent, JSONObject p, int size) {
        String avatar = p.optString("avatar", "");
        if (avatar.startsWith("data:image/jpeg;base64,")) {
            try {
                byte[] bytes = Base64.decode(avatar.substring("data:image/jpeg;base64,".length()), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    ImageView image = new ImageView(this);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageBitmap(bitmap);
                    image.setBackground(shape(PANEL, Color.TRANSPARENT, 15));
                    image.setClipToOutline(true);
                    parent.addView(image, new LinearLayout.LayoutParams(dp(size), dp(size)));
                    return;
                }
            } catch (Exception ignored) { }
        }
        String name = p.optString("display_name", "X");
        TextView fallback = text(name.isEmpty() ? "X" : name.substring(0, 1).toUpperCase(Locale.ROOT), 25, CYAN, true);
        fallback.setGravity(Gravity.CENTER);
        fallback.setBackground(shape(Color.rgb(31, 39, 45), CYAN, 15));
        parent.addView(fallback, new LinearLayout.LayoutParams(dp(size), dp(size)));
    }
    private void showProfile(String id, boolean mine) {
        if (mine && api.token().isEmpty()) { showAuth(); return; }
        LinearLayout target = page(mine ? "Mes factures" : "Profil public", true);
        int stamp = screenVersion;
        add(target, text("Chargement du profil...", 14, MUTED, false), 15);
        api.call("GET", mine ? "/me" : "/profiles/" + id, null, (result, error) -> {
            if (stamp != screenVersion) return;
            if (error != null) {
                if (mine && error.contains("401")) { api.clearToken(); showAuth(); return; }
                failure(error); return;
            }
            profile = result.optJSONObject("profile");
            profileOwner = mine;
            renderProfile();
        });
    }
    private void renderProfile() {
        if (profile == null) return;
        page(profileOwner ? "Mes factures" : "Profil XDebt", true);
        LinearLayout card = panel();
        LinearLayout person = row();
        portrait(person, profile, 65);
        LinearLayout info = column();
        info.setPadding(dp(14), 0, 0, 0);
        add(info, text(profile.optString("display_name"), 23, TEXT, true), 0);
        add(info, text(profileOwner ? "Votre profil" : "Profil public", 13, CYAN, false), 6);
        person.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        add(card, person, 0);
        add(card, text(profile.optString("bio", ""), 13, MUTED, false), 12);
        add(card, text("Total déclaré : " + money(profile.optLong("total_cents")), 17, TEXT, true), 14);
        add(card, text("Factures restantes : " + money(profile.optLong("remaining_cents")), 13, MUTED, false), 6);
        add(body, card, 15);
        if (profileOwner) {
            add(body, button("Modifier mon profil", false, v -> editProfile()), 12);
            add(body, button("Changer ma photo de profil", false, v -> pickPhoto()), 8);
        }
        add(body, text("Dossiers de factures", 18, TEXT, true), 25);
        add(body, text("9 catégories fixes et 3 dossiers personnalisés au maximum.", 12, MUTED, false), 5);
        JSONArray folders = profile.optJSONArray("folders");
        if (folders == null) return;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = (dm.widthPixels - dp(54)) / 3;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject f = folders.optJSONObject(i);
            if (f == null) continue;
            LinearLayout tile = panel();
            tile.setPadding(dp(9), dp(12), dp(7), dp(9));
            TextView symbol = text(f.optBoolean("custom") ? "+" : folderSymbol(i), 24, CYAN, true);
            add(tile, symbol, 0);
            add(tile, text(f.optString("name"), 11, TEXT, true), 8);
            JSONArray invoices = f.optJSONArray("invoices");
            add(tile, text((invoices == null ? 0 : invoices.length()) + " facture(s)", 10, MUTED, false), 6);
            tile.setOnClickListener(v -> showFolder(f.optString("key")));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / 3), GridLayout.spec(i % 3));
            lp.width = width - dp(8);
            lp.height = dp(120);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            grid.addView(tile, lp);
        }
        add(body, grid, 10);
        if (profileOwner && folders.length() < 12)
            add(body, button("+ Créer un dossier personnel (" + (12 - folders.length()) + " restant(s))", true,
                v -> addCustomFolder()), 15);
        add(body, text("Les documents et montants privés ne sont jamais visibles sur les profils publics.", 12, MUTED, false), 18);
    }
    private String folderSymbol(int index) {
        String[] symbols = { "⚡", "◉", "△", "⌂", "≋", "+", "▣", "◇", "≡" };
        return symbols[Math.min(Math.max(index, 0), symbols.length - 1)];
    }
    private JSONObject getFolder(String key) {
        JSONArray folders = profile == null ? null : profile.optJSONArray("folders");
        if (folders != null) for (int i = 0; i < folders.length(); i++) {
            JSONObject f = folders.optJSONObject(i);
            if (f != null && key.equals(f.optString("key"))) return f;
        }
        return null;
    }
    private void showFolder(String key) {
        JSONObject folder = getFolder(key);
        if (folder == null) { toast("Dossier introuvable"); return; }
        page(folder.optString("name"), true);
        add(body, text("Total des factures : " + money(folder.optLong("total_cents")), 21, CYAN, true), 12);
        add(body, text("Aucun paiement ni don réel n'est encore possible.", 13, MUTED, false), 9);
        if (profileOwner) add(body, button("+ Ajouter une facture", true, v -> addInvoice(key)), 18);
        JSONArray invoices = folder.optJSONArray("invoices");
        add(body, text("Factures (" + (invoices == null ? 0 : invoices.length()) + ")", 19, TEXT, true), 25);
        if (invoices == null || invoices.length() == 0) {
            add(body, text("Ce dossier ne contient aucune facture visible.", 14, MUTED, false), 14);
            return;
        }
        for (int i = 0; i < invoices.length(); i++) {
            JSONObject inv = invoices.optJSONObject(i);
            if (inv == null) continue;
            LinearLayout card = panel();
            add(card, text(inv.optString("label"), 16, TEXT, true), 0);
            add(card, text(money(inv.optLong("amount_cents")), 19, CYAN, true), 8);
            String date = inv.optString("due_date", "");
            add(card, text((date.isEmpty() ? "Échéance non précisée" : "Échéance : " + date) +
                (inv.optBoolean("is_public") ? "  ·  Public" : "  ·  Privé"), 12, MUTED, false), 6);
            if (profileOwner) add(card, button("Supprimer", false, v -> confirmDelete(inv.optString("id"), key)), 10);
            add(body, card, 9);
        }
    }
    private void confirmDelete(String invoiceId, String folderKey) {
        new AlertDialog.Builder(this).setTitle("Supprimer cette facture ?")
            .setMessage("Cette action est définitive.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer", (d, w) -> api.call("DELETE", "/me/invoices/" + invoiceId, null, (result, error) -> {
                if (error != null) failure(error); else refreshFolder(folderKey);
            })).show();
    }
    private void refreshFolder(String key) {
        api.call("GET", "/me", null, (result, error) -> {
            if (error != null) { failure(error); return; }
            profile = result.optJSONObject("profile");
            profileOwner = true;
            showFolder(key);
        });
    }
    private void addCustomFolder() {
        EditText name = field("Exemple : Impôts", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout wrapper = column();
        wrapper.setPadding(dp(15), dp(10), dp(15), 0);
        wrapper.addView(name);
        new AlertDialog.Builder(this).setTitle("Dossier personnel").setView(wrapper)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Créer", (d, w) -> {
                JSONObject data = new JSONObject();
                try { data.put("name", name.getText().toString()); } catch (Exception ignored) { }
                api.call("POST", "/me/folders", data, (result, error) -> {
                    if (error != null) failure(error);
                    else { profile = result.optJSONObject("profile"); profileOwner = true; renderProfile(); }
                });
            }).show();
    }
    private void addInvoice(String folderKey) {
        LinearLayout form = column();
        form.setPadding(dp(16), dp(10), dp(16), 0);
        EditText label = field("Nom de la facture", InputType.TYPE_CLASS_TEXT);
        EditText amount = field("Montant en euros, ex. 129,90", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText date = field("Échéance AAAA-MM-JJ (facultative)", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        CheckBox publiclyVisible = new CheckBox(this);
        publiclyVisible.setText("Publier le titre, le montant et l'échéance");
        publiclyVisible.setTextColor(TEXT);
        publiclyVisible.setChecked(false);
        add(form, label, 0);
        add(form, amount, 9);
        add(form, date, 9);
        add(form, publiclyVisible, 9);
        new AlertDialog.Builder(this).setTitle("Nouvelle facture").setView(form)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", (d, w) -> {
                try {
                    long cents = new BigDecimal(amount.getText().toString().replace(',', '.'))
                        .multiply(new BigDecimal("100")).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
                    JSONObject invoice = new JSONObject();
                    invoice.put("folder_key", folderKey);
                    invoice.put("label", label.getText().toString());
                    invoice.put("amount_cents", cents);
                    invoice.put("due_date", date.getText().toString().trim());
                    invoice.put("is_public", publiclyVisible.isChecked());
                    api.call("POST", "/me/invoices", invoice, (result, error) -> {
                        if (error != null) failure(error); else refreshFolder(folderKey);
                    });
                } catch (Exception e) { toast("Montant invalide : utilisez par exemple 129,90"); }
            }).show();
    }
    private void editProfile() {
        LinearLayout form = column();
        form.setPadding(dp(16), dp(10), dp(16), 0);
        EditText name = field("Nom public", InputType.TYPE_CLASS_TEXT);
        name.setText(profile.optString("display_name", ""));
        EditText bio = field("Présentation", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bio.setSingleLine(false);
        bio.setText(profile.optString("bio", ""));
        add(form, name, 0);
        add(form, bio, 10);
        new AlertDialog.Builder(this).setTitle("Modifier le profil").setView(form)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", (d, w) -> {
                JSONObject payload = new JSONObject();
                try { payload.put("display_name", name.getText().toString()); payload.put("bio", bio.getText().toString()); }
                catch (Exception ignored) { }
                api.call("PUT", "/me", payload, (response, error) -> {
                    if (error != null) failure(error);
                    else { profile = response.optJSONObject("profile"); renderProfile(); }
                });
            }).show();
    }
    private void pickPhoto() {
        toast("Cette photo sera visible sur votre profil public.");
        Intent choose = new Intent(Intent.ACTION_GET_CONTENT);
        choose.setType("image/*");
        choose.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(choose, "Choisir votre photo"), PHOTO_REQUEST);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PHOTO_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream input = getContentResolver().openInputStream(data.getData())) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) { toast("Image impossible à lire"); return; }
            int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - side) / 2, (bitmap.getHeight() - side) / 2, side, side);
            Bitmap small = Bitmap.createScaledBitmap(square, 160, 160, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 72, out);
            String encoded = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            JSONObject payload = new JSONObject();
            payload.put("avatar", encoded);
            api.call("PUT", "/me", payload, (response, error) -> {
                if (error != null) failure(error);
                else { profile = response.optJSONObject("profile"); renderProfile(); }
            });
        } catch (Exception e) { toast("Photo trop volumineuse ou invalide"); }
    }
    private void showSettings() {
        page("Paramètres", false);
        EditText url = field("Adresse HTTPS du serveur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(api.base());
        add(body, text("Serveur XDebt", 18, TEXT, true), 15);
        add(body, url, 10);
        add(body, button("Enregistrer le serveur", true, v -> {
            try { api.setBase(url.getText().toString()); api.clearToken(); showAuth(); }
            catch (Exception e) { toast(e.getMessage()); }
        }), 12);
        add(body, text("L'application est gratuite. Aucun don ni paiement réel n'est activé.", 13, MUTED, false), 20);
        add(body, button("Rechercher une mise à jour", false, v -> checkRelease()), 15);
        add(body, button("Retour", false, v -> {
            if (api.token().isEmpty()) showAuth(); else showCommunity();
        }), 9);
        if (!api.token().isEmpty()) add(body, button("Se déconnecter", false, v -> {
            api.call("POST", "/logout", new JSONObject(), (r, e) -> {
                api.clearToken(); showAuth();
            });
        }), 9);
    }
    private void checkRelease() {
        new Thread(() -> {
            HttpURLConnection c = null;
            String found = "";
            String url = "";
            try {
                c = (HttpURLConnection) new URL("https://api.github.com/repos/Chasmet/XDebt/releases/latest").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) throw new Exception("Aucune Release disponible.");
                try (InputStream stream = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] chunk = new byte[2048];
                    int n;
                    while ((n = stream.read(chunk)) > 0 && out.size() < 35000) out.write(chunk, 0, n);
                    JSONObject json = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                    found = json.optString("tag_name", "");
                    url = json.optString("html_url", "");
                }
            } catch (Exception e) { found = ""; }
            finally { if (c != null) c.disconnect(); }
            String latest = found;
            String link = url;
            runOnUiThread(() -> {
                if (latest.isEmpty()) { toast("Aucune mise à jour publiée."); return; }
                boolean newer = releaseNumber(latest) > releaseNumber(BuildConfig.VERSION_NAME);
                new AlertDialog.Builder(this)
                    .setTitle(newer ? "Mise à jour disponible : " + latest : "XDebt est à jour")
                    .setMessage("Version installée : " + BuildConfig.VERSION_NAME +
                        "\nVersion GitHub : " + latest +
                        "\nPour les versions Google Play, les mises à jour passeront par Play Store.")
                    .setNegativeButton("Fermer", null)
                    .setPositiveButton("Voir la Release", (d, w) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))))
                    .show();
            });
        }).start();
    }
    private int releaseNumber(String version) {
        try { String[] parts = version.replaceFirst("^v", "").split("\\."); return Integer.parseInt(parts[parts.length - 1]); }
        catch (Exception e) { return -1; }
    }
    @Override protected void onDestroy() {
        api.shutdown();
        super.onDestroy();
    }
}
