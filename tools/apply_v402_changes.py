from pathlib import Path

path = Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
s = path.read_text(encoding='utf-8')


def repl(old, new, label):
    global s
    if old not in s:
        raise SystemExit('V4.0.2 replace failed: ' + label)
    s = s.replace(old, new, 1)

repl(
'''    boolean draggingFastScroll = false;\n    final Runnable softenFastScroll = () -> { if(fastScrollThumb!=null && !draggingFastScroll) fastScrollThumb.setAlpha(.35f); };\n''',
'''    boolean draggingFastScroll = false;\n    final Runnable softenFastScroll = () -> { if(fastScrollThumb!=null && !draggingFastScroll) fastScrollThumb.setAlpha(.35f); };\n    final Runnable hideBackToTop = () -> {\n        if(backToTopButton==null || backToTopButton.getVisibility()!=View.VISIBLE) return;\n        backToTopButton.animate().cancel();\n        backToTopButton.animate().alpha(0f).setDuration(220).withEndAction(() -> {\n            if(backToTopButton!=null && backToTopButton.getAlpha()<=0.01f) backToTopButton.setVisibility(View.GONE);\n        }).start();\n    };\n''',
'add back-to-top fade runnable')

repl(
'''    @Override protected void onDestroy() {\n        executor.shutdownNow();\n        super.onDestroy();\n    }\n''',
'''    @Override protected void onDestroy() {\n        uiHandler.removeCallbacks(softenFastScroll);\n        uiHandler.removeCallbacks(hideBackToTop);\n        executor.shutdownNow();\n        super.onDestroy();\n    }\n''',
'onDestroy cleanup')

repl(
'''        backToTopButton=text("↑",25,Color.WHITE,true);\n        backToTopButton.setGravity(Gravity.CENTER);\n        backToTopButton.setBackground(rounded(NAVY2,24));\n        backToTopButton.setElevation(d(5));\n        backToTopButton.setVisibility(View.GONE);\n        backToTopButton.setOnClickListener(v->scroll.smoothScrollTo(0,0));\n        FrameLayout.LayoutParams btp=new FrameLayout.LayoutParams(d(48),d(48),Gravity.END|Gravity.BOTTOM);\n        btp.rightMargin=d(13);btp.bottomMargin=d(76);\n        scrollFrame.addView(backToTopButton,btp);\n''',
'''        backToTopButton=text("↑",23,Color.WHITE,true);\n        backToTopButton.setGravity(Gravity.CENTER);\n        backToTopButton.setBackground(rounded(Color.rgb(100,116,139),12));\n        backToTopButton.setElevation(d(2));\n        backToTopButton.setAlpha(0f);\n        backToTopButton.setVisibility(View.GONE);\n        backToTopButton.setOnClickListener(v->{ uiHandler.removeCallbacks(hideBackToTop); scroll.smoothScrollTo(0,0); });\n        FrameLayout.LayoutParams btp=new FrameLayout.LayoutParams(d(30),d(42),Gravity.END|Gravity.BOTTOM);\n        btp.rightMargin=d(1);btp.bottomMargin=d(68);\n        scrollFrame.addView(backToTopButton,btp);\n''',
'restyle back-to-top')

repl(
'''        if(max<=d(8)){\n            fastScrollThumb.setVisibility(View.GONE);\n            if(backToTopButton!=null)backToTopButton.setVisibility(View.GONE);\n            return;\n        }\n''',
'''        if(max<=d(8)){\n            fastScrollThumb.setVisibility(View.GONE);\n            uiHandler.removeCallbacks(hideBackToTop);\n            if(backToTopButton!=null){backToTopButton.animate().cancel();backToTopButton.setAlpha(0f);backToTopButton.setVisibility(View.GONE);}\n            return;\n        }\n''',
'hide arrow on short pages')

repl(
'''        fastScrollThumb.setLayoutParams(lp);\n        if(backToTopButton!=null)backToTopButton.setVisibility(scroll.getScrollY()>viewport*2?View.VISIBLE:View.GONE);\n        uiHandler.removeCallbacks(softenFastScroll);\n        uiHandler.postDelayed(softenFastScroll,1400);\n''',
'''        fastScrollThumb.setLayoutParams(lp);\n        if(backToTopButton!=null){\n            if(scroll.getScrollY()>viewport*2){\n                backToTopButton.animate().cancel();\n                backToTopButton.setVisibility(View.VISIBLE);\n                backToTopButton.setAlpha(.86f);\n                uiHandler.removeCallbacks(hideBackToTop);\n                uiHandler.postDelayed(hideBackToTop,900);\n            }else{\n                uiHandler.removeCallbacks(hideBackToTop);\n                backToTopButton.animate().cancel();\n                backToTopButton.setAlpha(0f);\n                backToTopButton.setVisibility(View.GONE);\n            }\n        }\n        uiHandler.removeCallbacks(softenFastScroll);\n        uiHandler.postDelayed(softenFastScroll,1400);\n''',
'fade arrow after scroll stops')

repl(
'''        for(OddsQuote q:list){\n            if(q==null||!wantedModule.equals(q.module)||!wantedSide.equalsIgnoreCase(q.side))continue;\n            if("market".equals(p.type)&&(Double.isNaN(p.threshold)||Double.isNaN(q.line)||Math.abs(p.threshold-q.line)>.011))continue;\n            p.matchedOdds.add(q);\n        }\n''',
'''        for(OddsQuote q:list){\n            if(q==null||isExchangeBookmaker(q.bookmaker)||!wantedModule.equals(q.module)||!wantedSide.equalsIgnoreCase(q.side))continue;\n            if("market".equals(p.type)&&(Double.isNaN(p.threshold)||Double.isNaN(q.line)||Math.abs(p.threshold-q.line)>.011))continue;\n            p.matchedOdds.add(q);\n        }\n''',
'exclude exchange prices in Android')

repl(
'''    int bookmakerPriority(String name){\n        String n=normBookmaker(name);\n        if(n.contains("bet365"))return 0;\n        if(n.contains("williamhill")||n.contains("william hill"))return 1;\n        if(n.contains("unibet"))return 2;\n        if(n.contains("betvictor")||n.contains("bet victor"))return 3;\n        if(n.contains("betfair"))return 4;\n        if(n.contains("pinnacle"))return 5;\n        if(n.contains("bwin"))return 6;\n        return 20;\n    }\n    String normBookmaker(String s){return String.valueOf(s==null?"":s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}\n''',
'''    int bookmakerPriority(String name){\n        String n=normBookmaker(name);\n        if(n.contains("bet365"))return 0;\n        if(n.contains("williamhill")||n.contains("william hill"))return 1;\n        if(n.contains("unibet"))return 2;\n        if(n.contains("betvictor")||n.contains("bet victor"))return 3;\n        if(n.contains("pinnacle"))return 4;\n        if(n.contains("bwin"))return 5;\n        if(n.contains("betway"))return 6;\n        if(n.contains("betano"))return 7;\n        if(n.contains("1xbet"))return 8;\n        if(n.contains("ladbrokes"))return 9;\n        if(n.contains("coral"))return 10;\n        return 20;\n    }\n    boolean isExchangeBookmaker(String name){\n        String n=normBookmaker(name);\n        return n.contains("betfair")||n.contains("smarkets")||n.contains("matchbook");\n    }\n    String normBookmaker(String s){return String.valueOf(s==null?"":s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}\n''',
'bookmaker safety priority')

path.write_text(s, encoding='utf-8')
print('Football Stats Analyzer V4.0.2 changes applied:', len(s))
