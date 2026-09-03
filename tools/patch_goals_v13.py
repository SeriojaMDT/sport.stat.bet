from pathlib import Path
import re

JAVA = Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
s = JAVA.read_text(encoding='utf-8')


def replace_once(pattern, replacement, label):
    global s
    s, count = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if count != 1:
        raise SystemExit('v1.3 patch failed: ' + label)


# Use every available match for the selected team/filter and keep the filters
# inside the selected-team card instead of a separate block.
replace_once(
    r'    void renderMain\(\) \{.*?\n    \}\n\n(?=    void renderSelectors\(\))',
'''    void renderMain() {
        content.removeAllViews();
        renderSourceCard();
        renderSelectors();

        if (!selectedTeam.isEmpty()) {
            ArrayList<Match> allMatches = filteredTeamMatches(true);
            if (!allMatches.isEmpty()) {
                renderTeamHero(new ArrayList<>(allMatches), allMatches.size());
            }
        }

        renderDashboard();
    }

''',
    'renderMain'
)

replace_once(
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

        ArrayList<Match> all = new ArrayList<>(matches);
        renderCombinedPeriodStats(all);
        renderDetailedTable(all);
        renderRanking();
        renderLegend();
    }

''',
    'renderDashboard'
)

replace_once(
    r'    void renderTeamHero\(ArrayList<Match> limited, int totalAvailable\) \{.*?\n    \}\n\n(?=    void renderPeriodSelector\(\))',
'''    void renderTeamHero(ArrayList<Match> matches, int totalAvailable) {
        LinearLayout c = card();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(selectedTeam, 25, TEXT, true));
        names.addView(text(selectedLeague + " • " + selectedCountry, 13, MUTED, false), marginTop(3, -2));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        TextView count = text(matches.size() + " meciuri", 12, BLUE_DARK, true);
        count.setGravity(Gravity.CENTER);
        count.setPadding(d(12), d(8), d(12), d(8));
        count.setBackground(rounded(Color.rgb(239,246,255), 20));
        head.addView(count);
        c.addView(head);

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
        c.addView(filters, marginTop(14, -2));

        String scope = "all".equals(matchType) ? "Toate meciurile sezonului"
                : "home".equals(matchType) ? "Meciurile de acasă" : "Meciurile din deplasare";
        c.addView(text(scope + " • toate cele " + matches.size() + " meciuri disponibile", 11, MUTED, false), marginTop(8, -2));

        content.addView(c, marginTop(10, -2));
    }

