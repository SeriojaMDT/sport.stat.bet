from pathlib import Path
import re

JAVA = Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
GRADLE = Path('app/build.gradle')
s = JAVA.read_text(encoding='utf-8')


def sub(pattern, repl, label, flags=re.S):
    global s
    s2, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'v1.5 patch failed {label}: {n}')
    s = s2

# Compact online summary with totals across the full ready catalog.
sub(
    r'    void renderSourceCard\(\) \{.*?\n    \}\n\n(?=    void renderSelectors\(\))',
'''    void renderSourceCard() {
        LinearLayout c = compactCard();

        LinkedHashSet<String> countrySet = new LinkedHashSet<>();
        int totalMatches = 0;
        for (LeagueSource source : catalog) {
            if (!source.ready) continue;
            if (!source.country.isEmpty()) countrySet.add(source.country);
            totalMatches += Math.max(0, source.matchesFinished);
        }

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Date online", 11, MUTED, true));

        String summary = countrySet.size() + " țări • "
                + catalog.size() + " competiții • "
                + NumberFormat.getIntegerInstance(new Locale("ro", "RO")).format(totalMatches) + " meciuri";
        sourceStatus = text(summary, 14, TEXT, true);
        left.addView(sourceStatus, marginTop(1, -2));

        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView live = chip("● ONLINE", GREEN, SOFT_GREEN);
        row.addView(live);
        c.addView(row);

        content.addView(c);
    }

''',
    'source summary'
)

# More compact team selectors.
sub(
    r'    void renderSelectors\(\) \{.*?\n    \}\n\n(?=    void renderFilters\(\))',
'''    void renderSelectors() {
        LinearLayout c = compactCard();
        c.addView(text("Selectează echipa", 17, TEXT, true));
        c.addView(text("Țară → campionat → echipă", 10, MUTED, true), marginTop(1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        countryButton = compactSelectButton("Țară", selectedCountry);
        countryButton.setOnClickListener(v -> chooseCountry());
        row.addView(countryButton, new LinearLayout.LayoutParams(0, d(46), 1));

        leagueButton = compactSelectButton("Ligă", selectedLeague);
        leagueButton.setOnClickListener(v -> chooseLeague());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(46), 1);
        lp.leftMargin = d(5);
        row.addView(leagueButton, lp);

        teamButton = compactSelectButton("Echipă", selectedTeam);
        teamButton.setOnClickListener(v -> chooseTeam());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, d(46), 1);
        tp.leftMargin = d(5);
        row.addView(teamButton, tp);

        c.addView(row, marginTop(7, -2));
        content.addView(c, marginTop(7, -2));
    }

''',
    'compact selectors'
)

# Compact selected-team card and filter buttons.
sub(
    r'    void renderTeamHero\(ArrayList<Match> matches, int totalAvailable\) \{.*?\n    \}\n\n(?=    void renderCombinedPeriodStats\()',
'''    void renderTeamHero(ArrayList<Match> matches, int totalAvailable) {
        LinearLayout c = compactCard();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(selectedTeam, 21, TEXT, true));
        names.addView(text(selectedLeague + " • " + selectedCountry, 11, MUTED, true), marginTop(1, -2));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        TextView count = text(matches.size() + " meciuri", 11, BLUE_DARK, true);
        count.setGravity(Gravity.CENTER);
        count.setPadding(d(9), d(5), d(9), d(5));
        count.setBackground(rounded(Color.rgb(239,246,255), 18));
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
        filters.addView(allButton, new LinearLayout.LayoutParams(0, d(40), 1));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, d(40), 1);
        fp.leftMargin = d(5);
        filters.addView(homeButton, fp);
        LinearLayout.LayoutParams fp2 = new LinearLayout.LayoutParams(0, d(40), 1);
        fp2.leftMargin = d(5);
        filters.addView(awayButton, fp2);
        c.addView(filters, marginTop(8, -2));

        String scope = "all".equals(matchType) ? "Toate meciurile sezonului"
                : "home".equals(matchType) ? "Meciurile de acasă" : "Meciurile din deplasare";
        c.addView(text(scope + " • " + matches.size() + " meciuri", 10, MUTED, true), marginTop(5, -2));

        content.addView(c, marginTop(7, -2));
    }

''',
    'compact team hero'
)

# Compact the top of the period statistics card and its tabs.
s = s.replace('        LinearLayout c = card();\n        c.addView(text("Statistica pe perioadă", 15, TEXT, true));',
              '        LinearLayout c = compactCard();\n        c.addView(text("Statistica pe perioadă", 14, TEXT, true));', 1)
