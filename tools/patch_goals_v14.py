from pathlib import Path
import re

JAVA = Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
GRADLE = Path('app/build.gradle')
s = JAVA.read_text(encoding='utf-8')

def sub(pattern, repl, label, flags=re.S):
    global s
    s2, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'v1.4 patch failed {label}: {n}')
    s = s2

sub(r'    static final String\[\] BLOGGER_URLS = \{.*?\n    \};', '''    static final String INDEX_URL =
            "https://www.bestsportstats.com/p/bssgoalsindexselected2024.html";
    static final String FALLBACK_PREMIER_LEAGUE_URL =
            "https://www.bestsportstats.com/p/bssgoalsenglandpremierleague2024.html";''', 'urls')

sub(r'    static final String\[\] PREFERRED_COUNTRIES = \{.*?\n    \};', '''    static final String[] PREFERRED_COUNTRIES = {
            "England", "Europe", "Spain", "Italy", "Germany", "France",
            "Scotland", "Portugal", "Netherlands", "Turkey", "Poland",
            "Norway", "Denmark", "Czech Republic", "Austria", "Romania",
            "Greece", "Sweden", "Switzerland", "Belgium", "Bulgaria",
            "Finland", "Hungary", "Latvia", "Mexico", "Saudi Arabia",
            "Australia", "Ukraine"
    };''', 'preferred')

s = s.replace('    final ArrayList<Match> data = new ArrayList<>();\n',
'''    final ArrayList<Match> data = new ArrayList<>();
    final ArrayList<LeagueSource> catalog = new ArrayList<>();
''')

sub(r'    void loadData\(boolean force\) \{.*?\n    \}\n\n(?=    void renderLoading\()', '''    void loadData(boolean force) {
        if (loading) return;
        loading = true;
        renderLoading(force ? "Actualizez lista de campionate…" : "Încarc statisticile…");

        final String wantedCountry = selectedCountry;
        final String wantedLeague = selectedLeague;

        executor.execute(() -> {
            ArrayList<LeagueSource> freshCatalog = new ArrayList<>();
            ArrayList<Match> fresh = new ArrayList<>();
            LeagueSource source = null;
            String error = "";

            try {
                String indexHtml = httpGet(INDEX_URL);
                String indexJson = extractJsonData(indexHtml);
                addIndexSources(indexJson, freshCatalog);

                if (freshCatalog.isEmpty()) {
                    throw new IOException("INDEX-ul nu conține încă ligi importate");
                }

                source = chooseInitialSource(freshCatalog, wantedCountry, wantedLeague);
                if (source == null) throw new IOException("Nu există o ligă disponibilă");

                String html = httpGet(source.dataUrl);
                addJsonMatches(extractJsonData(html), fresh);

                if (!fresh.isEmpty()) {
                    deduplicate(fresh);
                    sortMatchesNewestFirst(fresh);
                    saveLeagueCache(source, fresh);
                } else {
                    fresh.addAll(readLeagueCache(source));
                }

            } catch (Exception firstError) {
                error = firstError.getMessage() == null
                        ? firstError.getClass().getSimpleName()
                        : firstError.getMessage();

                if (source != null && fresh.isEmpty()) {
                    try { fresh.addAll(readLeagueCache(source)); } catch (Exception ignored) {}
                }

                if (freshCatalog.isEmpty()) {
                    LeagueSource fallback = fallbackPremierLeagueSource();
                    freshCatalog.add(fallback);
                    source = fallback;
                    try {
                        String html = httpGet(fallback.dataUrl);
                        addJsonMatches(extractJsonData(html), fresh);
                        if (!fresh.isEmpty()) {
                            deduplicate(fresh);
                            sortMatchesNewestFirst(fresh);
                            saveLeagueCache(fallback, fresh);
                        }
                    } catch (Exception secondError) {
                        if (fresh.isEmpty()) {
                            try { fresh.addAll(readLeagueCache(fallback)); } catch (Exception ignored) {}
                        }
                    }
                }
            }

            final ArrayList<LeagueSource> resultCatalog = freshCatalog;
            final ArrayList<Match> result = fresh;
            final LeagueSource resultSource = source;
            final String finalError = error;

            runOnUiThread(() -> {
                loading = false;

                catalog.clear();
                catalog.addAll(resultCatalog);

                data.clear();
                data.addAll(result);

                if (resultSource != null) {
                    selectedCountry = resultSource.country;
                    selectedLeague = resultSource.league;
                }

                if (data.isEmpty()) {
                    renderLoadError(finalError);
                } else {
                    restoreOrChooseSelections();
                    renderMain();
                }
            });
        });
    }

    void loadLeagueData(String country, String league) {
        if (loading) return;

        LeagueSource found = findSource(catalog, country, league);
        if (found == null) return;

        selectedCountry = country;
        selectedLeague = league;
        selectedTeam = "";
        persistSelections();

        loading = true;
        renderLoading("Încarc " + league + "…");

        final LeagueSource source = found;

        executor.execute(() -> {
            ArrayList<Match> fresh = new ArrayList<>();
            String error = "";

            try {
                String html = httpGet(source.dataUrl);
                addJsonMatches(extractJsonData(html), fresh);
                if (!fresh.isEmpty()) {
                    deduplicate(fresh);
                    sortMatchesNewestFirst(fresh);
                    saveLeagueCache(source, fresh);
                } else {
                    fresh.addAll(readLeagueCache(source));
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                try { fresh.addAll(readLeagueCache(source)); } catch (Exception ignored) {}
            }

            final ArrayList<Match> result = fresh;
            final String finalError = error;

            runOnUiThread(() -> {
                loading = false;
                data.clear();
                data.addAll(result);
                selectedCountry = source.country;
                selectedLeague = source.league;

                if (data.isEmpty()) {
                    renderLoadError(finalError);
                } else {
                    restoreOrChooseSelections();
                    persistSelections();
                    renderMain();
                }
            });
        });
    }

''', 'loadData')

