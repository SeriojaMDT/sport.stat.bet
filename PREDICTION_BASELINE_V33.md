# BestSportStats prediction baseline

Baseline to preserve: BestSportStats V3.3 prediction logic.

- Current-season predictions use only the currently loaded league-season page (`LeagueData`) and only fixtures completed before the target match (`before`).
- The V3.3 prediction formulas, safety margins, empirical/model confidence blend, home/away context and opponent-strength weighting are the reference behavior that the user considers good.
- Archive seasons must not be silently mixed into current-season predictions, Daily matches, Top predictions, or current all-database Streaks.
- Archive seasons are for explicit season selection in Statistics (and any future archive-specific feature).
- If a future experiment intentionally mixes archive history and prediction performance worsens, revert prediction behavior to this V3.3 baseline.

V3.4 is intended to preserve this baseline while making the UI archive-aware; it does not change the prediction formula itself.
