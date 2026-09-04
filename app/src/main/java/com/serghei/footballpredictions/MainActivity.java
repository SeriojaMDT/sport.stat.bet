package com.serghei.footballpredictions;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    static final int NAVY = Color.rgb(10,34,55);
    static final int NAVY2 = Color.rgb(18,66,98);
    static final int BLUE = Color.rgb(2,132,199);
    static final int GREEN = Color.rgb(22,163,74);
    static final int RED = Color.rgb(220,38,38);
    static final int AMBER = Color.rgb(217,119,6);
    static final int PURPLE = Color.rgb(109,40,217);
    static final int TEXT = Color.rgb(15,23,42);
    static final int MUTED = Color.rgb(100,116,139);
    static final int LINE = Color.rgb(226,232,240);
    static final int BG = Color.rgb(244,247,251);
    static final int SOFT_BLUE = Color.rgb(239,246,255);
    static final int SOFT_GREEN = Color.rgb(236,253,245);
    static final int SOFT_RED = Color.rgb(254,242,242);
    static final int SOFT_AMBER = Color.rgb(255,247,237);
    static final int SOFT_PURPLE = Color.rgb(245,243,255);

    static final String INDEX_URL = "https://www.bestsportstats.com/p/bss2indexcurrent.html";
    static final String CONFIG_URL = "https://www.bestsportstats.com/p/bss2config.html";
    static final String LEGACY_INDEX_URL = "https://www.bestsportstats.com/p/bssgoalsindexselected2024.html";

    final ExecutorService executor = Executors.newFixedThreadPool(3);
    final ArrayList<LeagueSource> catalog = new ArrayList<>();
    LeagueData leagueData = null;
    AppConfig config = new AppConfig();

    LinearLayout root, content, header;
    TextView headerTitle, headerSubtitle, sourceStatus;
    ScrollView scroll;

    String screen = "home";
    String selectedCountry = "";
    String selectedLeague = "";
    String selectedTeam = "";
    String selectedModule = "goals";
    String scope = "all";
    String period = "FT";
    boolean showMoreFuture = false;
    boolean loading = false;

    String seriesModule = "goals";
    String seriesPeriod = "FT";
    String seriesRule = "Under";
    String seriesValueSide = "Total";
    double seriesThreshold = 2.5;
    String seriesScope = "all";
    int seriesMin = 3;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        configureWindow();
        setContentView(buildShell());
        applySafeInsets(root);
        loadIndex(false);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    void configureWindow() {
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    }

    void applySafeInsets(View v) {
        v.setOnApplyWindowInsetsListener((view,insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0,top,0,bottom);
            return insets;
        });
        v.requestApplyInsets();
    }

    View buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(d(16),d(12),d(16),d(15));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY,NAVY2});
        bg.setCornerRadii(new float[]{0,0,0,0,d(22),d(22),d(22),d(22)});
        header.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("BESTSPORTSTATS",11,Color.rgb(125,211,252),true);
        brand.setLetterSpacing(.08f);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));

        TextView home = text("⌂",26,Color.WHITE,true);
        home.setGravity(Gravity.CENTER);
        home.setPadding(d(8),0,d(8),0);
        home.setOnClickListener(v -> { screen="home"; render(); });
        top.addView(home,new LinearLayout.LayoutParams(d(42),d(38)));

        TextView refresh = text("↻",27,Color.WHITE,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> loadIndex(true));
        top.addView(refresh,new LinearLayout.LayoutParams(d(42),d(38)));
        header.addView(top);

        headerTitle = text("BestSportStats",25,Color.WHITE,true);
        header.addView(headerTitle,mt(3,-2));
        headerSubtitle = text("Statistica sezonului curent",12,Color.rgb(203,213,225),false);
        header.addView(headerSubtitle,mt(1,-2));
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(d(13),d(12),d(13),d(30));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        return root;
    }

    void loadIndex(boolean force) {
        if (loading) return;
        loading = true;
        renderLoading(force ? "Actualizez INDEX-ul…" : "Încarc datele…");

        executor.execute(() -> {
            ArrayList<LeagueSource> fresh = new ArrayList<>();
            AppConfig freshConfig = new AppConfig();
            String err = "";
            try {
                String html = httpGet(INDEX_URL);
                String json = extractHiddenJson(html,"bss2-json-data");
                parseV2Index(json,fresh);
                if (fresh.isEmpty()) throw new IOException("INDEX V2 gol");
                try {
                    String ch = httpGet(CONFIG_URL);
                    String cj = extractHiddenJson(ch,"bss2-json-data");
                    freshConfig = AppConfig.from(cj);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                err = e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                try {
                    String html = httpGet(LEGACY_INDEX_URL);
                    String json = extractHiddenJson(html,"goals-json-data");
                    parseLegacyIndex(json,fresh);
                } catch (Exception ignored) {}
            }

            final ArrayList<LeagueSource> result = fresh;
            final AppConfig resultConfig = freshConfig;
            final String finalErr = err;
            runOnUiThread(() -> {
                loading = false;
                if (!result.isEmpty()) {
                    catalog.clear(); catalog.addAll(result);
                    config = resultConfig;
                    restoreSelection();
                    LeagueSource src = findSource(selectedCountry,selectedLeague);
                    if (src != null) loadLeague(src,false);
                    else render();
                } else {
                    renderError(finalErr.isEmpty()?"Nu am putut încărca INDEX-ul.":finalErr);
                }
            });
        });
    }

    void loadLeague(LeagueSource src, boolean preserveTeam) {
        if (src==null || loading) return;
        loading = true;
        renderLoading("Încarc " + src.league + "…");
        final String oldTeam = preserveTeam ? selectedTeam : "";

        executor.execute(() -> {
            LeagueData fresh = null;
            String err="";
            try {
                String html = httpGet(src.dataUrl);
                String json = extractHiddenJson(html,"bss2-json-data");
                if (!json.isEmpty()) fresh = LeagueData.fromV2(json,src);
                if (fresh==null || fresh.fixtures.isEmpty()) {
                    String legacy = extractHiddenJson(html,"goals-json-data");
                    if (!legacy.isEmpty()) fresh = LeagueData.fromLegacy(legacy,src);
                }
                if (fresh==null) throw new IOException("Pagina ligii nu conține date compatibile");
                saveCache(src,fresh.rawJson);
            } catch (Exception e) {
                err=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                try {
                    String cached = readCache(src);
                    if (!cached.isEmpty()) fresh = LeagueData.fromV2(cached,src);
                } catch(Exception ignored) {}
            }
            final LeagueData result=fresh;
            final String finalErr=err;
            runOnUiThread(() -> {
                loading=false;
                if (result!=null) {
                    leagueData=result;
                    selectedCountry=src.country;
                    selectedLeague=src.league;
                    ArrayList<String> teams=teamNames();
                    if (!oldTeam.isEmpty() && teams.contains(oldTeam)) selectedTeam=oldTeam;
                    else if (!teams.contains(selectedTeam)) selectedTeam=teams.isEmpty()?"":teams.get(0);
                    ensureSelectedModule();
                    resetContextFilters();
                    persistSelection();
                    render();
                } else renderError(finalErr);
            });
        });
    }

    String httpGet(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent","BestSportStats-Android/2.0");
        int code=c.getResponseCode();
        InputStream in = code>=200 && code<300 ? c.getInputStream() : c.getErrorStream();
        String body=readAll(in);
        c.disconnect();
        if (code<200 || code>=300) throw new IOException("HTTP "+code);
        return body;
    }

    String readAll(InputStream in) throws Exception {
        if (in==null) return "";
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        byte[] b=new byte[8192]; int n;
        while((n=in.read(b))>0) bos.write(b,0,n);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    String extractHiddenJson(String html,String id) {
        if (html==null) return "";
        Pattern p=Pattern.compile("<div[^>]*id=[\\\"']"+Pattern.quote(id)+"[\\\"'][^>]*>(.*?)</div>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
        Matcher m=p.matcher(html);
        if(!m.find()) return "";
        return htmlDecode(m.group(1)).trim();
    }

    String htmlDecode(String s) {
        return s.replace("&quot;","\"").replace("&#39;","'")
                .replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");
    }

    void parseV2Index(String json, ArrayList<LeagueSource> out) throws Exception {
        JSONObject root=new JSONObject(json);
        JSONArray a=root.optJSONArray("competitions");
        if(a==null) return;
        for(int i=0;i<a.length();i++) {
            JSONObject o=a.optJSONObject(i); if(o==null || !o.optBoolean("ready",true)) continue;
            LeagueSource s=LeagueSource.fromV2(o);
            if(s.valid()) out.add(s);
        }
        sortCatalog(out);
    }

    void parseLegacyIndex(String json, ArrayList<LeagueSource> out) throws Exception {
        JSONObject root=new JSONObject(json);
        JSONArray a=root.optJSONArray("competitions");
        if(a==null) return;
        for(int i=0;i<a.length();i++) {
            JSONObject o=a.optJSONObject(i); if(o==null || !o.optBoolean("ready",false)) continue;
            LeagueSource s=LeagueSource.fromLegacy(o);
            if(s.valid()) out.add(s);
        }
        sortCatalog(out);
    }

    void sortCatalog(ArrayList<LeagueSource> list) {
        Collections.sort(list,(a,b)->{
            int cr=Integer.compare(a.countryRank,b.countryRank);
            if(cr!=0) return cr;
            int cn=a.country.compareToIgnoreCase(b.country);
            if(cn!=0 && a.countryRank>=9999 && b.countryRank>=9999) return cn;
            int lr=Integer.compare(a.leagueRank,b.leagueRank);
            if(lr!=0) return lr;
            return a.league.compareToIgnoreCase(b.league);
        });
    }

    void render() {
        content.removeAllViews();
        if ("home".equals(screen)) renderHome();
        else if ("stats".equals(screen)) renderStats();
        else renderSeries();
        scroll.scrollTo(0,0);
    }

    void setHeader(String t,String s) { headerTitle.setText(t); headerSubtitle.setText(s); }

    void renderHome() {
        setHeader("BestSportStats","Sezonul curent • date dinamice");
        renderOnlineSummary();

        LinearLayout c=card();
        c.addView(text("Alege compartimentul",18,TEXT,true));
        c.addView(text("Datele și ligile se actualizează din Blogger fără APK nou.",11,MUTED,false),mt(3,-2));

        Button stats=bigAction("▥  Statistica","Goluri, cornere, cartonașe și toate modulele disponibile",SOFT_BLUE,BLUE);
        stats.setOnClickListener(v->{screen="stats";render();});
        c.addView(stats,mt(12,d(78)));

        Button series=bigAction("↗  Serii","Găsește automat echipele cu serii active",SOFT_PURPLE,PURPLE);
        series.setOnClickListener(v->{screen="series";render();});
        c.addView(series,mt(8,d(78)));
        content.addView(c,mt(8,-2));

        if (config.maintenance && !config.maintenanceMessage.isEmpty()) {
            LinearLayout m=card();
            m.setBackground(rounded(SOFT_AMBER,16));
            m.addView(text(config.maintenanceMessage,13,AMBER,true));
            content.addView(m,mt(8,-2));
        }
    }

    void renderOnlineSummary() {
        LinkedHashSet<String> countries=new LinkedHashSet<>();
        int comps=0,finished=0;
        for(LeagueSource s:catalog){countries.add(s.country); comps++; finished+=Math.max(0,s.finishedFixtures);}
        LinearLayout c=compactCard();
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Date online",11,MUTED,true));
        sourceStatus=text(countries.size()+" țări • "+comps+" competiții • "+fmt(finished)+" meciuri FT",14,TEXT,true);
        left.addView(sourceStatus,mt(1,-2));
        row.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(chip("● LIVE",GREEN,SOFT_GREEN)); c.addView(row);
        content.addView(c);
    }

    void renderStats() {
        setHeader("Statistica","Doar modulele disponibile pentru liga selectată");
        renderSelectors(true);
        if (leagueData==null || selectedTeam.isEmpty()) return;
        renderModuleTabs();
        renderTeamHero();
        renderFutureFixtures();
        renderScope();
        if("goals".equals(selectedModule)) renderGoals();
        else renderNumericModule(selectedModule);
    }

    void renderSeries() {
        setHeader("Serii","Filtrează seriile active ale echipelor");
        renderSelectors(false);
        if(leagueData==null) return;
        ensureSeriesModule();
        renderSeriesFilter();
        renderSeriesResults();
    }

    void renderSelectors(boolean withTeam) {
        LinearLayout c=compactCard();
        c.addView(text(withTeam?"Selectează echipa":"Selectează campionatul",16,TEXT,true));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER);

        Button cb=selectButton(selectedCountry.isEmpty()?"Țară":selectedCountry);
        cb.setOnClickListener(v->chooseCountry(withTeam));
        row.addView(cb,new LinearLayout.LayoutParams(0,d(44),1));

        Button lb=selectButton(selectedLeague.isEmpty()?"Ligă":selectedLeague);
        lb.setOnClickListener(v->chooseLeague(withTeam));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(44),1);lp.leftMargin=d(5);row.addView(lb,lp);

        if(withTeam){
            Button tb=selectButton(selectedTeam.isEmpty()?"Echipă":selectedTeam);
            tb.setOnClickListener(v->chooseTeam());
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,d(44),1);tp.leftMargin=d(5);row.addView(tb,tp);
        }
        c.addView(row,mt(7,-2)); content.addView(c,mt(7,-2));
    }

    void renderModuleTabs() {
        ArrayList<String> mods=availableModules();
        if(mods.isEmpty()) return;
        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setPadding(0,d(2),d(4),d(2));
        for(String m:mods){
            Button b=smallTab(moduleLabel(m),m.equals(selectedModule));
            b.setOnClickListener(v->{selectedModule=m;period="FT";showMoreFuture=false;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,d(40));p.rightMargin=d(5);row.addView(b,p);
        }
        hs.addView(row); content.addView(hs,mt(7,d(44)));
    }

    void renderTeamHero() {
        LinearLayout c=compactCard();
        c.addView(text(selectedTeam,21,TEXT,true));
        c.addView(text(selectedLeague+" • "+selectedCountry,11,MUTED,true),mt(1,-2));
        content.addView(c,mt(7,-2));
    }

    void renderFutureFixtures() {
        ArrayList<Fixture> f=futureForTeam(selectedTeam);
        if(f.isEmpty()) return;
        LinearLayout c=compactCard();
        c.addView(text("Următorul meci",14,TEXT,true));
        c.addView(futureRow(f.get(0)),mt(6,-2));
        if(f.size()>1){
            Button more=linkButton(showMoreFuture?"Ascunde programul":"Arată mai multe ("+(f.size()-1)+")");
            more.setOnClickListener(v->{showMoreFuture=!showMoreFuture;render();});
            c.addView(more,mt(5,d(38)));
            if(showMoreFuture) for(int i=1;i<f.size();i++) c.addView(futureRow(f.get(i)),mt(4,-2));
        }
        content.addView(c,mt(7,-2));
    }

    View futureRow(Fixture f){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(d(9),d(7),d(9),d(7));r.setBackground(bordered(Color.WHITE,LINE,10));
        r.addView(text(shortDate(f.date)+" • "+cleanRound(f.round),10,MUTED,true));
        r.addView(text(f.home.name+"  –  "+f.away.name,13,TEXT,true),mt(2,-2));return r;
    }

    void renderScope(){
        LinearLayout c=compactCard();
        LinearLayout row=new LinearLayout(this);
        String[] labs={"Toate","Acasă","Deplasare"}; String[] vals={"all","home","away"};
        for(int i=0;i<3;i++){
            final String val=vals[i]; Button b=segment(labs[i],scope.equals(val)); b.setOnClickListener(v->{scope=val;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,d(39),1);if(i>0)p.leftMargin=d(5);row.addView(b,p);
        }
        c.addView(row);content.addView(c,mt(7,-2));
    }

    void renderGoals(){
        ArrayList<Fixture> matches=finishedForTeam(selectedTeam,scope);
        if(matches.isEmpty()){renderEmpty("Nu există meciuri terminate pentru filtrul selectat.");return;}
        renderPeriodTabs();
        GoalStats gs=GoalStats.calc(matches,selectedTeam,period);
        LinearLayout c=compactCard();
        c.addView(text(periodLabel(period),15,TEXT,true));
        addStat(c,"Meciuri",String.valueOf(gs.games),TEXT);
        addStat(c,"Victorii",gs.wins+"  ("+pct(gs.wins,gs.games)+")",GREEN);
        addStat(c,"Egaluri",gs.draws+"  ("+pct(gs.draws,gs.games)+")",AMBER);
        addStat(c,"Înfrângeri",gs.losses+"  ("+pct(gs.losses,gs.games)+")",RED);
        addDivider(c);
        addStat(c,"Goluri totale",String.valueOf(gs.totalGoals),TEXT);
        addStat(c,"Medie / meci",one(gs.totalGoals/(double)Math.max(1,gs.games)),TEXT);
        addStat(c,"Marcate",String.valueOf(gs.gf),GREEN);
        addStat(c,"Primite",String.valueOf(gs.ga),RED);
        addDivider(c);
        double[] th="FT".equals(period)?new double[]{.5,1.5,2.5,3.5,4.5}:new double[]{.5,1.5,2.5,3.5};
        for(double t:th){int n=goalCount(matches,selectedTeam,period,t,true);addStat(c,"Over "+oneThreshold(t),n+"  ("+pct(n,gs.games)+")",GREEN);}
        int btts=goalBttsCount(matches,period);addStat(c,"BTTS • Da",btts+"  ("+pct(btts,gs.games)+")",GREEN);
        addStat(c,"BTTS • Nu",(gs.games-btts)+"  ("+pct(gs.games-btts,gs.games)+")",RED);
        int even=goalEvenCount(matches,period);addStat(c,"Par",even+"  ("+pct(even,gs.games)+")",TEXT);
        addStat(c,"Impar",(gs.games-even)+"  ("+pct(gs.games-even,gs.games)+")",TEXT);
        content.addView(c,mt(7,-2));
        renderGoalsTable(matches);
    }

    void renderPeriodTabs(){
        LinearLayout c=compactCard();LinearLayout row=new LinearLayout(this);
        String[] labs={"Final","Prima repriză","A doua repriză"};String[] vals={"FT","HT","2HT"};
        for(int i=0;i<3;i++){
            final String val=vals[i];Button b=segment(labs[i],period.equals(val));b.setTextSize(10);b.setOnClickListener(v->{period=val;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,d(40),1);if(i>0)p.leftMargin=d(5);row.addView(b,p);
        }c.addView(row);content.addView(c,mt(7,-2));
    }

    void renderGoalsTable(ArrayList<Fixture> matches){
        LinearLayout c=compactCard();c.addView(text("Istoric meci cu meci",15,TEXT,true));
        HorizontalScrollView hs=new HorizontalScrollView(this);TableLayout table=new TableLayout(this);table.setShrinkAllColumns(false);
        table.addView(tableHeader(new String[]{"Data","Etapa","Meci","FT","1.5","2.5","3.5","E/O","BTTS","HT","2HT"},new int[]{68,90,150,46,48,48,48,46,50,46,46}));
        for(Fixture f:matches){
            int[] ft=f.score.ft,ht=f.score.ht,sh=f.score.sh;
            String[] vals={shortDate(f.date),cleanRound(f.round),f.home.name+" - "+f.away.name,score(ft),ou(ft,1.5),ou(ft,2.5),ou(ft,3.5),evenOdd(ft),btts(ft),score(ht),score(sh)};
            table.addView(tableRow(vals,new int[]{68,90,150,46,48,48,48,46,50,46,46}));
        }
        hs.addView(table);c.addView(hs,mt(7,-2));content.addView(c,mt(7,-2));
    }

    void renderNumericModule(String module){
        ArrayList<Fixture> matches=finishedForTeam(selectedTeam,scope);
        Metric metric=metricFor(module);
        if(metric==null){renderEmpty("Modul indisponibil.");return;}
        ArrayList<Fixture> available=new ArrayList<>();
        for(Fixture f:matches) if(metricValues(f,selectedTeam,metric.key)!=null) available.add(f);
        if(available.isEmpty()){renderEmpty("Nu există date pentru această statistică în meciurile selectate.");return;}

        double total=0,team=0,opp=0;
        for(Fixture f:available){double[] v=metricValues(f,selectedTeam,metric.key);team+=v[0];opp+=v[1];total+=v[0]+v[1];}
        LinearLayout c=compactCard();
        c.addView(text(metric.label,15,TEXT,true));
        addStat(c,"Meciuri cu date",available.size()+" / "+matches.size(),TEXT);
        addStat(c,"Medie total / meci",one(total/available.size()),BLUE);
        addStat(c,"Echipa / meci",one(team/available.size()),GREEN);
        addStat(c,"Adversar / meci",one(opp/available.size()),RED);
        addDivider(c);
        for(double t:metric.thresholds){int n=0;for(Fixture f:available){double[] v=metricValues(f,selectedTeam,metric.key);if(v[0]+v[1]>t)n++;}addStat(c,"Over "+oneThreshold(t),n+"  ("+pct(n,available.size())+")",GREEN);}
        content.addView(c,mt(7,-2));

        LinearLayout tc=compactCard();tc.addView(text("Istoric meci cu meci",15,TEXT,true));
        HorizontalScrollView hs=new HorizontalScrollView(this);TableLayout table=new TableLayout(this);
        ArrayList<String> heads=new ArrayList<>(Arrays.asList("Data","Etapa","Meci","Echipă","Adv.","Total"));
        for(double t:metric.tableThresholds)heads.add(oneThreshold(t));
        int[] widths=new int[heads.size()];for(int i=0;i<widths.length;i++)widths[i]=(i==2?150:(i==1?90:58));widths[0]=68;
        table.addView(tableHeader(heads.toArray(new String[0]),widths));
        for(Fixture f:available){double[] v=metricValues(f,selectedTeam,metric.key);ArrayList<String> vals=new ArrayList<>();
            vals.add(shortDate(f.date));vals.add(cleanRound(f.round));vals.add(f.home.name+" - "+f.away.name);vals.add(one(v[0]));vals.add(one(v[1]));vals.add(one(v[0]+v[1]));
            for(double t:metric.tableThresholds)vals.add((v[0]+v[1]>t)?"O":"U");
            table.addView(tableRow(vals.toArray(new String[0]),widths));
        }
        hs.addView(table);tc.addView(hs,mt(7,-2));content.addView(tc,mt(7,-2));
    }

    void renderSeriesFilter(){
        LinearLayout c=compactCard();c.addView(text("Filtru serii",16,TEXT,true));
        c.addView(filterRow("Statistică",moduleLabel(seriesModule),v->chooseSeriesModule()),mt(7,-2));
        if("goals".equals(seriesModule)) c.addView(filterRow("Perioadă",periodLabel(seriesPeriod),v->chooseSeriesPeriod()),mt(5,-2));
        else c.addView(filterRow("Valoare",seriesValueSide,v->chooseSeriesSide()),mt(5,-2));
        c.addView(filterRow("Tip",seriesRule,v->chooseSeriesRule()),mt(5,-2));
        if(seriesUsesThreshold()) c.addView(filterRow("Prag",oneThreshold(seriesThreshold),v->chooseSeriesThreshold()),mt(5,-2));
        c.addView(filterRow("Meciuri",scopeLabel(seriesScope),v->chooseSeriesScope()),mt(5,-2));
        c.addView(filterRow("Serie minimă",seriesMin+"+",v->chooseSeriesMin()),mt(5,-2));
        content.addView(c,mt(7,-2));
    }

    View filterRow(String label,String value,View.OnClickListener click){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,12,MUTED,true);r.addView(l,new LinearLayout.LayoutParams(0,d(38),1));
        Button b=selectButton(value+" ▾");b.setOnClickListener(click);r.addView(b,new LinearLayout.LayoutParams(d(155),d(38)));return r;
    }

    void renderSeriesResults(){
        ArrayList<SeriesItem> items=calculateSeries();
        LinearLayout c=compactCard();c.addView(text("Serii active",16,TEXT,true));
        if(items.isEmpty()){c.addView(text("Nicio echipă nu are acum seria selectată de minimum "+seriesMin+" meciuri.",12,MUTED,false),mt(7,-2));}
        else{
            c.addView(text(items.size()+" echipe • ordonate după seria cea mai lungă",11,MUTED,false),mt(2,-2));
            int rank=1;for(SeriesItem it:items){
                LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(d(8),d(8),d(8),d(8));r.setBackground(bordered(Color.WHITE,LINE,10));
                TextView pos=text(String.valueOf(rank++),12,MUTED,true);pos.setGravity(Gravity.CENTER);r.addView(pos,new LinearLayout.LayoutParams(d(30),-1));
                LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text(it.team,13,TEXT,true));names.addView(text(it.lastDate,10,MUTED,false),mt(1,-2));r.addView(names,new LinearLayout.LayoutParams(0,-2,1));
                TextView st=chip(it.streak+" la rând",PURPLE,SOFT_PURPLE);r.addView(st);
                final String team=it.team;r.setOnClickListener(v->{selectedTeam=team;selectedModule=seriesModule;period=seriesPeriod;screen="stats";scope=seriesScope;render();});
                c.addView(r,mt(5,-2));
            }
        }
        content.addView(c,mt(7,-2));
    }

    ArrayList<SeriesItem> calculateSeries(){
        ArrayList<SeriesItem> out=new ArrayList<>();
        for(String team:teamNames()){
            ArrayList<Fixture> matches=finishedForTeam(team,seriesScope);
            int streak=0;String date="";
            for(Fixture f:matches){
                Boolean ok=seriesMatch(f,team);if(ok==null)continue;if(ok){streak++;if(date.isEmpty())date=shortDate(f.date);}else break;
            }
            if(streak>=seriesMin)out.add(new SeriesItem(team,streak,date));
        }
        Collections.sort(out,(a,b)->{int x=Integer.compare(b.streak,a.streak);return x!=0?x:a.team.compareToIgnoreCase(b.team);});
        return out;
    }

    Boolean seriesMatch(Fixture f,String team){
        if("goals".equals(seriesModule)){
            int[] s=scoreForPeriod(f,seriesPeriod);if(s==null)return null;int sum=s[0]+s[1];
            if("Over".equals(seriesRule))return sum>seriesThreshold;
            if("Under".equals(seriesRule))return sum<seriesThreshold;
            if("BTTS Da".equals(seriesRule))return s[0]>0&&s[1]>0;
            if("BTTS Nu".equals(seriesRule))return !(s[0]>0&&s[1]>0);
            if("Par".equals(seriesRule))return sum%2==0;
            if("Impar".equals(seriesRule))return sum%2!=0;
            int[] tf=teamScore(f,team,s);if(tf==null)return null;
            if("Victorie".equals(seriesRule))return tf[0]>tf[1];
            if("Egal".equals(seriesRule))return tf[0]==tf[1];
            if("Înfrângere".equals(seriesRule))return tf[0]<tf[1];
            if("Fără înfrângere".equals(seriesRule))return tf[0]>=tf[1];
            if("Marchează".equals(seriesRule))return tf[0]>0;
            if("Nu marchează".equals(seriesRule))return tf[0]==0;
            if("Fără gol primit".equals(seriesRule))return tf[1]==0;
            return null;
        }
        Metric m=metricFor(seriesModule);if(m==null)return null;double[] v=metricValues(f,team,m.key);if(v==null)return null;
        double val="Echipă".equals(seriesValueSide)?v[0]:"Adversar".equals(seriesValueSide)?v[1]:v[0]+v[1];
        if("Over".equals(seriesRule))return val>seriesThreshold;
        if("Under".equals(seriesRule))return val<seriesThreshold;
        return null;
    }

    void chooseCountry(boolean withTeam){
        ArrayList<String> list=countryNames();showChoice("Alege țara",list,selectedCountry,val->{
            selectedCountry=val;ArrayList<String> ls=leagueNames(val);selectedLeague=ls.isEmpty()?"":ls.get(0);selectedTeam="";showMoreFuture=false;
            LeagueSource src=findSource(selectedCountry,selectedLeague);if(src!=null)loadLeague(src,false);else render();
        });
    }
    void chooseLeague(boolean withTeam){
        ArrayList<String> list=leagueNames(selectedCountry);showChoice("Alege liga",list,selectedLeague,val->{selectedLeague=val;selectedTeam="";showMoreFuture=false;LeagueSource src=findSource(selectedCountry,val);if(src!=null)loadLeague(src,false);});
    }
    void chooseTeam(){ArrayList<String> list=teamNames();showChoice("Alege echipa",list,selectedTeam,val->{selectedTeam=val;resetContextFilters();persistSelection();render();});}

    void chooseSeriesModule(){ArrayList<String> mods=availableModules();ArrayList<String> labels=new ArrayList<>();for(String m:mods)labels.add(moduleLabel(m));showChoice("Statistică",labels,moduleLabel(seriesModule),lab->{for(String m:mods)if(moduleLabel(m).equals(lab))seriesModule=m;seriesRule="goals".equals(seriesModule)?"Under":"Over";seriesThreshold=defaultThreshold(seriesModule);render();});}
    void chooseSeriesPeriod(){showChoice("Perioadă",new ArrayList<>(Arrays.asList("Final","Prima repriză","A doua repriză")),periodLabel(seriesPeriod),lab->{seriesPeriod="Prima repriză".equals(lab)?"HT":"A doua repriză".equals(lab)?"2HT":"FT";render();});}
    void chooseSeriesSide(){showChoice("Valoare",new ArrayList<>(Arrays.asList("Total","Echipă","Adversar")),seriesValueSide,val->{seriesValueSide=val;render();});}
    void chooseSeriesRule(){ArrayList<String> r=new ArrayList<>();if("goals".equals(seriesModule))r.addAll(Arrays.asList("Under","Over","BTTS Da","BTTS Nu","Par","Impar","Victorie","Egal","Înfrângere","Fără înfrângere","Marchează","Nu marchează","Fără gol primit"));else r.addAll(Arrays.asList("Over","Under"));showChoice("Tip serie",r,seriesRule,val->{seriesRule=val;render();});}
    void chooseSeriesThreshold(){ArrayList<String> vals=new ArrayList<>();for(double x:thresholdsFor(seriesModule))vals.add(oneThreshold(x));showChoice("Prag",vals,oneThreshold(seriesThreshold),val->{try{seriesThreshold=Double.parseDouble(val);}catch(Exception ignored){}render();});}
    void chooseSeriesScope(){ArrayList<String> labels=new ArrayList<>(Arrays.asList("Toate","Acasă","Deplasare"));showChoice("Meciuri",labels,scopeLabel(seriesScope),lab->{seriesScope="Acasă".equals(lab)?"home":"Deplasare".equals(lab)?"away":"all";render();});}
    void chooseSeriesMin(){ArrayList<String> vals=new ArrayList<>();for(int i=3;i<=12;i++)vals.add(i+"+");showChoice("Serie minimă",vals,seriesMin+"+",val->{try{seriesMin=Integer.parseInt(val.replace("+",""));}catch(Exception ignored){}render();});}

    interface Pick { void go(String value); }
    void showChoice(String title,ArrayList<String> items,String selected,Pick cb){
        if(items.isEmpty())return;String[] a=items.toArray(new String[0]);int checked=items.indexOf(selected);
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(a,checked,(d,w)->{String v=a[w];d.dismiss();cb.go(v);}).setNegativeButton("Închide",null).show();
    }

    ArrayList<String> countryNames(){LinkedHashSet<String> set=new LinkedHashSet<>();for(LeagueSource s:catalog)set.add(s.country);return new ArrayList<>(set);}
    ArrayList<String> leagueNames(String country){ArrayList<String> out=new ArrayList<>();for(LeagueSource s:catalog)if(s.country.equals(country)&&!out.contains(s.league))out.add(s.league);return out;}
    LeagueSource findSource(String c,String l){for(LeagueSource s:catalog)if(s.country.equals(c)&&s.league.equals(l))return s;return null;}

    ArrayList<String> teamNames(){
        ArrayList<String> out=new ArrayList<>();if(leagueData==null)return out;
        for(Team t:leagueData.teams)if(!out.contains(t.name))out.add(t.name);
        if(out.isEmpty())for(Fixture f:leagueData.fixtures){if(!out.contains(f.home.name))out.add(f.home.name);if(!out.contains(f.away.name))out.add(f.away.name);}
        Collections.sort(out,String.CASE_INSENSITIVE_ORDER);return out;
    }

    ArrayList<Fixture> finishedForTeam(String team,String sc){
        ArrayList<Fixture> out=new ArrayList<>();if(leagueData==null)return out;
        for(Fixture f:leagueData.fixtures){if(!f.finished()||!f.hasTeam(team))continue;if("home".equals(sc)&&!f.home.name.equals(team))continue;if("away".equals(sc)&&!f.away.name.equals(team))continue;out.add(f);}
        Collections.sort(out,(a,b)->Long.compare(b.ts,a.ts));return out;
    }
    ArrayList<Fixture> futureForTeam(String team){ArrayList<Fixture> out=new ArrayList<>();if(leagueData==null)return out;long now=System.currentTimeMillis()/1000L;for(Fixture f:leagueData.fixtures)if(f.hasTeam(team)&&f.future()&&f.ts>=now-86400)out.add(f);Collections.sort(out,Comparator.comparingLong(a->a.ts));return out;}

    ArrayList<String> availableModules(){ArrayList<String> out=new ArrayList<>();if(leagueData==null)return out;String[] order={"goals","corners","cards","shots","fouls","offsides","possession","passes","saves","xg","freeKicks"};for(String m:order)if(leagueData.modules.has(m))out.add(m);return out;}
    void ensureSelectedModule(){ArrayList<String> m=availableModules();if(!m.contains(selectedModule))selectedModule=m.isEmpty()?"goals":m.get(0);}
    void ensureSeriesModule(){ArrayList<String> m=availableModules();if(!m.contains(seriesModule))seriesModule=m.isEmpty()?"goals":m.get(0);}
    void resetContextFilters(){scope="all";period="FT";showMoreFuture=false;}

    String moduleLabel(String m){
        switch(m){case"goals":return"Goluri";case"corners":return"Cornere";case"cards":return"Cartonașe galbene";case"shots":return"Șuturi";case"fouls":return"Faulturi";case"offsides":return"Offside";case"possession":return"Posesie";case"passes":return"Pase";case"saves":return"Parade";case"xg":return"xG";case"freeKicks":return"Lovituri libere";default:return m;}
    }

    Metric metricFor(String m){
        if("corners".equals(m))return new Metric("cor","Cornere",new double[]{5.5,6.5,7.5,8.5,9.5,10.5,11.5,12.5},new double[]{7.5,8.5,9.5,10.5});
        if("cards".equals(m))return new Metric("yc","Cartonașe galbene",new double[]{1.5,2.5,3.5,4.5,5.5,6.5},new double[]{2.5,3.5,4.5,5.5});
        if("shots".equals(m))return new Metric("sht","Șuturi totale",new double[]{7.5,9.5,11.5,13.5,15.5,17.5,19.5},new double[]{9.5,11.5,13.5,15.5});
        if("fouls".equals(m))return new Metric("fou","Faulturi",new double[]{15.5,19.5,23.5,27.5,31.5},new double[]{19.5,23.5,27.5});
        if("offsides".equals(m))return new Metric("off","Offside",new double[]{0.5,1.5,2.5,3.5,4.5,5.5},new double[]{1.5,2.5,3.5});
        if("possession".equals(m))return new Metric("pos","Posesie %",new double[]{45.5,49.5,54.5,59.5},new double[]{49.5,54.5});
        if("passes".equals(m))return new Metric("pas","Pase",new double[]{599.5,699.5,799.5,899.5,999.5},new double[]{699.5,799.5,899.5});
        if("saves".equals(m))return new Metric("sav","Parade portar",new double[]{3.5,5.5,7.5,9.5,11.5},new double[]{5.5,7.5,9.5});
        if("xg".equals(m))return new Metric("xg","Expected Goals (xG)",new double[]{1.5,2.5,3.5,4.5},new double[]{1.5,2.5,3.5});
        if("freeKicks".equals(m))return new Metric("fk","Lovituri libere",new double[]{15.5,19.5,23.5,27.5},new double[]{19.5,23.5});
        return null;
    }

    double[] thresholdsFor(String module){if("goals".equals(module))return new double[]{.5,1.5,2.5,3.5,4.5,5.5};Metric m=metricFor(module);return m==null?new double[]{.5,1.5,2.5}:m.thresholds;}
    double defaultThreshold(String module){if("goals".equals(module))return 2.5;Metric m=metricFor(module);return m==null?2.5:m.thresholds[Math.min(2,m.thresholds.length-1)];}
    boolean seriesUsesThreshold(){return "Over".equals(seriesRule)||"Under".equals(seriesRule);}

    double[] metricValues(Fixture f,String team,String key){
        if(f.stats==null||f.stats.full==null)return null;Map<String,Double> h=f.stats.full.home,a=f.stats.full.away;
        if(!h.containsKey(key)||!a.containsKey(key))return null;double hv=h.get(key),av=a.get(key);return f.home.name.equals(team)?new double[]{hv,av}:f.away.name.equals(team)?new double[]{av,hv}:null;
    }

    int[] scoreForPeriod(Fixture f,String p){if(f.score==null)return null;if("HT".equals(p))return f.score.ht;if("2HT".equals(p))return f.score.sh;return f.score.ft;}
    int[] teamScore(Fixture f,String team,int[] s){if(s==null)return null;if(f.home.name.equals(team))return new int[]{s[0],s[1]};if(f.away.name.equals(team))return new int[]{s[1],s[0]};return null;}
    int goalCount(ArrayList<Fixture> fs,String team,String p,double t,boolean over){int n=0;for(Fixture f:fs){int[] s=scoreForPeriod(f,p);if(s!=null&&((s[0]+s[1]>t)==over))n++;}return n;}
    int goalBttsCount(ArrayList<Fixture> fs,String p){int n=0;for(Fixture f:fs){int[] s=scoreForPeriod(f,p);if(s!=null&&s[0]>0&&s[1]>0)n++;}return n;}
    int goalEvenCount(ArrayList<Fixture> fs,String p){int n=0;for(Fixture f:fs){int[] s=scoreForPeriod(f,p);if(s!=null&&(s[0]+s[1])%2==0)n++;}return n;}

    void restoreSelection(){
        SharedPreferences sp=getSharedPreferences("bss2",0);String c=sp.getString("country","");String l=sp.getString("league","");
        LeagueSource src=findSource(c,l);if(src==null&&!catalog.isEmpty())src=catalog.get(0);if(src!=null){selectedCountry=src.country;selectedLeague=src.league;}
        selectedTeam=sp.getString("team","");
    }
    void persistSelection(){getSharedPreferences("bss2",0).edit().putString("country",selectedCountry).putString("league",selectedLeague).putString("team",selectedTeam).apply();}
    String cacheName(LeagueSource s){return "bss2_"+s.leagueId+"_"+s.season+".json";}
    void saveCache(LeagueSource s,String json){try(FileOutputStream o=openFileOutput(cacheName(s),MODE_PRIVATE)){o.write(json.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
    String readCache(LeagueSource s)throws Exception{try(FileInputStream in=openFileInput(cacheName(s))){return readAll(in);}}

    void renderLoading(String msg){content.removeAllViews();LinearLayout c=card();ProgressBar p=new ProgressBar(this);c.addView(p,new LinearLayout.LayoutParams(-1,d(50)));TextView t=text(msg,13,MUTED,true);t.setGravity(Gravity.CENTER);c.addView(t,mt(7,-2));content.addView(c,mt(20,-2));}
    void renderError(String msg){content.removeAllViews();LinearLayout c=card();c.addView(text("Nu am putut încărca datele",17,RED,true));c.addView(text(msg==null?"Eroare necunoscută":msg,12,MUTED,false),mt(6,-2));Button b=linkButton("Încearcă din nou");b.setOnClickListener(v->loadIndex(true));c.addView(b,mt(8,d(42)));content.addView(c,mt(15,-2));}
    void renderEmpty(String msg){LinearLayout c=compactCard();TextView t=text(msg,12,MUTED,false);t.setGravity(Gravity.CENTER);c.addView(t);content.addView(c,mt(7,-2));}

    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(d(15),d(14),d(15),d(14));c.setBackground(rounded(Color.WHITE,18));c.setElevation(d(3));return c;}
    LinearLayout compactCard(){LinearLayout c=card();c.setPadding(d(11),d(10),d(11),d(10));return c;}
    GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));return g;}
    GradientDrawable bordered(int color,int stroke,int radius){GradientDrawable g=rounded(color,radius);g.setStroke(d(1),stroke);return g;}
    TextView text(String s,int z,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(color);v.setLineSpacing(0,1.05f);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    TextView chip(String s,int fg,int bg){TextView v=text(s,10,fg,true);v.setGravity(Gravity.CENTER);v.setPadding(d(9),d(5),d(9),d(5));v.setBackground(rounded(bg,20));return v;}
    Button bigAction(String title,String sub,int bg,int fg){Button b=new Button(this);b.setAllCaps(false);b.setText(title+"\n"+sub);b.setTextSize(15);b.setTextColor(fg);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(d(16),d(8),d(12),d(8));b.setBackground(rounded(bg,14));b.setStateListAnimator(null);return b;}
    Button selectButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(10);b.setTextColor(TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setPadding(d(5),0,d(5),0);b.setBackground(bordered(Color.WHITE,LINE,10));b.setStateListAnimator(null);return b;}
    Button smallTab(String s,boolean active){Button b=selectButton(s);b.setTextSize(11);b.setTextColor(active?Color.WHITE:MUTED);b.setBackground(rounded(active?BLUE:Color.WHITE,12));b.setPadding(d(13),0,d(13),0);return b;}
    Button segment(String s,boolean active){Button b=selectButton(s);b.setTextSize(11);b.setTextColor(active?Color.WHITE:MUTED);b.setBackground(rounded(active?BLUE:Color.rgb(248,250,252),10));return b;}
    Button linkButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(11);b.setTextColor(BLUE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(SOFT_BLUE,10));b.setStateListAnimator(null);return b;}
    void addStat(LinearLayout c,String a,String b,int color){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView l=text(a,12,MUTED,false);TextView v=text(b,12,color,true);v.setGravity(Gravity.END);r.addView(l,new LinearLayout.LayoutParams(0,d(28),1));r.addView(v,new LinearLayout.LayoutParams(0,d(28),1));c.addView(r);}
    void addDivider(LinearLayout c){View v=new View(this);v.setBackgroundColor(LINE);c.addView(v,mt(5,d(1)));}
    TableRow tableHeader(String[] vals,int[] widths){TableRow r=new TableRow(this);for(int i=0;i<vals.length;i++)r.addView(tableCell(vals[i],Color.WHITE,NAVY2,true,widths[i]));return r;}
    TableRow tableRow(String[] vals,int[] widths){TableRow r=new TableRow(this);for(int i=0;i<vals.length;i++){String v=vals[i];int fg=TEXT,bg=Color.WHITE;if("O".equals(v)||"Over".equals(v)||"Yes".equals(v)){fg=GREEN;bg=SOFT_GREEN;}else if("U".equals(v)||"Under".equals(v)||"No".equals(v)){fg=RED;bg=SOFT_RED;}r.addView(tableCell(v,fg,bg,false,widths[i]));}return r;}
    TextView tableCell(String s,int fg,int bg,boolean bold,int width){TextView v=text(s==null?"":s,9,fg,bold);v.setGravity(Gravity.CENTER);v.setPadding(d(3),d(7),d(3),d(7));v.setBackground(bordered(bg,LINE,0));v.setMaxLines(4);v.setLayoutParams(new TableRow.LayoutParams(d(width),-2));return v;}
    LinearLayout.LayoutParams mt(int top,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height);p.topMargin=d(top);return p;}
    int d(int x){return Math.round(x*getResources().getDisplayMetrics().density);}

    String fmt(int n){return NumberFormat.getIntegerInstance(new Locale("ro","RO")).format(n);}
    String pct(int n,int d){return d<=0?"0%":Math.round(n*100f/d)+"%";}
    String one(double v){return String.format(Locale.US,"%.1f",v);}
    String oneThreshold(double v){return Math.abs(v-Math.rint(v))<.0001?String.valueOf((int)v):String.format(Locale.US,"%.1f",v);}
    String score(int[] s){return s==null?"–":s[0]+"-"+s[1];}
    String ou(int[] s,double t){return s==null?"–":s[0]+s[1]>t?"O":"U";}
    String btts(int[] s){return s==null?"–":(s[0]>0&&s[1]>0)?"Yes":"No";}
    String evenOdd(int[] s){return s==null?"–":((s[0]+s[1])%2==0?"E":"O");}
    String shortDate(String iso){try{Date d=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).parse(iso);return new SimpleDateFormat("dd.MM.yy",Locale.US).format(d);}catch(Exception e){return iso==null?"":(iso.length()>=10?iso.substring(0,10):iso);}}
    String cleanRound(String r){if(r==null||r.isEmpty())return"–";return r.replace("Regular Season - ","Etapa ").replace("Regular Season","Sezon regulat");}
    String periodLabel(String p){return"HT".equals(p)?"Prima repriză":"2HT".equals(p)?"A doua repriză":"Final";}
    String scopeLabel(String s){return"home".equals(s)?"Acasă":"away".equals(s)?"Deplasare":"Toate";}

    static class AppConfig {
        boolean appEnabled=true,maintenance=false;String maintenanceMessage="";
        static AppConfig from(String json){AppConfig c=new AppConfig();try{if(json==null||json.isEmpty())return c;JSONObject o=new JSONObject(json);c.appEnabled=o.optBoolean("appEnabled",true);c.maintenance=o.optBoolean("maintenance",false);c.maintenanceMessage=o.optString("maintenanceMessage","");}catch(Exception ignored){}return c;}
    }

    static class LeagueSource {
        String country="",league="",dataUrl="";int leagueId,season,finishedFixtures;int countryRank=9999,leagueRank=9999;ModuleSet modules=new ModuleSet();
        boolean valid(){return !country.isEmpty()&&!league.isEmpty()&&!dataUrl.isEmpty();}
        static LeagueSource fromV2(JSONObject o){LeagueSource s=new LeagueSource();s.country=o.optString("country","");s.league=o.optString("league","");s.leagueId=o.optInt("leagueId",0);s.season=o.optInt("season",0);s.dataUrl=o.optString("dataUrl","");s.countryRank=o.optInt("countryRank",9999);s.leagueRank=o.optInt("leagueRank",9999);JSONObject a=o.optJSONObject("availability");if(a!=null)s.finishedFixtures=a.optInt("finishedFixtures",0);s.modules=ModuleSet.from(o.optJSONObject("modules"));return s;}
        static LeagueSource fromLegacy(JSONObject o){LeagueSource s=new LeagueSource();s.country=o.optString("country","");s.league=o.optString("league","");s.leagueId=o.optInt("leagueId",0);s.season=o.optInt("season",0);s.dataUrl=o.optString("dataUrl","");s.finishedFixtures=o.optInt("matchesFinished",0);s.modules.goals=true;return s;}
    }

    static class ModuleSet {
        boolean goals,corners,cards,shots,fouls,offsides,possession,passes,saves,xg,freeKicks;
        boolean has(String k){switch(k){case"goals":return goals;case"corners":return corners;case"cards":return cards;case"shots":return shots;case"fouls":return fouls;case"offsides":return offsides;case"possession":return possession;case"passes":return passes;case"saves":return saves;case"xg":return xg;case"freeKicks":return freeKicks;}return false;}
        static ModuleSet from(JSONObject o){ModuleSet x=new ModuleSet();if(o==null){x.goals=true;return x;}x.goals=o.optBoolean("goals",false);x.corners=o.optBoolean("corners",false);x.cards=o.optBoolean("cards",false);x.shots=o.optBoolean("shots",false);x.fouls=o.optBoolean("fouls",false);x.offsides=o.optBoolean("offsides",false);x.possession=o.optBoolean("possession",false);x.passes=o.optBoolean("passes",false);x.saves=o.optBoolean("saves",false);x.xg=o.optBoolean("xg",false);x.freeKicks=o.optBoolean("freeKicks",false);return x;}
    }

    static class LeagueData {
        String rawJson="";ModuleSet modules=new ModuleSet();final ArrayList<Team> teams=new ArrayList<>();final ArrayList<Fixture> fixtures=new ArrayList<>();
        static LeagueData fromV2(String json,LeagueSource src)throws Exception{LeagueData d=new LeagueData();d.rawJson=json;JSONObject r=new JSONObject(json);d.modules=ModuleSet.from(r.optJSONObject("modules"));JSONArray ts=r.optJSONArray("teams");if(ts!=null)for(int i=0;i<ts.length();i++){JSONObject o=ts.optJSONObject(i);if(o!=null)d.teams.add(new Team(o.optInt("id"),o.optString("name")));}JSONArray fs=r.optJSONArray("fixtures");if(fs!=null)for(int i=0;i<fs.length();i++){JSONObject o=fs.optJSONObject(i);if(o!=null){Fixture f=Fixture.fromV2(o);if(f!=null)d.fixtures.add(f);}}return d;}
        static LeagueData fromLegacy(String json,LeagueSource src)throws Exception{LeagueData d=new LeagueData();d.rawJson=json;d.modules.goals=true;Object root=new JSONTokener(json).nextValue();JSONArray a=root instanceof JSONArray?(JSONArray)root:((JSONObject)root).optJSONArray("matches");if(a==null&&root instanceof JSONObject)a=((JSONObject)root).optJSONArray("data");if(a==null)return d;LinkedHashMap<String,Team> map=new LinkedHashMap<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;Fixture f=Fixture.fromLegacy(o);if(f!=null){d.fixtures.add(f);map.put(f.home.name,f.home);map.put(f.away.name,f.away);}}d.teams.addAll(map.values());return d;}
    }

    static class Team {int id;String name;Team(int i,String n){id=i;name=n==null?"":n;}}
    static class Score {int[] ht,ft,sh;}
    static class StatPair {Map<String,Double> home=new HashMap<>(),away=new HashMap<>();}
    static class StatsBlock {StatPair full;}

    static class Fixture {
        int id;long ts;String date="",status="",round="";Team home,away;Score score=new Score();StatsBlock stats;
        boolean hasTeam(String t){return home!=null&&away!=null&&(home.name.equals(t)||away.name.equals(t));}
        boolean finished(){return Arrays.asList("FT","AET","PEN","AWD","WO").contains(status);}
        boolean future(){return Arrays.asList("NS","TBD","PST").contains(status);}
        static Fixture fromV2(JSONObject o){try{Fixture f=new Fixture();f.id=o.optInt("id");f.ts=o.optLong("ts");f.date=o.optString("date","");f.status=o.optString("status","");f.round=o.optString("round","");JSONObject h=o.optJSONObject("home"),a=o.optJSONObject("away");f.home=new Team(h==null?0:h.optInt("id"),h==null?"":h.optString("name"));f.away=new Team(a==null?0:a.optInt("id"),a==null?"":a.optString("name"));JSONObject sc=o.optJSONObject("score");if(sc!=null){f.score.ht=pair(sc.optJSONArray("ht"));f.score.ft=pair(sc.optJSONArray("ft"));f.score.sh=pair(sc.optJSONArray("sh"));}JSONObject st=o.optJSONObject("st");if(st!=null){JSONObject full=st.optJSONObject("f");if(full!=null){f.stats=new StatsBlock();f.stats.full=new StatPair();parseStatSide(full.optJSONObject("h"),f.stats.full.home);parseStatSide(full.optJSONObject("a"),f.stats.full.away);}}return f;}catch(Exception e){return null;}}
        static Fixture fromLegacy(JSONObject o){try{Fixture f=new Fixture();f.id=o.optInt("FixtureId",o.optInt("fixtureId",0));f.date=o.optString("Date","");f.ts=o.optLong("Timestamp",0);f.status=o.optString("Status","FT");f.round=o.optString("Round","");String home=o.optString("Home","");String away=o.optString("Away","");String teams=o.optString("Teams","");if((home.isEmpty()||away.isEmpty())&&teams.contains(" - ")){String[] p=teams.split(" - ",2);home=p[0].trim();away=p[1].trim();}f.home=new Team(o.optInt("HomeId",0),home);f.away=new Team(o.optInt("AwayId",0),away);f.score.ft=parseScoreText(o.optString("FT",""));f.score.ht=parseScoreText(o.optString("HT",""));f.score.sh=parseScoreText(o.optString("2HT",""));if(f.ts==0)try{f.ts=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(f.date).getTime()/1000L;}catch(Exception ignored){}return f;}catch(Exception e){return null;}}
        static int[] pair(JSONArray a){if(a==null||a.length()<2||a.isNull(0)||a.isNull(1))return null;return new int[]{a.optInt(0),a.optInt(1)};}
        static int[] parseScoreText(String s){if(s==null)return null;Matcher m=Pattern.compile("(-?\\d+)\\s*[-:]\\s*(-?\\d+)").matcher(s);if(!m.find())return null;return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))};}
        static void parseStatSide(JSONObject o,Map<String,Double> map){if(o==null)return;Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();if(!o.isNull(k))map.put(k,o.optDouble(k));}}
    }

    static class Metric {String key,label;double[] thresholds,tableThresholds;Metric(String k,String l,double[] t,double[] tt){key=k;label=l;thresholds=t;tableThresholds=tt;}}
    static class SeriesItem {String team,lastDate;int streak;SeriesItem(String t,int s,String d){team=t;streak=s;lastDate=d;}}

    static class GoalStats {
        int games,wins,draws,losses,gf,ga,totalGoals;
        static GoalStats calc(ArrayList<Fixture> fs,String team,String p){GoalStats g=new GoalStats();for(Fixture f:fs){int[] s="HT".equals(p)?f.score.ht:"2HT".equals(p)?f.score.sh:f.score.ft;if(s==null)continue;int tf,ta;if(f.home.name.equals(team)){tf=s[0];ta=s[1];}else{tf=s[1];ta=s[0];}g.games++;g.gf+=tf;g.ga+=ta;g.totalGoals+=tf+ta;if(tf>ta)g.wins++;else if(tf==ta)g.draws++;else g.losses++;}return g;}
    }
}