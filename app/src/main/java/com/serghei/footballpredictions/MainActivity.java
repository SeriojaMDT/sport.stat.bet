package com.serghei.footballpredictions;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int NAVY = Color.rgb(12, 36, 58);
    static final int NAVY2 = Color.rgb(18, 58, 88);
    static final int GREEN = Color.rgb(22, 163, 74);
    static final int GREEN_DARK = Color.rgb(21, 128, 61);
    static final int RED = Color.rgb(220, 38, 38);
    static final int AMBER = Color.rgb(217, 119, 6);
    static final int BG = Color.rgb(244, 247, 251);
    static final int CARD = Color.WHITE;
    static final int TEXT = Color.rgb(15, 23, 42);
    static final int MUTED = Color.rgb(100, 116, 139);
    static final int LINE = Color.rgb(226, 232, 240);
    static final int SOFT_GREEN = Color.rgb(236, 253, 245);
    static final int SOFT_BLUE = Color.rgb(239, 246, 255);

    SharedPreferences sp;
    LinearLayout page;
    TextView title, sub;
    final TextView[] navViews = new TextView[4];
    boolean admin = false;
    int currentTab = 0;

    final ArrayList<P> ps = new ArrayList<>();
    final ArrayList<F> fs = new ArrayList<>();
    final ExecutorService ex = Executors.newFixedThreadPool(3);

    String selectedDate = isoToday();
    String loadedDate = "";
    String loadedSource = "";
    int loadedCount = 0;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("fp", 0);
        load();

        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        View root = shell();
        setContentView(root);
        applySafeInsets(root);
        home();
    }

    @Override
    public void onDestroy() {
        ex.shutdownNow();
        super.onDestroy();
    }

    void applySafeInsets(View root) {
        if (Build.VERSION.SDK_INT >= 20) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(0, top, 0, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    View shell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(d(20), d(15), d(20), d(18));
        GradientDrawable hg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY, NAVY2});
        hg.setCornerRadii(new float[]{0,0,0,0,d(22),d(22),d(22),d(22)});
        header.setBackground(hg);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ball = t("●", 13, Color.rgb(134,239,172), true);
        brandRow.addView(ball);
        TextView brand = t("  PREDICȚII FOTBAL", 12, Color.rgb(187,247,208), true);
        brand.setLetterSpacing(.06f);
        brandRow.addView(brand);
        header.addView(brandRow);

        title = t("Predicții", 26, Color.WHITE, true);
        header.addView(title, mt(8, -2));

        sub = t("Analize personale și rezultate", 13, Color.rgb(203,213,225), false);
        header.addView(sub, mt(2, -2));

        root.addView(header);

        FrameLayout frame = new FrameLayout(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(d(6), d(5), d(6), d(5));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(d(10));

        String[] labels = {"⌂\nAcasă", "⚽\nMeciuri", "▥\nStatistici", "⚙\nAdmin"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView v = t(labels[i], 12, MUTED, false);
            v.setGravity(Gravity.CENTER);
            v.setPadding(d(4), d(6), d(4), d(5));
            v.setOnClickListener(x -> {
                if (idx == 0) home();
                else if (idx == 1) fixtures();
                else if (idx == 2) stats();
                else admin();
            });
            navViews[i] = v;
            nav.addView(v, new LinearLayout.LayoutParams(0, d(62), 1));
        }
        root.addView(nav);
        return root;
    }

    void selectNav(int idx) {
        currentTab = idx;
        for (int i = 0; i < navViews.length; i++) {
            if (navViews[i] == null) continue;
            navViews[i].setTextColor(i == idx ? GREEN_DARK : MUTED);
            navViews[i].setTypeface(Typeface.DEFAULT, i == idx ? Typeface.BOLD : Typeface.NORMAL);
            navViews[i].setBackground(i == idx ? rounded(SOFT_GREEN, 14) : rounded(Color.TRANSPARENT, 14));
        }
    }

    LinearLayout body(String a, String b, int tab) {
        selectNav(tab);
        title.setText(a);
        sub.setText(b);
        page.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(d(16), d(16), d(16), d(28));
        scroll.addView(x);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        return x;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(d(16), d(16), d(16), d(16));
        c.setBackground(rounded(CARD, 18));
        c.setElevation(d(3));
        return c;
    }

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(d(radius));
        return g;
    }

    GradientDrawable bordered(int color, int stroke, int radius) {
        GradientDrawable g = rounded(color, radius);
        g.setStroke(d(1), stroke);
        return g;
    }

    TextView t(String s, int z, int c, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(c);
        v.setLineSpacing(0, 1.08f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    TextView chip(String text, int fg, int bg) {
        TextView v = t(text, 11, fg, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(d(10), d(6), d(10), d(6));
        v.setBackground(rounded(bg, 20));
        return v;
    }

    Button pri(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(GREEN, 14));
        b.setStateListAnimator(null);
        return b;
    }

    Button sec(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(NAVY);
        b.setBackground(bordered(Color.WHITE, LINE, 14));
        b.setStateListAnimator(null);
        return b;
    }

    EditText in(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(148,163,184));
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(d(12), d(10), d(12), d(10));
        e.setBackground(bordered(Color.rgb(248,250,252), LINE, 12));
        return e;
    }

    int d(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
    }

    LinearLayout.LayoutParams mt(int top, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height);
        p.topMargin = d(top);
        return p;
    }

    void home() {
        LinearLayout b = body("Predicții", "Selecțiile și rezultatele tale", 0);

        int won = 0, lost = 0, pending = 0;
        for (P x : ps) {
            if ("WON".equals(x.status)) won++;
            else if ("LOST".equals(x.status)) lost++;
            else pending++;
        }

        LinearLayout summary = card();
        summary.addView(t("Panoul meu", 20, TEXT, true));
        summary.addView(t("Publică din Admin, apoi urmărește rezultatele aici.", 13, MUTED, false), mt(5,-2));

        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        TextView c1 = chip("✓ " + won + " câștigate", GREEN_DARK, SOFT_GREEN);
        TextView c2 = chip("• " + pending + " în așteptare", AMBER, Color.rgb(255,247,237));
        chips.addView(c1, new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,-2,1);
        cp.leftMargin = d(8);
        chips.addView(c2, cp);
        summary.addView(chips, mt(14,-2));

        Button refresh = pri("Actualizează rezultatele");
        refresh.setOnClickListener(v -> refresh());
        summary.addView(refresh, mt(14,d(50)));
        b.addView(summary);

        if (ps.isEmpty()) {
            LinearLayout empty = card();
            TextView icon = t("⚽", 34, GREEN, false);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon);
            TextView e = t("Nu ai încă predicții publicate.", 16, TEXT, true);
            e.setGravity(Gravity.CENTER);
            empty.addView(e, mt(8,-2));
            TextView e2 = t("Intră în Admin, încarcă meciurile pentru o zi și alege un meci.", 13, MUTED, false);
            e2.setGravity(Gravity.CENTER);
            empty.addView(e2, mt(5,-2));
            b.addView(empty, mt(12,-2));
            return;
        }

        ArrayList<P> q = new ArrayList<>(ps);
        Collections.sort(q, (a,c) -> Long.compare(c.created, a.created));
        for (P p : q) b.addView(predCard(p), mt(12,-2));
    }

    View predCard(P p) {
        LinearLayout c = card();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(t(p.league.isEmpty() ? "Fotbal" : p.league, 12, MUTED, true),
                new LinearLayout.LayoutParams(0,-2,1));

        int cc = "WON".equals(p.status) ? GREEN_DARK : "LOST".equals(p.status) ? RED : AMBER;
        int cbg = "WON".equals(p.status) ? SOFT_GREEN : "LOST".equals(p.status)
                ? Color.rgb(254,242,242) : Color.rgb(255,247,237);
        row.addView(chip(status(p.status), cc, cbg));
        c.addView(row);

        c.addView(t(p.home + "  —  " + p.away, 19, TEXT, true), mt(10,-2));
        c.addView(t(p.date, 13, MUTED, false), mt(4,-2));

        LinearLayout marketRow = new LinearLayout(this);
        marketRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView m = chip(p.market, Color.WHITE, GREEN);
        marketRow.addView(m);
        if (!p.score.isEmpty()) {
            TextView sc = chip("Scor: " + p.score, NAVY, SOFT_BLUE);
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(-2,-2);
            xp.leftMargin = d(8);
            marketRow.addView(sc, xp);
        }
        c.addView(marketRow, mt(12,-2));

        if (!p.note.isEmpty()) {
            TextView note = t(p.note, 13, MUTED, false);
            note.setPadding(d(1),0,d(1),0);
            c.addView(note, mt(10,-2));
        }
        if (!p.finalScore.isEmpty()) {
            c.addView(t("Rezultat final: " + p.finalScore, 13, cc, true), mt(10,-2));
        }

        c.setOnLongClickListener(v -> {
            if (admin) manage(p);
            else Toast.makeText(this, "Intră în Admin pentru modificare.", Toast.LENGTH_SHORT).show();
            return true;
        });
        return c;
    }

    void fixtures() {
        LinearLayout b = body("Meciuri", loadedDate.isEmpty()
                ? "Încarcă meciurile din cabinetul Admin"
                : "Meciuri încărcate pentru " + prettyDate(loadedDate), 1);

        LinearLayout info = card();
        info.addView(t("Programul meciurilor", 20, TEXT, true));
        if (loadedDate.isEmpty()) {
            info.addView(t("Nu am încărcat încă o zi. Pentru a economisi trafic, meciurile se descarcă doar când apeși butonul din Admin.", 13, MUTED, false), mt(6,-2));
            Button go = pri(admin ? "Deschide Admin" : "Logare Admin");
            go.setOnClickListener(v -> admin());
            info.addView(go, mt(14,d(50)));
            b.addView(info);
            return;
        }

        info.addView(t(loadedCount + " meciuri • " + loadedSource, 13, MUTED, false), mt(5,-2));
        b.addView(info);

        for (F f : new ArrayList<>(fs)) {
            LinearLayout c = fixtureCard(f, admin);
            b.addView(c, mt(10,-2));
        }
    }

    LinearLayout fixtureCard(F f, boolean withButton) {
        LinearLayout c = card();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(t(f.league.isEmpty() ? "Fotbal" : f.league, 12, MUTED, true),
                new LinearLayout.LayoutParams(0,-2,1));
        top.addView(chip(f.timeOnly(), NAVY, SOFT_BLUE));
        c.addView(top);

        c.addView(t(f.home, 17, TEXT, true), mt(10,-2));
        TextView vs = t("vs", 11, MUTED, true);
        c.addView(vs, mt(2,-2));
        c.addView(t(f.away, 17, TEXT, true), mt(1,-2));

        if (withButton) {
            Button use = sec("Alege pentru predicție");
            use.setOnClickListener(v -> create(f));
            c.addView(use, mt(12,d(46)));
        }
        return c;
    }

    void stats() {
        LinearLayout b = body("Statistici", "Performanța predicțiilor tale", 2);
        int w = 0, l = 0, p = 0;
        for (P x : ps) {
            if ("WON".equals(x.status)) w++;
            else if ("LOST".equals(x.status)) l++;
            else p++;
        }
        int settled = w + l;
        double rate = settled == 0 ? 0 : w * 100.0 / settled;

        LinearLayout hero = card();
        TextView r = t(String.format(Locale.US, "%.1f%%", rate), 44, GREEN_DARK, true);
        r.setGravity(Gravity.CENTER);
        hero.addView(t("Rată de succes", 18, TEXT, true));
        hero.addView(r, mt(16,-2));
        TextView z = t("din predicțiile încheiate", 13, MUTED, false);
        z.setGravity(Gravity.CENTER);
        hero.addView(z);
        b.addView(hero);

        LinearLayout numbers = new LinearLayout(this);
        numbers.setGravity(Gravity.CENTER);
        numbers.addView(statBox("Total", ps.size(), NAVY), new LinearLayout.LayoutParams(0,d(92),1));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,d(92),1);
        np.leftMargin = d(8);
        numbers.addView(statBox("Câștigate", w, GREEN_DARK), np);
        LinearLayout.LayoutParams np2 = new LinearLayout.LayoutParams(0,d(92),1);
        np2.leftMargin = d(8);
        numbers.addView(statBox("Pierdute", l, RED), np2);
        b.addView(numbers, mt(12,-2));

        LinearLayout pendingCard = card();
        pendingCard.addView(t("În așteptare", 14, MUTED, true));
        pendingCard.addView(t(String.valueOf(p), 30, AMBER, true), mt(5,-2));
        b.addView(pendingCard, mt(12,-2));
    }

    LinearLayout statBox(String label, int value, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(d(6),d(8),d(6),d(8));
        box.setBackground(rounded(Color.WHITE,16));
        box.setElevation(d(2));
        TextView n = t(String.valueOf(value), 24, color, true);
        n.setGravity(Gravity.CENTER);
        box.addView(n);
        TextView l = t(label, 11, MUTED, true);
        l.setGravity(Gravity.CENTER);
        box.addView(l, mt(3,-2));
        return box;
    }

    void admin() {
        if (!admin) {
            login();
            return;
        }

        LinearLayout b = body("Cabinet Admin", "Încarcă meciuri și publică predicții", 3);

        LinearLayout loader = card();
        LinearLayout adminRow = new LinearLayout(this);
        adminRow.setGravity(Gravity.CENTER_VERTICAL);
        adminRow.addView(t("Meciuri pentru predicții", 20, TEXT, true), new LinearLayout.LayoutParams(0,-2,1));
        adminRow.addView(chip("ADMIN", GREEN_DARK, SOFT_GREEN));
        loader.addView(adminRow);
        loader.addView(t("Alege ziua. Aplicația descarcă programul de pe internet doar când apeși „Încarcă meciurile”.", 13, MUTED, false), mt(6,-2));

        Button dateButton = sec("📅  " + prettyDate(selectedDate));
        dateButton.setOnClickListener(v -> openDatePicker(dateButton));
        loader.addView(dateButton, mt(14,d(50)));

        Button load = pri("Încarcă meciurile");
        load.setOnClickListener(v -> syncDate(selectedDate, true));
        loader.addView(load, mt(8,d(52)));

        if (!loadedDate.isEmpty()) {
            loader.addView(chip(loadedCount + " meciuri • " + loadedSource,
                    GREEN_DARK, SOFT_GREEN), mt(10,-2));
        }

        LinearLayout actions = new LinearLayout(this);
        Button manual = sec("+ Predicție manuală");
        manual.setOnClickListener(v -> create(null));
        actions.addView(manual, new LinearLayout.LayoutParams(0,d(46),1));
        Button out = sec("Ieșire");
        out.setOnClickListener(v -> { admin = false; login(); });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0,d(46),1);
        op.leftMargin = d(8);
        actions.addView(out, op);
        loader.addView(actions, mt(10,-2));
        b.addView(loader);

        if (!fs.isEmpty()) {
            EditText search = in("Caută echipă sau campionat");
            b.addView(search, mt(12,d(50)));

            LinearLayout matchList = new LinearLayout(this);
            matchList.setOrientation(LinearLayout.VERTICAL);
            b.addView(matchList);
            renderAdminFixtures(matchList, "");

            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                public void onTextChanged(CharSequence s,int st,int before,int count){
                    renderAdminFixtures(matchList, s.toString());
                }
                public void afterTextChanged(Editable e){}
            });
        } else {
            LinearLayout empty = card();
            TextView e = t("Alege o dată și apasă „Încarcă meciurile”.", 14, MUTED, false);
            e.setGravity(Gravity.CENTER);
            empty.addView(e);
            b.addView(empty, mt(12,-2));
        }

        if (!ps.isEmpty()) {
            TextView section = t("Predicții publicate", 17, TEXT, true);
            b.addView(section, mt(20,-2));
            ArrayList<P> q = new ArrayList<>(ps);
            Collections.sort(q, (a,c) -> Long.compare(c.created,a.created));
            for (P p : q) {
                LinearLayout x = card();
                x.addView(t(p.home + " — " + p.away, 15, TEXT, true));
                x.addView(t(p.market + "  •  " + status(p.status), 12, MUTED, false), mt(4,-2));
                Button m = sec("Gestionează");
                m.setOnClickListener(v -> manage(p));
                x.addView(m, mt(8,d(42)));
                b.addView(x, mt(9,-2));
            }
        }
    }

    void renderAdminFixtures(LinearLayout target, String query) {
        target.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (F f : new ArrayList<>(fs)) {
            String hay = (f.home + " " + f.away + " " + f.league).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            target.addView(fixtureCard(f, true), mt(9,-2));
            shown++;
        }
        if (shown == 0) {
            TextView none = t("Nu am găsit meciuri pentru filtrul acesta.", 13, MUTED, false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0,d(22),0,d(18));
            target.addView(none);
        }
    }

    void login() {
        LinearLayout b = body("Logare Admin", "Acces la cabinetul de predicții", 3);
        LinearLayout c = card();
        c.addView(t("Cabinet de administrator", 22, TEXT, true));
        c.addView(t("Autentificare locală pentru versiunea de test.", 13, MUTED, false), mt(5,-2));

        EditText e = in("E-mail");
        e.setText("admin@local.app");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        EditText p = in("Parolă");
        p.setText("123456");
        p.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        c.addView(e, mt(16,d(54)));
        c.addView(p, mt(9,d(54)));

        Button go = pri("LOGARE");
        go.setOnClickListener(v -> {
            if (e.getText().toString().trim().equals("admin@local.app")
                    && p.getText().toString().equals("123456")) {
                admin = true;
                admin();
            } else {
                Toast.makeText(this, "Date incorecte", Toast.LENGTH_SHORT).show();
            }
        });
        c.addView(go, mt(12,d(52)));

        TextView hint = t("Date test: admin@local.app / 123456", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        c.addView(hint, mt(10,-2));
        b.addView(c);
    }

    void openDatePicker(Button button) {
        Calendar c = Calendar.getInstance();
        try {
            Date x = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate);
            if (x != null) c.setTime(x);
        } catch (Exception ignored) {}

        DatePickerDialog dp = new DatePickerDialog(this, (view, y, m, day) -> {
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day);
            button.setText("📅  " + prettyDate(selectedDate));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    void create(F chosen) {
        LinearLayout b = body("Predicție nouă", "Completează selecția și publică", 3);
        LinearLayout c = card();

        if (chosen != null) {
            c.addView(chip(chosen.league, NAVY, SOFT_BLUE));
            c.addView(t(chosen.home + "  —  " + chosen.away, 20, TEXT, true), mt(10,-2));
            c.addView(t(chosen.date, 13, MUTED, false), mt(4,-2));
        } else {
            c.addView(t("Predicție manuală", 20, TEXT, true));
        }

        EditText league = in("Campionat");
        EditText home = in("Gazdă");
        EditText away = in("Vizitatoare");
        EditText date = in("Data și ora");

        if (chosen != null) {
            league.setText(chosen.league);
            home.setText(chosen.home);
            away.setText(chosen.away);
            date.setText(chosen.date);
        }

        c.addView(league, mt(14,d(52)));
        c.addView(home, mt(8,d(52)));
        c.addView(away, mt(8,d(52)));
        c.addView(date, mt(8,d(52)));

        String[] markets = {
                "1","X","2","1X","X2","12",
                "Peste 0.5","Sub 0.5",
                "Peste 1.5","Sub 1.5",
                "Peste 2.5","Sub 2.5",
                "Peste 3.5","Sub 3.5",
                "Peste 4.5","Sub 4.5",
                "Ambele marchează: DA","Ambele marchează: NU",
                "Scor exact"
        };
        Spinner mk = new Spinner(this);
        ArrayAdapter<String> marketAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, markets);
        mk.setAdapter(marketAdapter);
        mk.setBackground(bordered(Color.rgb(248,250,252), LINE, 12));
        c.addView(t("Piață", 13, TEXT, true), mt(14,-2));
        c.addView(mk, mt(5,d(52)));

        EditText sc = in("Scor estimat, ex. 2-1");
        EditText note = in("Comentariu / analiză");
        note.setSingleLine(false);
        note.setMinLines(3);
        note.setGravity(Gravity.TOP);
        c.addView(sc, mt(9,d(52)));
        c.addView(note, mt(9,d(96)));

        Button pub = pri("PUBLICĂ PREDICȚIA");
        pub.setOnClickListener(v -> {
            if (home.getText().toString().trim().isEmpty()
                    || away.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Completează echipele", Toast.LENGTH_SHORT).show();
                return;
            }

            P p = new P();
            p.id = UUID.randomUUID().toString();
            if (chosen != null) {
                p.event = chosen.event;
                p.source = chosen.source;
            }
            p.league = league.getText().toString().trim();
            p.home = home.getText().toString().trim();
            p.away = away.getText().toString().trim();
            p.date = date.getText().toString().trim();
            p.market = String.valueOf(mk.getSelectedItem());
            p.score = sc.getText().toString().trim();
            p.note = note.getText().toString().trim();
            p.status = "PENDING";
            p.created = System.currentTimeMillis();
            ps.add(p);
            save();
            Toast.makeText(this, "Predicția a fost publicată", Toast.LENGTH_SHORT).show();
            home();
        });
        c.addView(pub, mt(14,d(54)));
        b.addView(c);
    }

    void manage(P p) {
        String[] a = {
                "Marchează CÂȘTIGAT",
                "Marchează PIERDUT",
                "Marchează ÎN AȘTEPTARE",
                "Șterge"
        };
        new AlertDialog.Builder(this)
                .setTitle(p.home + " — " + p.away)
                .setItems(a, (dialog, which) -> {
                    if (which == 0) p.status = "WON";
                    else if (which == 1) p.status = "LOST";
                    else if (which == 2) {
                        p.status = "PENDING";
                        p.finalScore = "";
                    } else ps.remove(p);
                    save();
                    admin();
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    String status(String s) {
        return "WON".equals(s) ? "✓ CÂȘTIGAT"
                : "LOST".equals(s) ? "✕ PIERDUT"
                : "• ÎN AȘTEPTARE";
    }

    void syncDate(String iso, boolean show) {
        if (show) Toast.makeText(this, "Se încarcă meciurile pentru " + prettyDate(iso) + "…", Toast.LENGTH_SHORT).show();

        ex.execute(() -> {
            ArrayList<F> online = new ArrayList<>();
            String source = "";

            try {
                JSONObject j = get("https://www.sofascore.com/api/v1/sport/football/scheduled-events/" + iso);
                JSONArray e = j.optJSONArray("events");
                if (e != null) {
                    for (int i = 0; i < e.length(); i++) {
                        JSONObject o = e.optJSONObject(i);
                        if (o == null) continue;

                        JSONObject ht = o.optJSONObject("homeTeam");
                        JSONObject at = o.optJSONObject("awayTeam");
                        if (ht == null || at == null) continue;

                        String h = ht.optString("name");
                        String a = at.optString("name");
                        if (h.isEmpty() || a.isEmpty()) continue;

                        JSONObject tournament = o.optJSONObject("tournament");
                        String league = tournament == null ? "Fotbal" : tournament.optString("name", "Fotbal");

                        F f = new F();
                        f.event = String.valueOf(o.optLong("id", 0));
                        f.source = "sofa";
                        f.league = league;
                        f.home = h;
                        f.away = a;
                        long ts = o.optLong("startTimestamp", 0);
                        f.start = ts > 0 ? ts * 1000L : 0;
                        f.date = ts > 0 ? friendlyTimestamp(f.start) : prettyDate(iso);
                        online.add(f);
                    }
                }
                if (!online.isEmpty()) source = "SofaScore";
            } catch (Exception ignored) {}

            if (online.isEmpty()) {
                try {
                    JSONObject j = get("https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=" + iso + "&s=Soccer");
                    JSONArray e = j.optJSONArray("events");
                    if (e != null) {
                        for (int i = 0; i < e.length(); i++) {
                            JSONObject o = e.optJSONObject(i);
                            if (o == null) continue;
                            String h = o.optString("strHomeTeam");
                            String a = o.optString("strAwayTeam");
                            if (h.isEmpty() || a.isEmpty()) continue;

                            F f = new F();
                            f.event = o.optString("idEvent");
                            f.source = "tsdb";
                            f.league = o.optString("strLeague", "Fotbal");
                            f.home = h;
                            f.away = a;
                            f.date = friendly(o.optString("dateEvent"), o.optString("strTime"));
                            online.add(f);
                        }
                    }
                    if (!online.isEmpty()) source = "TheSportsDB";
                } catch (Exception ignored) {}
            }

            Collections.sort(online, (a,b) -> {
                if (a.start > 0 && b.start > 0) return Long.compare(a.start,b.start);
                int l = a.league.compareToIgnoreCase(b.league);
                if (l != 0) return l;
                return a.home.compareToIgnoreCase(b.home);
            });

            final String finalSource = source;
            runOnUiThread(() -> {
                fs.clear();
                fs.addAll(online);
                loadedDate = iso;
                loadedCount = online.size();
                loadedSource = finalSource.isEmpty() ? "sursă indisponibilă" : finalSource;

                if (show) {
                    if (online.isEmpty()) {
                        Toast.makeText(this, "Nu am putut încărca meciurile. Verifică internetul și încearcă din nou.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Încărcate " + online.size() + " meciuri.", Toast.LENGTH_SHORT).show();
                    }
                    admin();
                }
            });
        });
    }

    void refresh() {
        ArrayList<P> q = new ArrayList<>();
        for (P p : ps) {
            if ("PENDING".equals(p.status) && !p.event.isEmpty()) q.add(p);
        }
        if (q.isEmpty()) {
            Toast.makeText(this, "Nu există predicții online în așteptare.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Se verifică rezultatele…", Toast.LENGTH_SHORT).show();
        ex.execute(() -> {
            int changed = 0;

            for (P p : q) {
                try {
                    int h = -1, a = -1;
                    boolean finished = false;

                    if ("sofa".equals(p.source)) {
                        JSONObject j = get("https://www.sofascore.com/api/v1/event/" + p.event);
                        JSONObject o = j.optJSONObject("event");
                        if (o == null) continue;

                        JSONObject st = o.optJSONObject("status");
                        String type = st == null ? "" : st.optString("type");
                        finished = "finished".equalsIgnoreCase(type)
                                || "afterpenalties".equalsIgnoreCase(type)
                                || "afterextra".equalsIgnoreCase(type);

                        JSONObject hs = o.optJSONObject("homeScore");
                        JSONObject as = o.optJSONObject("awayScore");
                        if (hs != null && as != null) {
                            h = hs.has("normaltime") ? hs.optInt("normaltime", -1) : hs.optInt("current", -1);
                            a = as.has("normaltime") ? as.optInt("normaltime", -1) : as.optInt("current", -1);
                        }
                    } else {
                        JSONObject j = get("https://www.thesportsdb.com/api/v1/json/123/lookupevent.php?id=" + p.event);
                        JSONArray e = j.optJSONArray("events");
                        if (e == null || e.length() == 0) continue;
                        JSONObject o = e.getJSONObject(0);
                        String hs = o.optString("intHomeScore");
                        String as = o.optString("intAwayScore");
                        if (!hs.isEmpty() && !as.isEmpty() && !"null".equals(hs) && !"null".equals(as)) {
                            h = Integer.parseInt(hs);
                            a = Integer.parseInt(as);
                            finished = true;
                        }
                    }

                    if (!finished || h < 0 || a < 0) continue;
                    p.finalScore = h + "-" + a;
                    p.status = win(p.market, p.score, h, a) ? "WON" : "LOST";
                    changed++;
                } catch (Exception ignored) {}
            }

            if (changed > 0) save();
            int done = changed;
            runOnUiThread(() -> {
                Toast.makeText(this, "Rezultate actualizate: " + done, Toast.LENGTH_SHORT).show();
                home();
            });
        });
    }

    boolean win(String m, String s, int h, int a) {
        int total = h + a;
        if ("1".equals(m)) return h > a;
        if ("X".equals(m)) return h == a;
        if ("2".equals(m)) return a > h;
        if ("1X".equals(m)) return h >= a;
        if ("X2".equals(m)) return a >= h;
        if ("12".equals(m)) return h != a;
        if ("Peste 0.5".equals(m)) return total >= 1;
        if ("Sub 0.5".equals(m)) return total == 0;
        if ("Peste 1.5".equals(m)) return total >= 2;
        if ("Sub 1.5".equals(m)) return total <= 1;
        if ("Peste 2.5".equals(m)) return total >= 3;
        if ("Sub 2.5".equals(m)) return total <= 2;
        if ("Peste 3.5".equals(m)) return total >= 4;
        if ("Sub 3.5".equals(m)) return total <= 3;
        if ("Peste 4.5".equals(m)) return total >= 5;
        if ("Sub 4.5".equals(m)) return total <= 4;
        if ("Ambele marchează: DA".equals(m)) return h > 0 && a > 0;
        if ("Ambele marchează: NU".equals(m)) return h == 0 || a == 0;
        if ("Scor exact".equals(m)) {
            return s.replace(":","-").replace(" ","").equals(h + "-" + a);
        }
        return false;
    }

    JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 Android FootballPredictions/2.0");
        c.setRequestProperty("Accept", "application/json");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder s = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) s.append(line);
        r.close();
        return new JSONObject(s.toString());
    }

    String friendly(String date, String time) {
        try {
            String t = time == null ? "" : time.replace("Z","");
            if (t.length() > 5) t = t.substring(0,5);
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date x = input.parse(date + " " + (t.isEmpty() ? "12:00" : t));
            SimpleDateFormat out = new SimpleDateFormat("dd MMM yyyy • HH:mm", new Locale("ro","RO"));
            return out.format(x);
        } catch (Exception e) {
            return date + " " + time;
        }
    }

    String friendlyTimestamp(long millis) {
        return new SimpleDateFormat("dd MMM yyyy • HH:mm", new Locale("ro","RO"))
                .format(new Date(millis));
    }

    static String isoToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    String prettyDate(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date x = in.parse(iso);
            if (x == null) return iso;
            SimpleDateFormat out = new SimpleDateFormat("EEE, dd MMM yyyy", new Locale("ro","RO"));
            return out.format(x);
        } catch (Exception e) {
            return iso;
        }
    }

    void load() {
        try {
            JSONArray a = new JSONArray(sp.getString("predictions","[]"));
            for (int i=0;i<a.length();i++) ps.add(P.from(a.getJSONObject(i)));
        } catch (Exception ignored) {}
    }

    void save() {
        JSONArray a = new JSONArray();
        for (P p : ps) a.put(p.json());
        sp.edit().putString("predictions", a.toString()).apply();
    }

    static class F {
        String event = "", source = "", league = "", home = "", away = "", date = "";
        long start = 0;
        String key() {
            return event.isEmpty() ? league + home + away + date : source + ":" + event;
        }
        String timeOnly() {
            int idx = date.lastIndexOf("•");
            if (idx >= 0 && idx + 1 < date.length()) return date.substring(idx + 1).trim();
            return "";
        }
    }

    static class P {
        String id = "", event = "", source = "", league = "", home = "", away = "",
                date = "", market = "", score = "", note = "", status = "PENDING", finalScore = "";
        long created;

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("event", event);
                o.put("source", source);
                o.put("league", league);
                o.put("home", home);
                o.put("away", away);
                o.put("date", date);
                o.put("market", market);
                o.put("score", score);
                o.put("note", note);
                o.put("status", status);
                o.put("finalScore", finalScore);
                o.put("created", created);
            } catch (Exception ignored) {}
            return o;
        }

        static P from(JSONObject o) {
            P p = new P();
            p.id = o.optString("id");
            p.event = o.optString("event");
            p.source = o.optString("source");
            p.league = o.optString("league");
            p.home = o.optString("home");
            p.away = o.optString("away");
            p.date = o.optString("date");
            p.market = o.optString("market");
            p.score = o.optString("score");
            p.note = o.optString("note");
            p.status = o.optString("status","PENDING");
            p.finalScore = o.optString("finalScore");
            p.created = o.optLong("created");
            return p;
        }
    }
}
