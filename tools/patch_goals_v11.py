from pathlib import Path
import re

JAVA = Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
GRADLE = Path('app/build.gradle')
s = JAVA.read_text(encoding='utf-8')

# Fields / state.
s = s.replace(
    '    Button allButton, homeButton, awayButton, limitButton;\n',
    '    Button allButton, homeButton, awayButton;\n    Button ftTabButton, htTabButton, secondHtTabButton;\n'
)
s = s.replace(
    '    String matchType = "all";\n    int matchLimit = 10;\n',
    '    String matchType = "all";\n    String selectedPeriod = "FT";\n    int matchLimit = 10;\n'
)

# Main order: selected team card before "Meciuri analizate".
s = s.replace(
'''    void renderMain() {
        content.removeAllViews();
        renderSourceCard();
        renderSelectors();
        renderFilters();
        renderDashboard();
    }
''',
'''    void renderMain() {
        content.removeAllViews();
        renderSourceCard();
        renderSelectors();

        if (!selectedTeam.isEmpty()) {
            ArrayList<Match> allMatches = filteredTeamMatches(true);
            if (!allMatches.isEmpty()) {
                int shown = Math.min(matchLimit, allMatches.size());
                ArrayList<Match> limited = new ArrayList<>(allMatches.subList(0, shown));
                renderTeamHero(limited, allMatches.size());
            }
        }

        renderFilters();
        renderDashboard();
    }
''')

# Three horizontal selection buttons.
s = re.sub(
    r'    void renderSelectors\(\) \{.*?\n    \}\n\n(?=    void renderFilters\(\))',
'''    void renderSelectors() {
        LinearLayout c = card();
        c.addView(text("Selectează echipa", 19, TEXT, true));
        c.addView(text("Țară → campionat → echipă", 12, MUTED, false), marginTop(3, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        countryButton = compactSelectButton("Țară", selectedCountry);
        countryButton.setOnClickListener(v -> chooseCountry());
        row.addView(countryButton, new LinearLayout.LayoutParams(0, d(58), 1));

        leagueButton = compactSelectButton("Ligă", selectedLeague);
        leagueButton.setOnClickListener(v -> chooseLeague());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(58), 1);
        lp.leftMargin = d(7);
        row.addView(leagueButton, lp);

        teamButton = compactSelectButton("Echipă", selectedTeam);
        teamButton.setOnClickListener(v -> chooseTeam());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, d(58), 1);
        tp.leftMargin = d(7);
        row.addView(teamButton, tp);

        c.addView(row, marginTop(13, -2));
        content.addView(c, marginTop(10, -2));
    }

''', s, flags=re.S)

# All/Home/Away only; remove match-count dropdown.
s = re.sub(
    r'    void renderFilters\(\) \{.*?\n    \}\n\n(?=    void renderDashboard\(\))',
'''    void renderFilters() {
        LinearLayout c = card();
        c.addView(text("Meciuri analizate", 15, TEXT, true));

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER);
        allButton = segmentButton("Toate", "all".equals(matchType));
        homeButton = segmentButton("Acasă", "home".equals(matchType));
        awayButton = segmentButton("Deplasare", "away".equals(matchType));
        allButton.setOnClickListener(v -> setMatchType("all"));
        homeButton.setOnClickListener(v -> setMatchType("home"));
        awayButton.setOnClickListener(v -> setMatchType("away"));
        filters.addView(allButton, new LinearLayout.LayoutParams(0, d(48), 1));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, d(48), 1);
        fp.leftMargin = d(7);
        filters.addView(homeButton, fp);
        LinearLayout.LayoutParams fp2 = new LinearLayout.LayoutParams(0, d(48), 1);
        fp2.leftMargin = d(7);
        filters.addView(awayButton, fp2);
        c.addView(filters, marginTop(10, -2));

        TextView note = text("Se analizează automat ultimele 10 meciuri disponibile pentru filtrul ales.", 11, MUTED, false);
        c.addView(note, marginTop(8, -2));
        content.addView(c, marginTop(10, -2));
    }

''', s, flags=re.S)