''',
    'renderTeamHero'
)

# One compact statistics card: period tabs + W/D/L + goals + O/U + BTTS + odd/even.
# This removes the duplicated separate "Goluri" block from the visible dashboard.
replace_once(
    r'    void renderPeriodSelector\(\) \{.*?\n(?=    View statLine\(\))',
'''    void renderCombinedPeriodStats(ArrayList<Match> matches) {
        Stats st = calculateStats(matches, selectedTeam, selectedPeriod);
        String periodName = "FT".equals(selectedPeriod) ? "Final"
                : "HT".equals(selectedPeriod) ? "Prima repriză" : "A doua repriză";
        int accent = "FT".equals(selectedPeriod) ? BLUE_DARK
                : "HT".equals(selectedPeriod) ? PURPLE : AMBER;

        int bttsYes = 0;
        int even = 0;
        for (Match m : matches) {
            String periodScore = "FT".equals(selectedPeriod) ? m.ft
                    : "HT".equals(selectedPeriod) ? m.ht : m.secondHt;
            int[] score = parseScore(periodScore);
            if (score[0] > 0 && score[1] > 0) bttsYes++;
            if (((score[0] + score[1]) % 2) == 0) even++;
        }
        int bttsNo = Math.max(0, st.games - bttsYes);
        int odd = Math.max(0, st.games - even);

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

        c.addView(divider(), marginTop(13, d(1)));
        c.addView(text(periodName, 19, accent, true), marginTop(12, -2));
        c.addView(statLine("Meciuri", String.valueOf(st.games), TEXT), marginTop(10, -2));
        c.addView(statLine("Victorii", st.wins + "  (" + pct(st.wins, st.games) + ")", GREEN), marginTop(6, -2));
        c.addView(statLine("Egaluri", st.draws + "  (" + pct(st.draws, st.games) + ")", AMBER), marginTop(6, -2));
        c.addView(statLine("Înfrângeri", st.losses + "  (" + pct(st.losses, st.games) + ")", RED), marginTop(6, -2));

        c.addView(divider(), marginTop(10, d(1)));
        c.addView(statLine("Goluri totale", String.valueOf(st.goalsFor + st.goalsAgainst), TEXT), marginTop(9, -2));
        c.addView(statLine("Medie / meci", one((st.goalsFor + st.goalsAgainst) / (double)Math.max(1, st.games)), TEXT), marginTop(5, -2));
        c.addView(statLine("Marcate", st.goalsFor + "  (" + one(st.goalsFor / (double)Math.max(1, st.games)) + ")", GREEN), marginTop(5, -2));
        c.addView(statLine("Primite", st.goalsAgainst + "  (" + one(st.goalsAgainst / (double)Math.max(1, st.games)) + ")", RED), marginTop(5, -2));

        c.addView(divider(), marginTop(10, d(1)));
        c.addView(statLine("Over 0.5", st.over05 + "  (" + pct(st.over05, st.games) + ")", GREEN), marginTop(9, -2));
        c.addView(statLine("Over 1.5", st.over15 + "  (" + pct(st.over15, st.games) + ")", GREEN), marginTop(5, -2));
        c.addView(statLine("Over 2.5", st.over25 + "  (" + pct(st.over25, st.games) + ")", GREEN), marginTop(5, -2));
        c.addView(statLine("Over 3.5", st.over35 + "  (" + pct(st.over35, st.games) + ")", GREEN), marginTop(5, -2));

        c.addView(divider(), marginTop(10, d(1)));
        c.addView(statLine("BTTS • Da", bttsYes + "  (" + pct(bttsYes, st.games) + ")", GREEN), marginTop(9, -2));
        c.addView(statLine("BTTS • Nu", bttsNo + "  (" + pct(bttsNo, st.games) + ")", RED), marginTop(5, -2));
        c.addView(statLine("Par", even + "  (" + pct(even, st.games) + ")", TEXT), marginTop(5, -2));
        c.addView(statLine("Impar", odd + "  (" + pct(odd, st.games) + ")", TEXT), marginTop(5, -2));

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

''',
    'period statistics block'
)

# Remove the three odds columns (1 / X / 2) from the detailed table.
replace_once(
    r'    TableRow detailHeader\(\) \{.*?\n(?=    void renderRanking\(\))',
'''    TableRow detailHeader() {
        String[] heads = {"Data","Echipe","FT","1.5","2.5","3.5","E/O","BTTS","1<>2","HT","h0.5","h1.5","2HT","2h0.5","2h1.5"};
        int[] widths = {72,145,46,48,48,48,48,50,46,46,50,50,46,52,52};
        TableRow r = new TableRow(this);
        for (int i = 0; i < heads.length; i++) {
            String h = heads[i];
            int bg = h.equals("FT") || h.equals("1.5") || h.equals("2.5") || h.equals("3.5") || h.equals("E/O") || h.equals("BTTS") || h.equals("1<>2") ? RED
                    : h.startsWith("h") || h.equals("HT") ? PURPLE
                    : h.equals("2HT") || h.startsWith("2h") ? AMBER : NAVY2;
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
                ou(ft, 1.5), ou(ft, 2.5), ou(ft, 3.5), evenOdd(ft), btts(ft), halfMost(m),
                safeScore(m.ht), ou(ht, .5), ou(ht, 1.5),
                safeScore(m.secondHt), ou(sh, .5), ou(sh, 1.5)
        };
        int[] widths = {72,145,46,48,48,48,48,50,46,46,50,50,46,52,52};
        TableRow r = new TableRow(this);
        for (int i = 0; i < vals.length; i++) {
            String v = vals[i];
            int bg = Color.WHITE;
            int fg = TEXT;
            if (i == 2 || i == 9 || i == 12) {
                bg = resultSoftColor(i == 2 ? m.ft : i == 9 ? m.ht : m.secondHt, m, selectedTeam);
                fg = resultColor(i == 2 ? m.ft : i == 9 ? m.ht : m.secondHt, m, selectedTeam);
            } else if ("Over".equals(v) || "Yes".equals(v) || "Even".equals(v) || "1".equals(v)) {
                bg = SOFT_GREEN;
                fg = GREEN;
            } else if ("Under".equals(v) || "No".equals(v) || "Odd".equals(v) || "2".equals(v)) {
                bg = SOFT_RED;
                fg = RED;
            } else if ("=".equals(v)) {
                bg = SOFT_AMBER;
                fg = AMBER;
            }
            r.addView(tableCell(v, fg, bg, i == 2 || i == 9 || i == 12, widths[i]));
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

''',
    'detailed table'
)

# Sanity checks: the visible dashboard must no longer be capped to 10 or call
# the duplicated stats blocks, and the table must no longer expose odds.
main_block = re.search(r'    void renderMain\(\).*?(?=    void renderSelectors\()', s, re.S).group(0)
dash_block = re.search(r'    void renderDashboard\(\).*?(?=    void renderTeamHero\()', s, re.S).group(0)
if 'Math.min(matchLimit' in main_block or 'Math.min(matchLimit' in dash_block:
    raise SystemExit('v1.3 patch incomplete: 10-match limit is still active')
if 'renderGoalMarkets(' in dash_block or 'renderSelectedPeriodStats(' in dash_block:
    raise SystemExit('v1.3 patch incomplete: duplicate stats are still visible')

JAVA.write_text(s, encoding='utf-8')