s = s.replace('        row.addView(ftTabButton, new LinearLayout.LayoutParams(0, d(48), 1));',
              '        row.addView(ftTabButton, new LinearLayout.LayoutParams(0, d(40), 1));', 1)
s = s.replace('        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, d(48), 1);',
              '        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, d(40), 1);', 1)
s = s.replace('        p1.leftMargin = d(7);', '        p1.leftMargin = d(5);', 1)
s = s.replace('        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, d(48), 1);',
              '        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, d(40), 1);', 1)
s = s.replace('        p2.leftMargin = d(7);', '        p2.leftMargin = d(5);', 1)
s = s.replace('        c.addView(row, marginTop(10, -2));', '        c.addView(row, marginTop(7, -2));', 1)

# Reset analysis filters whenever country, league, or team changes.
sub(
    r'    void chooseCountry\(\) \{.*?\n    \}\n\n    void chooseLeague\(\) \{.*?\n    \}\n\n    void chooseTeam\(\) \{.*?\n    \}\n',
'''    void chooseCountry() {
        ArrayList<String> list = countries();
        showChoice("Alege țara", list, selectedCountry, value -> {
            selectedCountry = value;
            selectedLeague = firstOrEmpty(leagues(selectedCountry));
            selectedTeam = "";
            resetAnalysisTabs();
            persistSelections();
            loadLeagueData(selectedCountry, selectedLeague);
        });
    }

    void chooseLeague() {
        ArrayList<String> list = leagues(selectedCountry);
        showChoice("Alege campionatul", list, selectedLeague, value -> {
            selectedLeague = value;
            selectedTeam = "";
            resetAnalysisTabs();
            persistSelections();
            loadLeagueData(selectedCountry, selectedLeague);
        });
    }

    void chooseTeam() {
        ArrayList<String> list = teams(selectedCountry, selectedLeague);
        showChoice("Alege echipa", list, selectedTeam, value -> {
            selectedTeam = value;
            resetAnalysisTabs();
            persistSelections();
            renderMain();
        });
    }

    void resetAnalysisTabs() {
        matchType = "all";
        selectedPeriod = "FT";
    }
''',
    'selection reset'
)

# Make selector and segment labels more prominent while staying compact.
sub(
    r'    Button compactSelectButton\(String label, String value\) \{.*?\n    \}\n\n(?=    Button selectButton\()',
'''    Button compactSelectButton(String label, String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        String shown = value == null || value.isEmpty() ? "Alege" : value;
        b.setText(label + System.lineSeparator() + shown + " ▾");
        b.setTextSize(10);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(d(3), d(1), d(3), d(1));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(TEXT);
        b.setBackground(bordered(Color.rgb(248,250,252), LINE, 11));
        b.setStateListAnimator(null);
        return b;
    }

''',
    'compact selector button'
)

sub(
    r'    Button segmentButton\(String label, boolean active\) \{.*?\n    \}\n\n(?=    TextView text\()',
'''    Button segmentButton(String label, boolean active) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(active ? Color.WHITE : MUTED);
        b.setBackground(rounded(active ? BLUE_DARK : Color.rgb(248,250,252), 10));
        b.setStateListAnimator(null);
        return b;
    }

''',
    'segment button'
)

sub(
    r'    Button periodTabButton\(String label, boolean active\) \{.*?\n    \}\n\n(?=    View statLine\()',
'''    Button periodTabButton(String label, boolean active) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(10);
        b.setSingleLine(false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(active ? Color.WHITE : MUTED);
        b.setBackground(rounded(active ? BLUE_DARK : Color.rgb(248,250,252), 10));
        b.setStateListAnimator(null);
        return b;
    }

''',
    'period tab button'
)

# Add a compact card helper without changing the rest of the app.
marker = '    LinearLayout card() {'
if marker not in s:
    raise SystemExit('v1.5 patch failed compactCard marker')
compact_helper = '''    LinearLayout compactCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(d(11), d(10), d(11), d(10));
        c.setBackground(rounded(Color.WHITE, 16));
        c.setElevation(d(2));
        return c;
    }

'''
s = s.replace(marker, compact_helper + marker, 1)

# Sanity checks.
for required in ('resetAnalysisTabs()', 'compactCard()', 'countrySet.size() + " țări', 'NumberFormat.getIntegerInstance'):
    if required not in s:
        raise SystemExit('v1.5 patch incomplete: ' + required)

JAVA.write_text(s, encoding='utf-8')

g = GRADLE.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 5', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '1.5-test'", g, count=1)
GRADLE.write_text(g, encoding='utf-8')

print('v1.5 compact dashboard + reset filters patch applied')