# Dashboard: no recent-match cards; one selected period at a time.
s = re.sub(
    r'    void renderDashboard\(\) \{.*?\n    \}\n\n(?=    void renderTeamHero\()',
'''    void renderDashboard() {
        if (selectedTeam.isEmpty()) return;
        ArrayList<Match> matches = filteredTeamMatches(true);
        if (matches.isEmpty()) {
            LinearLayout c = card();
            TextView n = text("Nu există meciuri pentru filtrul selectat.", 14, MUTED, false);
            n.setGravity(Gravity.CENTER);
            c.addView(n);
            content.addView(c, marginTop(10, -2));
            return;
        }

        int shown = Math.min(matchLimit, matches.size());
        ArrayList<Match> limited = new ArrayList<>(matches.subList(0, shown));

        renderPeriodSelector();
        renderSelectedPeriodStats(limited);
        renderGoalMarkets(limited, selectedPeriod);
        renderDetailedTable(limited);
        renderRanking();
        renderLegend();
    }

''', s, flags=re.S)

s = s.replace(
'''        String scope = "all".equals(matchType) ? "Toate meciurile" : "home".equals(matchType) ? "Doar acasă" : "Doar deplasare";
        c.addView(text(scope + " • " + totalAvailable + " disponibile în baza încărcată", 12, MUTED, false), marginTop(8,-2));
''',
'''        String scope = "all".equals(matchType) ? "Toate" : "home".equals(matchType) ? "Acasă" : "Deplasare";
        c.addView(text(scope + " • statistica ultimelor " + limited.size() + " meciuri", 12, MUTED, false), marginTop(8,-2));
''')

# Replace horizontal period cards with three tabs + one card.
s = re.sub(
    r'    void renderPeriodCards\(ArrayList<Match> matches\) \{.*?\n(?=    View statLine\()',
'''    void renderPeriodSelector() {
        LinearLayout c = card();
        c.addView(text("Statistica pe perioadă", 15, TEXT, true));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        ftTabButton = periodTabButton("Final", "FT".equals(selectedPeriod));
        htTabButton = periodTabButton("Prima repriză", "HT".equals(selectedPeriod));
        secondHtTabButton = periodTabButton("A doua repriză", "2HT".equals(selectedPeriod));
        ftTabButton.setOnClickListener(v -> setPeriod("FT"));
        htTabButton.setOnClickListener(v -> setPeriod("HT"));
        secondHtTabButton.setOnClickListener(v -> setPeriod("2HT"));

        row.addView(ftTabButton, new LinearLayout.LayoutParams(0, d(48), 1));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, d(48), 1);
        p1.leftMargin = d(7);
        row.addView(htTabButton, p1);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, d(48), 1);
        p2.leftMargin = d(7);
        row.addView(secondHtTabButton, p2);
        c.addView(row, marginTop(10, -2));
        content.addView(c, marginTop(10, -2));
    }

    void setPeriod(String period) {
        selectedPeriod = period;
        renderMain();
    }

    Button periodTabButton(String label, boolean active) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(11);
        b.setSingleLine(false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        b.setTextColor(active ? Color.WHITE : MUTED);
        b.setBackground(rounded(active ? BLUE_DARK : Color.rgb(248,250,252), 12));
        b.setStateListAnimator(null);
        return b;
    }

    void renderSelectedPeriodStats(ArrayList<Match> matches) {
        Stats stats = calculateStats(matches, selectedTeam, selectedPeriod);
        String title = "FT".equals(selectedPeriod) ? "Final" : "HT".equals(selectedPeriod) ? "Prima repriză" : "A doua repriză";
        int accent = "FT".equals(selectedPeriod) ? BLUE_DARK : "HT".equals(selectedPeriod) ? PURPLE : AMBER;
        LinearLayout c = periodCard(title, stats, !"FT".equals(selectedPeriod), accent);
        content.addView(c, marginTop(10, -2));
    }

    LinearLayout periodCard(String title, Stats s, boolean half, int accent) {
        LinearLayout c = card();
        c.setPadding(d(15),d(15),d(15),d(15));
        TextView head = text(title, 18, accent, true);
        c.addView(head);
        c.addView(statLine("Victorii", s.wins + "  (" + pct(s.wins,s.games) + ")", GREEN), marginTop(10,-2));
        c.addView(statLine("Egaluri", s.draws + "  (" + pct(s.draws,s.games) + ")", AMBER), marginTop(6,-2));
        c.addView(statLine("Înfrângeri", s.losses + "  (" + pct(s.losses,s.games) + ")", RED), marginTop(6,-2));
        c.addView(divider(), marginTop(10,d(1)));
        c.addView(statLine("Goluri totale", String.valueOf(s.goalsFor + s.goalsAgainst), TEXT), marginTop(9,-2));
        c.addView(statLine("Medie / meci", one((s.goalsFor+s.goalsAgainst)/(double)Math.max(1,s.games)), TEXT), marginTop(5,-2));
        c.addView(statLine("Marcate", s.goalsFor + "  (" + one(s.goalsFor/(double)Math.max(1,s.games)) + ")", GREEN), marginTop(5,-2));
        c.addView(statLine("Primite", s.goalsAgainst + "  (" + one(s.goalsAgainst/(double)Math.max(1,s.games)) + ")", RED), marginTop(5,-2));
        c.addView(divider(), marginTop(10,d(1)));
        if (half) {
            c.addView(statLine("Over 0.5", s.over05 + "  (" + pct(s.over05,s.games) + ")", GREEN), marginTop(9,-2));
            c.addView(statLine("Over 1.5", s.over15 + "  (" + pct(s.over15,s.games) + ")", GREEN), marginTop(5,-2));
            c.addView(statLine("Over 2.5", s.over25 + "  (" + pct(s.over25,s.games) + ")", GREEN), marginTop(5,-2));
        } else {
            c.addView(statLine("Over 1.5", s.over15 + "  (" + pct(s.over15,s.games) + ")", GREEN), marginTop(9,-2));
            c.addView(statLine("Over 2.5", s.over25 + "  (" + pct(s.over25,s.games) + ")", GREEN), marginTop(5,-2));
            c.addView(statLine("Over 3.5", s.over35 + "  (" + pct(s.over35,s.games) + ")", GREEN), marginTop(5,-2));
        }
        return c;
    }

''', s, flags=re.S)

