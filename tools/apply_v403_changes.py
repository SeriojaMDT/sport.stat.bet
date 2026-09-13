from pathlib import Path
p=Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
s=p.read_text(encoding='utf-8')

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'missing pattern: {label}')
    s=s.replace(old,new,1)

rep('''    // Selecțiile manuale ale utilizatorului. Toate elementele din aceeași selecție trebuie să provină\n    // de la același bookmaker pentru ca produsul cotelor să aibă sens.\n    final ArrayList<ManualSelection> manualSelections = new ArrayList<>();\n    String manualBookmaker = "";\n''','''    // Selecțiile manuale sunt un instrument de notițe. Fiecare element își păstrează bookmakerul și cota\n    // găsite pentru acel market; selecțiile din aceeași listă pot proveni de la case diferite.\n    final ArrayList<ManualSelection> manualSelections = new ArrayList<>();\n    String manualBookmaker = ""; // păstrat doar pentru compatibilitate cu selecțiile salvate de versiunile vechi\n''','manual selection comment')

rep('content.setPadding(d(13),d(12),d(13),d(30));','content.setPadding(d(13),d(12),d(34),d(30));','content right gutter')

rep('''        backToTopButton=text("↑",23,Color.WHITE,true);\n        backToTopButton.setGravity(Gravity.CENTER);\n        backToTopButton.setBackground(rounded(Color.rgb(100,116,139),12));\n        backToTopButton.setElevation(d(2));\n''','''        backToTopButton=text("↑",18,Color.rgb(71,85,105),true);\n        backToTopButton.setGravity(Gravity.CENTER);\n        backToTopButton.setBackgroundColor(Color.TRANSPARENT);\n        backToTopButton.setElevation(0);\n''','transparent back-to-top')

rep('''        FrameLayout.LayoutParams btp=new FrameLayout.LayoutParams(d(30),d(42),Gravity.END|Gravity.BOTTOM);\n        btp.rightMargin=d(1);btp.bottomMargin=d(68);\n''','''        FrameLayout.LayoutParams btp=new FrameLayout.LayoutParams(d(22),d(30),Gravity.END|Gravity.BOTTOM);\n        btp.rightMargin=d(2);btp.bottomMargin=d(76);\n''','back-to-top size')

rep('''        for(OddsQuote q:list){\n            if(q==null||isExchangeBookmaker(q.bookmaker)||!wantedModule.equals(q.module)||!wantedSide.equalsIgnoreCase(q.side))continue;\n''','''        for(OddsQuote q:list){\n            // Cotele sub 1.18 nu sunt utile pentru lista de predicții. 1.18 rămâne acceptată.\n            if(q==null||q.odd<1.18-0.0001||isExchangeBookmaker(q.bookmaker)||!wantedModule.equals(q.module)||!wantedSide.equalsIgnoreCase(q.side))continue;\n''','minimum odds')

rep('''    void addManualSelection(TopPredictionItem it){\n        if(it==null||it.prediction==null||it.match==null||it.match.fixture==null)return;\n        DailyPrediction p=it.prediction;OddsQuote q=manualSelections.isEmpty()?p.bestQuote:quoteForBookmaker(p,manualBookmaker);\n        if(q==null){Toast.makeText(this,"Această selecție nu are cotă la "+manualBookmaker+". Păstrăm aceeași casă pentru cota totală.",Toast.LENGTH_LONG).show();return;}\n        Fixture f=it.match.fixture;ManualSelection m=new ManualSelection();m.key=manualSelectionKey(f.id,p);m.fixtureId=f.id;m.ts=f.ts;m.home=f.home.name;m.away=f.away.name;m.country=it.match.source.country;m.league=it.match.source.league;m.category=p.category;m.pick=p.label;m.confidence=p.confidence;m.bookmaker=q.bookmaker;m.odd=q.odd;\n        if(manualSelections.isEmpty())manualBookmaker=q.bookmaker;manualSelections.add(m);persistManualSelections();updateSelectionFloatButton();render();\n    }\n''','''    void addManualSelection(TopPredictionItem it){\n        if(it==null||it.prediction==null||it.match==null||it.match.fixture==null)return;\n        DailyPrediction p=it.prediction;OddsQuote q=p.bestQuote;\n        if(q==null)return;\n        Fixture f=it.match.fixture;ManualSelection m=new ManualSelection();m.key=manualSelectionKey(f.id,p);m.fixtureId=f.id;m.ts=f.ts;m.home=f.home.name;m.away=f.away.name;m.country=it.match.source.country;m.league=it.match.source.league;m.category=p.category;m.pick=p.label;m.confidence=p.confidence;m.bookmaker=q.bookmaker;m.odd=q.odd;\n        manualSelections.add(m);manualBookmaker="";persistManualSelections();updateSelectionFloatButton();render();\n    }\n''','mixed bookmaker add')

rep('''        TextView bk=text(manualBookmaker,12,PURPLE,true);bk.setGravity(Gravity.CENTER);box.addView(bk);\n''','''        TextView bk=text("Cote orientative • selecțiile pot proveni de la case diferite",10,MUTED,true);bk.setGravity(Gravity.CENTER);box.addView(bk);\n''','dialog note')

rep('''            TextView odd=text(formatOdds(m.odd),11,PURPLE,true);odd.setGravity(Gravity.END);row.addView(odd,new LinearLayout.LayoutParams(d(58),-2));card.addView(row,mt(2,-2));\n''','''            TextView odd=text(formatOdds(m.odd)+" • "+m.bookmaker,10,PURPLE,true);odd.setGravity(Gravity.END);odd.setSingleLine(true);odd.setEllipsize(TextUtils.TruncateAt.END);row.addView(odd,new LinearLayout.LayoutParams(d(118),-2));card.addView(row,mt(2,-2));\n''','dialog bookmaker display')

rep('''        TextView total=text("Cotă totală: "+formatOdds(manualTotalOdds()),17,GREEN,true);total.setGravity(Gravity.CENTER);box.addView(total,mt(10,-2));\n''','''        TextView total=text("Cotă totală orientativă: "+formatOdds(manualTotalOdds()),17,GREEN,true);total.setGravity(Gravity.CENTER);box.addView(total,mt(10,-2));\n''','total label')

rep('''    void shareManualSelections(){\n        if(manualSelections.isEmpty())return;StringBuilder b=new StringBuilder("Selecțiile mele • ").append(manualBookmaker).append('\\n');int i=1;for(ManualSelection m:manualSelections)b.append(i++).append(". ").append(m.home).append(" – ").append(m.away).append(" | ").append(translateMarketLabel(m.pick)).append(" | cotă ").append(formatOdds(m.odd)).append('\\n');b.append("Cotă totală: ").append(formatOdds(manualTotalOdds()));\n''','''    void shareManualSelections(){\n        if(manualSelections.isEmpty())return;StringBuilder b=new StringBuilder("Selecțiile mele • cote orientative").append('\\n');int i=1;for(ManualSelection m:manualSelections)b.append(i++).append(". ").append(m.home).append(" – ").append(m.away).append(" | ").append(translateMarketLabel(m.pick)).append(" | cotă ").append(formatOdds(m.odd)).append(" • ").append(m.bookmaker).append('\\n');b.append("Cotă totală orientativă: ").append(formatOdds(manualTotalOdds()));\n''','share mixed bookmakers')

p.write_text(s,encoding='utf-8')
print('V4.0.3 changes applied', len(s))
