package ph.lottopicker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Draw schedule, latest results with winners, current jackpots, lucky picks and saved picks. */
public class MainActivity extends Activity {

    private LinearLayout list;
    private TextView updated;
    private Button refreshBtn;
    private float dp;
    private final Random rnd = new Random();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean loading;

    private static final int INK = 0xFF1C2333, SOFT = 0xFF5F6B7A, PAPER = 0xFFF3F5F9, BLUE = 0xFF12307A, GOLD = 0xFFFFC928;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        root.setFitsSystemWindows(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(BLUE);
        head.setPadding(px(18), px(14), px(10), px(14));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView title = tv("🎱 Lotto Picker", 22, Color.WHITE, true);
        updated = tv("", 13, 0xCCFFFFFF, false);
        ht.addView(title);
        ht.addView(updated);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        refreshBtn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        refreshBtn.setText("↻ Refresh");
        refreshBtn.setAllCaps(false);
        refreshBtn.setTextColor(GOLD);
        refreshBtn.setTextSize(16);
        refreshBtn.setOnClickListener(v -> refresh(true));
        head.addView(refreshBtn);
        root.addView(head);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(12), px(12), px(12), px(24));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        long age = System.currentTimeMillis() - Lotto.updatedAt(this);
        if (age > 20 * 60 * 1000L) refresh(false);
        else render();
    }

    private void refresh(boolean manual) {
        if (loading) return;
        loading = true;
        refreshBtn.setEnabled(false);
        refreshBtn.setText("Loading…");
        new Thread(() -> {
            String msg = Lotto.refresh(this, 8);
            ui.post(() -> {
                loading = false;
                refreshBtn.setEnabled(true);
                refreshBtn.setText("↻ Refresh");
                render();
                LottoWidget.updateAll(this);
                if (manual || !msg.startsWith("Updated")) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ---------------- helpers ----------------

    private int px(float v) { return Math.round(v * dp); }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, float r, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(r));
        if (stroke != 0) g.setStroke(px(1), stroke);
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Color.WHITE, 16, 0x14000000));
        c.setElevation(px(2));
        c.setPadding(px(16), px(14), px(16), px(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, px(12));
        list.addView(c, lp);
        return c;
    }

    private void section(String s) {
        TextView t = tv(s, 13, SOFT, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        t.setPadding(px(4), px(8), 0, px(6));
        list.addView(t);
    }

    private LinearLayout balls(String numbers, int color, String highlightAgainst) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, px(6), 0, px(6));
        List<String> other = new ArrayList<>();
        if (highlightAgainst != null) for (String s : highlightAgainst.split("-")) other.add(s);
        for (String n : numbers.split("-")) {
            TextView b = tv(n, 17, Color.WHITE, true);
            b.setGravity(Gravity.CENTER);
            boolean hit = highlightAgainst != null && other.remove(n);
            int fill = highlightAgainst == null ? color : (hit ? 0xFF2E7D32 : 0xFFB0B8C4);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(fill);
            b.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(38), px(38));
            lp.setMargins(0, 0, px(6), 0);
            row.addView(b, lp);
        }
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(h);
        return wrap;
    }

    private String winnersText(Lotto.Game g, Lotto.Result r) {
        if (r.winners < 0) return "Winners: not yet reported";
        if (g.major) {
            if (r.winners == 0) return "Jackpot winners: none (the jackpot rolls over)";
            return "Jackpot winners: " + r.winners + (r.winners == 1 ? " lucky bettor! 🎉" : " bettors (shared) 🎉");
        }
        return "Winners: " + String.format(java.util.Locale.US, "%,d", r.winners);
    }

    // ---------------- screen ----------------

    private void render() {
        list.removeAllViews();
        long up = Lotto.updatedAt(this);
        updated.setText(up == 0 ? "Tap Refresh to load results" : "Updated " + DateUtils.getRelativeTimeSpanString(up, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        Map<String, Lotto.Result> latest = Lotto.latest(this);

        // next draws
        section("Next jackpot draws");
        LinearLayout nc = card();
        List<Lotto.Game> majors = new ArrayList<>();
        for (Lotto.Game g : Lotto.GAMES) if (g.major) majors.add(g);
        majors.sort((a, b) -> Long.compare(Lotto.nextDraw(a).getTimeInMillis(), Lotto.nextDraw(b).getTimeInMillis()));
        for (Lotto.Game g : majors) {
            Lotto.Result r = latest.get(g.key);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(6), 0, px(6));
            TextView dot = tv(g.shortName, 13, Color.WHITE, true);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(round(g.color, 10, 0));
            row.addView(dot, new LinearLayout.LayoutParams(px(52), px(30)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(px(12), 0, 0, 0);
            col.addView(tv(Lotto.whenText(Lotto.nextDraw(g)), 15, INK, true));
            col.addView(tv(r == null ? "Jackpot: tap Refresh" : currentPrize(g, r), 14, SOFT, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            nc.addView(row);
        }

        // major games
        section("Latest results and current prizes");
        for (Lotto.Game g : Lotto.GAMES) if (g.major) gameCard(g, latest.get(g.key));

        // small games
        section("Daily and digit games");
        LinearLayout sc = card();
        for (Lotto.Game g : Lotto.GAMES) {
            if (g.major) continue;
            Lotto.Result r = latest.get(g.key);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(5), 0, px(5));
            TextView name = tv(g.name, 15, INK, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1.3f));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.END);
            if (r == null) col.addView(tv("—", 15, SOFT, false));
            else {
                TextView n = tv(r.numbers, 18, g.color, true);
                n.setGravity(Gravity.END);
                col.addView(n);
                TextView w = tv(Lotto.pretty(r.date) + " · " + Lotto.peso(r.jackpot) + " · "
                        + (r.winners < 0 ? "winners n/a" : String.format(java.util.Locale.US, "%,d winners", r.winners)), 12, SOFT, false);
                w.setGravity(Gravity.END);
                col.addView(w);
            }
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 2f));
            row.setOnClickListener(v -> pickDialog(g, latest.get(g.key)));
            sc.addView(row);
        }
        sc.addView(tv("Tap a game for a lucky pick.", 12, SOFT, false));

        // schedule
        section("Weekly draw schedule");
        LinearLayout sch = card();
        String[] dn = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        int[] dv = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        int today = Lotto.now().get(Calendar.DAY_OF_WEEK);
        for (int i = 0; i < 7; i++) {
            StringBuilder s = new StringBuilder();
            for (Lotto.Game g : Lotto.GAMES) {
                if (!g.drawsOn(dv[i]) || g.key.startsWith("3D") || g.key.startsWith("2D")) continue;
                if (s.length() > 0) s.append(" · ");
                s.append(g.shortName);
            }
            LinearLayout row = new LinearLayout(this);
            row.setPadding(px(6), px(6), px(6), px(6));
            if (dv[i] == today) row.setBackground(round(0x1A12307A, 8, 0));
            row.addView(tv(dn[i] + (dv[i] == today ? " (today)" : ""), 15, INK, dv[i] == today), new LinearLayout.LayoutParams(0, -2, 1.1f));
            row.addView(tv(s.toString(), 15, INK, false), new LinearLayout.LayoutParams(0, -2, 1.4f));
            sch.addView(row);
        }
        sch.addView(tv("\nJackpot and digit games are drawn at 9 PM. Swertres (3D) and EZ2 (2D) are drawn daily at 2 PM, 5 PM and 9 PM.", 13, SOFT, false));

        TextView foot = tv("Results are read from public PCSO results pages (Inquirer, GMA News). Always confirm with PCSO "
                + "(pcso.gov.ph) before claiming a prize. Jackpot amounts change with sales. For 18 and older only; play responsibly.", 12, SOFT, false);
        foot.setPadding(px(6), px(6), px(6), 0);
        list.addView(foot);
    }

    private String currentPrize(Lotto.Game g, Lotto.Result r) {
        if (r.winners == 0) return "Current jackpot: at least " + Lotto.peso(r.jackpot) + " (no winner last draw)";
        if (r.winners > 0) return "Jackpot was won on " + Lotto.pretty(r.date) + "; it restarts from the minimum";
        return "Last jackpot: " + Lotto.peso(r.jackpot);
    }

    private void gameCard(Lotto.Game g, Lotto.Result r) {
        LinearLayout c = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = tv(g.shortName, 14, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(g.color, 10, 0));
        top.addView(badge, new LinearLayout.LayoutParams(px(56), px(30)));
        TextView name = tv(g.name, 18, INK, true);
        name.setPadding(px(12), 0, 0, 0);
        top.addView(name);
        c.addView(top);

        if (r == null) {
            c.addView(tv("\nNo result loaded yet. Tap ↻ Refresh.", 14, SOFT, false));
        } else {
            c.addView(tv("Latest draw: " + Lotto.pretty(r.date), 14, SOFT, false));
            c.addView(balls(r.numbers, g.color, null));
            c.addView(tv("Jackpot: " + Lotto.pesoFull(r.jackpot), 16, INK, true));
            TextView w = tv(winnersText(g, r), 15, r.winners > 0 ? 0xFF2E7D32 : INK, r.winners > 0);
            c.addView(w);
            TextView cp = tv(currentPrize(g, r), 15, g.color, true);
            cp.setPadding(0, px(6), 0, 0);
            c.addView(cp);
        }
        c.addView(tv("Next draw: " + Lotto.whenText(Lotto.nextDraw(g)), 14, INK, false));
        c.addView(tv("Draw days: " + g.dayNames() + " · " + g.timeName(), 13, SOFT, false));

        // saved picks checked against the latest draw
        List<String> picks = Lotto.savedPicks(this, g.key);
        if (!picks.isEmpty() && r != null) {
            c.addView(tv("\nYour saved picks vs. latest draw:", 14, INK, true));
            for (String p : picks) {
                c.addView(balls(p, g.color, r.numbers));
                int m = Lotto.matches(g, p, r.numbers);
                c.addView(tv(Lotto.prizeHint(g, m), 13, m >= 3 ? 0xFF2E7D32 : SOFT, m >= 3));
            }
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, px(8), 0, 0);
        Button pick = new Button(this);
        pick.setText("🎲 Lucky pick");
        pick.setAllCaps(false);
        pick.setTextColor(Color.WHITE);
        pick.setBackground(round(g.color, 22, 0));
        pick.setOnClickListener(v -> pickDialog(g, r));
        btns.addView(pick, new LinearLayout.LayoutParams(0, px(44), 1));
        Button hist = new Button(this, null, android.R.attr.borderlessButtonStyle);
        hist.setText("History");
        hist.setAllCaps(false);
        hist.setTextColor(g.color);
        hist.setOnClickListener(v -> historyDialog(g));
        btns.addView(hist, new LinearLayout.LayoutParams(0, px(44), 1));
        c.addView(btns);
    }

    private void pickDialog(Lotto.Game g, Lotto.Result r) {
        final String[] cur = {Lotto.format(g, Lotto.luckyPick(g, rnd))};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(20), px(10), px(20), 0);
        LinearLayout holder = new LinearLayout(this);
        holder.addView(balls(cur[0], g.color, null));
        box.addView(holder);
        box.addView(tv(g.note, 13, SOFT, false));
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Lucky pick · " + g.name)
                .setView(box)
                .setPositiveButton("Save pick", (di, w) -> {
                    Lotto.savePick(this, g.key, cur[0]);
                    Toast.makeText(this, "Saved. It will be checked against each new draw.", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNeutralButton("🎲 Again", null)
                .setNegativeButton("Close", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            cur[0] = Lotto.format(g, Lotto.luckyPick(g, rnd));
            holder.removeAllViews();
            holder.addView(balls(cur[0], g.color, null));
        }));
        d.show();
    }

    private void historyDialog(Lotto.Game g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(20), px(6), px(20), px(6));
        List<Lotto.Result> h = Lotto.history(this, g.key);
        if (h.isEmpty()) box.addView(tv("No results loaded yet.", 14, SOFT, false));
        for (Lotto.Result r : h) {
            box.addView(tv(Lotto.pretty(r.date) + " · " + Lotto.pesoFull(r.jackpot) + " · "
                    + (r.winners < 0 ? "winners n/a" : r.winners + " winner(s)"), 13, SOFT, false));
            box.addView(balls(r.numbers, g.color, null));
        }
        List<String> picks = Lotto.savedPicks(this, g.key);
        if (!picks.isEmpty()) {
            box.addView(tv("\nSaved picks (tap to delete):", 14, INK, true));
            for (String p : picks) {
                TextView t = tv("🗑  " + p, 15, INK, false);
                t.setPadding(0, px(6), 0, px(6));
                t.setOnClickListener(v -> { Lotto.removePick(this, g.key, p); t.setVisibility(View.GONE); render(); });
                box.addView(t);
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this).setTitle(g.name + " · recent draws").setView(sv)
                .setPositiveButton("Close", null).show();
    }
}