# Markets follow selected period.
s = s.replace('    void renderGoalMarkets(ArrayList<Match> matches) {', '    void renderGoalMarkets(ArrayList<Match> matches, String period) {')
s = s.replace('        c.addView(text("Goluri • FT", 19, TEXT, true));', '        String periodName = "FT".equals(period) ? "Final" : "HT".equals(period) ? "Prima repriză" : "A doua repriză";\n        c.addView(text("Goluri • " + periodName, 19, TEXT, true));')
s = s.replace('            int[] ft = parseScore(m.ft);\n            int sum = ft[0] + ft[1];', '            String periodScore = "FT".equals(period) ? m.ft : "HT".equals(period) ? m.ht : m.secondHt;\n            int[] ft = parseScore(periodScore);\n            int sum = ft[0] + ft[1];')

# Remove "Ultimele meciuri" block completely.
s = re.sub(r'    void renderRecentMatches\(ArrayList<Match> matches\) \{.*?\n(?=    void renderDetailedTable\()', '', s, flags=re.S)

# Narrow table columns, but preserve all text (wrap instead of ellipsis).
s = re.sub(
    r'    TableRow detailHeader\(\) \{.*?\n(?=    void renderRanking\(\))',
'''    TableRow detailHeader() {
        String[] heads = {"Data","Echipe","FT","1.5","2.5","3.5","E/O","BTTS","1<>2","HT","h0.5","h1.5","2HT","2h0.5","2h1.5","1","X","2"};
        int[] widths = {72,145,46,48,48,48,48,50,46,46,50,50,46,52,52,44,44,44};
        TableRow r = new TableRow(this);
        for (int i=0;i<heads.length;i++) {
            String h = heads[i];
            int bg = h.equals("FT")||h.equals("1.5")||h.equals("2.5")||h.equals("3.5")||h.equals("E/O")||h.equals("BTTS")||h.equals("1<>2") ? RED
                    : h.startsWith("h")||h.equals("HT") ? PURPLE
                    : h.equals("2HT")||h.startsWith("2h") ? AMBER : NAVY2;
            r.addView(tableCell(h, Color.WHITE, bg, true, widths[i]));
        }
        return r;
    }

    TableRow detailRow(Match m) {
        int[] ft = parseScore(m.ft);
        int[] ht = parseScore(m.ht);
        int[] sh = parseScore(m.secondHt);
        String[] vals = {
                m.date,
                m.home + " - " + m.away,
                m.ft,
                ou(ft,1.5), ou(ft,2.5), ou(ft,3.5), evenOdd(ft), btts(ft), halfMost(m),
                safeScore(m.ht), ou(ht,.5), ou(ht,1.5),
                safeScore(m.secondHt), ou(sh,.5), ou(sh,1.5),
                dash(m.odd1), dash(m.oddX), dash(m.odd2)
        };
        int[] widths = {72,145,46,48,48,48,48,50,46,46,50,50,46,52,52,44,44,44};
        TableRow r = new TableRow(this);
        for (int i=0;i<vals.length;i++) {
            String v = vals[i];
            int bg = Color.WHITE;
            int fg = TEXT;
            if (i==2 || i==9 || i==12) {
                bg = resultSoftColor(i==2?m.ft:i==9?m.ht:m.secondHt, m, selectedTeam);
                fg = resultColor(i==2?m.ft:i==9?m.ht:m.secondHt, m, selectedTeam);
            } else if ("Over".equals(v) || "Yes".equals(v) || "Even".equals(v) || "1".equals(v)) {
                bg = SOFT_GREEN; fg = GREEN;
            } else if ("Under".equals(v) || "No".equals(v) || "Odd".equals(v) || "2".equals(v)) {
                bg = SOFT_RED; fg = RED;
            } else if ("=".equals(v)) {
                bg = SOFT_AMBER; fg = AMBER;
            }
            r.addView(tableCell(v, fg, bg, i==2||i==9||i==12, widths[i]));
        }
        return r;
    }

    TextView tableCell(String value, int fg, int bg, boolean bold, int widthDp) {
        TextView v = text(value == null ? "" : value, 9, fg, bold);
        v.setGravity(Gravity.CENTER);
        v.setPadding(d(3), d(7), d(3), d(7));
        v.setBackground(bordered(bg, LINE, 0));
        v.setMaxLines(4);
        v.setEllipsize(null);
        v.setHorizontallyScrolling(false);
        v.setLayoutParams(new TableRow.LayoutParams(d(widthDp), -2));
        return v;
    }

''', s, flags=re.S)