sub(r'    void renderSourceCard\(\) \{.*?\n    \}\n\n(?=    void renderSelectors\()', '''    void renderSourceCard() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Date online", 12, MUTED, true));
        sourceStatus = text(data.size() + " meciuri • " + catalog.size() + " competiții disponibile", 15, TEXT, true);
        left.addView(sourceStatus, marginTop(2, -2));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView live = chip("● ONLINE", GREEN, SOFT_GREEN);
        row.addView(live);
        c.addView(row);
        TextView hint = text("Aplicația citește INDEX-ul Blogger și descarcă numai campionatul selectat.", 12, MUTED, false);
        c.addView(hint, marginTop(7, -2));
        content.addView(c);
    }

''', 'source card')

s = s.replace('TextView info = text("Verifică internetul și încearcă din nou. Aplicația citește JSON-ul din postările Blogger BestSportStats.", 13, MUTED, false);',
              'TextView info = text("Verifică internetul și încearcă din nou. Aplicația citește INDEX-ul și paginile de date Blogger BestSportStats.", 13, MUTED, false);')

sub(r'    void chooseCountry\(\) \{.*?\n(?=    interface ChoiceCallback)', '''    void chooseCountry() {
        ArrayList<String> list = countries();
        showChoice("Alege țara", list, selectedCountry, value -> {
            selectedCountry = value;
            selectedLeague = firstOrEmpty(leagues(selectedCountry));
            selectedTeam = "";
            persistSelections();
            loadLeagueData(selectedCountry, selectedLeague);
        });
    }

    void chooseLeague() {
        ArrayList<String> list = leagues(selectedCountry);
        showChoice("Alege campionatul", list, selectedLeague, value -> {
            selectedLeague = value;
            selectedTeam = "";
            persistSelections();
            loadLeagueData(selectedCountry, selectedLeague);
        });
    }

    void chooseTeam() {
        ArrayList<String> list = teams(selectedCountry, selectedLeague);
        showChoice("Alege echipa", list, selectedTeam, value -> {
            selectedTeam = value;
            persistSelections();
            renderMain();
        });
    }

''', 'choices')

sub(r'    void restoreOrChooseSelections\(\) \{.*?\n    \}\n\n(?=    void persistSelections\()', '''    void restoreOrChooseSelections() {
        android.content.SharedPreferences sp = getSharedPreferences("goals_stats", 0);

        ArrayList<String> availableCountries = countries();
        String oldCountry = sp.getString("country", selectedCountry.isEmpty() ? "England" : selectedCountry);
        if (selectedCountry.isEmpty() || !availableCountries.contains(selectedCountry)) {
            if (availableCountries.contains(oldCountry)) selectedCountry = oldCountry;
            else if (availableCountries.contains("England")) selectedCountry = "England";
            else selectedCountry = firstOrEmpty(availableCountries);
        }

        ArrayList<String> availableLeagues = leagues(selectedCountry);
        String oldLeague = sp.getString("league", selectedLeague);
        if (selectedLeague.isEmpty() || !availableLeagues.contains(selectedLeague)) {
            selectedLeague = availableLeagues.contains(oldLeague) ? oldLeague : firstOrEmpty(availableLeagues);
        }

        ArrayList<String> availableTeams = teams(selectedCountry, selectedLeague);
        String oldTeam = sp.getString("team", "");
        selectedTeam = availableTeams.contains(oldTeam) ? oldTeam : firstOrEmpty(availableTeams);

        matchType = sp.getString("type", "all");
        matchLimit = 0;
    }

''', 'restore')

sub(r'    ArrayList<String> countries\(\) \{.*?\n    \}\n\n    ArrayList<String> leagues\(String country\) \{.*?\n    \}\n\n(?=    ArrayList<String> teams\()', '''    ArrayList<String> countries() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (LeagueSource source : catalog) {
            if (source.ready && !source.country.isEmpty()) all.add(source.country);
        }

        ArrayList<String> out = new ArrayList<>();
        for (String p : PREFERRED_COUNTRIES) if (all.contains(p)) out.add(p);

        ArrayList<String> others = new ArrayList<>();
        for (String s : all) if (!out.contains(s)) others.add(s);
        Collections.sort(others, String.CASE_INSENSITIVE_ORDER);
        out.addAll(others);
        return out;
    }

    ArrayList<String> leagues(String country) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (LeagueSource source : catalog) {
            if (source.ready && source.country.equals(country) && !source.league.isEmpty()) {
                set.add(source.league);
            }
        }
        ArrayList<String> out = new ArrayList<>(set);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

''', 'country league methods')