# Remove match-limit chooser.
s = re.sub(r'    void chooseLimit\(\) \{.*?\n    \}\n\n(?=    interface ChoiceCallback)', '', s, flags=re.S)

# Add compact selector helper.
helper = '''    Button compactSelectButton(String label, String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        String shown = value == null || value.isEmpty() ? "Alege" : value;
        b.setText(label + "\\n" + shown + " ▾");
        b.setTextSize(10);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(d(4), d(3), d(4), d(3));
        b.setTextColor(TEXT);
        b.setBackground(bordered(Color.rgb(248,250,252), LINE, 13));
        b.setStateListAnimator(null);
        return b;
    }

'''
s = s.replace('    Button selectButton(String label, String value) {', helper + '    Button selectButton(String label, String value) {')

JAVA.write_text(s, encoding='utf-8')

g = GRADLE.read_text(encoding='utf-8')
g = g.replace('versionCode 1', 'versionCode 2').replace("versionName '1.0-test'", "versionName '1.1-test'")
GRADLE.write_text(g, encoding='utf-8')

# Fail loudly if an expected old UI element survived.
for forbidden in ('limitButton', 'renderRecentMatches(', 'renderPeriodCards('):
    if forbidden in s:
        raise SystemExit('Patch incomplete: ' + forbidden)