insert_pat = r'(    ArrayList<String> teams\(String country, String league\) \{.*?\n    \}\n\n)(?=    ArrayList<Match> filteredTeamMatches)'
m = re.search(insert_pat, s, re.S)
if not m:
    raise SystemExit('failed helper insertion')
helpers = r'''    void addIndexSources(String json, ArrayList<LeagueSource> out) throws Exception {
        Object rootValue = new JSONTokener(json).nextValue();
        if (!(rootValue instanceof JSONObject)) return;

        JSONObject rootObject = (JSONObject) rootValue;
        JSONArray arr = rootObject.optJSONArray("competitions");
        if (arr == null) return;

        LinkedHashMap<String, LeagueSource> unique = new LinkedHashMap<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !o.optBoolean("ready", false)) continue;

            LeagueSource source = LeagueSource.from(o);
            if (!source.valid()) continue;

            unique.put(source.country + "|" + source.league, source);
        }

        out.addAll(unique.values());
    }

    LeagueSource chooseInitialSource(ArrayList<LeagueSource> sources, String wantedCountry, String wantedLeague) {
        LeagueSource direct = findSource(sources, wantedCountry, wantedLeague);
        if (direct != null) return direct;

        android.content.SharedPreferences sp = getSharedPreferences("goals_stats", 0);
        LeagueSource saved = findSource(sources, sp.getString("country", "England"), sp.getString("league", "Premier League"));
        if (saved != null) return saved;

        LeagueSource premier = findSource(sources, "England", "Premier League");
        if (premier != null) return premier;

        return sources.isEmpty() ? null : sources.get(0);
    }

    LeagueSource findSource(List<LeagueSource> sources, String country, String league) {
        if (country == null || league == null) return null;
        for (LeagueSource source : sources) {
            if (source.ready && source.country.equals(country) && source.league.equals(league)) return source;
        }
        return null;
    }

    LeagueSource fallbackPremierLeagueSource() {
        LeagueSource source = new LeagueSource();
        source.country = "England";
        source.league = "Premier League";
        source.dataUrl = FALLBACK_PREMIER_LEAGUE_URL;
        source.season = 2024;
        source.leagueId = 39;
        source.matchesFinished = 380;
        source.ready = true;
        return source;
    }

    void saveLeagueCache(LeagueSource source, ArrayList<Match> matches) {
        JSONArray arr = new JSONArray();
        for (Match m : matches) arr.put(m.toJson());
        try (FileOutputStream out = openFileOutput(cacheFileName(source), MODE_PRIVATE)) {
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    ArrayList<Match> readLeagueCache(LeagueSource source) throws Exception {
        ArrayList<Match> out = new ArrayList<>();
        try (FileInputStream in = openFileInput(cacheFileName(source))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONArray arr = new JSONArray(bos.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < arr.length(); i++) {
                Match m = Match.from(arr.optJSONObject(i));
                if (m.valid()) out.add(m);
            }
        }
        sortMatchesNewestFirst(out);
        return out;
    }

    String cacheFileName(LeagueSource source) {
        String key = (source.country + "_" + source.league + "_" + source.season)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "goals_" + key + ".json";
    }

'''
s = s[:m.end(1)] + helpers + s[m.end(1):]

marker = '    static class Stats {'
if marker not in s:
    raise SystemExit('no Stats marker')
league_class = r'''    static class LeagueSource {
        String country = "", league = "", dataUrl = "", type = "";
        int leagueId = 0, season = 0, matchesFinished = 0;
        boolean ready = false;

        static LeagueSource from(JSONObject o) {
            LeagueSource source = new LeagueSource();
            if (o == null) return source;
            source.country = Match.clean(o.optString("country"));
            source.league = Match.clean(o.optString("league"));
            source.dataUrl = Match.clean(o.optString("dataUrl"));
            source.type = Match.clean(o.optString("type"));
            source.leagueId = o.optInt("leagueId", 0);
            source.season = o.optInt("season", 0);
            source.matchesFinished = o.optInt("matchesFinished", 0);
            source.ready = o.optBoolean("ready", false);
            return source;
        }

        boolean valid() {
            return ready && !country.isEmpty() && !league.isEmpty()
                    && dataUrl.startsWith("https://www.bestsportstats.com/");
        }
    }

'''
s = s.replace(marker, league_class + marker, 1)

JAVA.write_text(s, encoding='utf-8')

g = GRADLE.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 4', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '1.4-test'", g, count=1)
GRADLE.write_text(g, encoding='utf-8')

for required in ('INDEX_URL', 'loadLeagueData(', 'addIndexSources(', 'static class LeagueSource'):
    if required not in s:
        raise SystemExit('v1.4 patch incomplete: ' + required)
if 'BLOGGER_URLS' in s:
    raise SystemExit('v1.4 patch incomplete: old BLOGGER_URLS still present')
print('v1.4 multi-league patch applied')