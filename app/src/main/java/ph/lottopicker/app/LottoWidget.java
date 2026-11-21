package ph.lottopicker.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Map;
import java.util.Random;

/** Home-screen widget: the next jackpot draw, its current prize, the latest numbers and a lucky pick. */
public class LottoWidget extends AppWidgetProvider {

    static final String ACTION_PICK = "ph.lottopicker.app.PICK";
    static final String ACTION_REFRESH = "ph.lottopicker.app.REFRESH";
    private static String lastPick = "";

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LottoWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(c, false));
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, views(c, false));
        if (System.currentTimeMillis() - Lotto.updatedAt(c) > 2 * 3600 * 1000L) refreshAsync(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ACTION_PICK.equals(i.getAction())) {
            Lotto.Game g = Lotto.nextMajor();
            lastPick = Lotto.format(g, Lotto.luckyPick(g, new Random()));
            updateAll(c);
        } else if (ACTION_REFRESH.equals(i.getAction())) {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (int id : m.getAppWidgetIds(new ComponentName(c, LottoWidget.class))) m.updateAppWidget(id, views(c, true));
            refreshAsync(c);
        }
    }

    private void refreshAsync(Context c) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { Lotto.refresh(app, 3); } finally {
                updateAll(app);
                pr.finish();
            }
        }).start();
    }

    private static PendingIntent broadcast(Context c, String action, int code) {
        Intent i = new Intent(c, LottoWidget.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews views(Context c, boolean loading) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_lotto);
        Lotto.Game g = Lotto.nextMajor();
        Map<String, Lotto.Result> latest = Lotto.latest(c);
        Lotto.Result r = latest.get(g.key);
        v.setTextViewText(R.id.w_game, g.name);
        v.setInt(R.id.w_game, "setBackgroundColor", g.color);
        v.setTextViewText(R.id.w_when, Lotto.whenText(Lotto.nextDraw(g)));
        if (r == null) {
            v.setTextViewText(R.id.w_jackpot, loading ? "Loading…" : "Tap ↻ to load results");
            v.setTextViewText(R.id.w_last, "");
        } else {
            v.setTextViewText(R.id.w_jackpot, r.winners == 0 ? "Jackpot ≥ " + Lotto.peso(r.jackpot)
                    : r.winners > 0 ? "Won on " + Lotto.pretty(r.date) + " (" + r.winners + " winner)" : "Jackpot " + Lotto.peso(r.jackpot));
            v.setTextViewText(R.id.w_last, "Last (" + Lotto.pretty(r.date) + "): " + r.numbers
                    + (r.winners >= 0 ? "  · " + r.winners + " winner(s)" : ""));
        }
        boolean showPick = lastPick.length() > 0 && lastPick.split("-").length == g.count;
        v.setTextViewText(R.id.w_pick, showPick ? "🎲 " + lastPick : "🎲 Tap for a lucky pick");
        v.setOnClickPendingIntent(R.id.w_pick, broadcast(c, ACTION_PICK, 1));
        v.setOnClickPendingIntent(R.id.w_refresh, broadcast(c, ACTION_REFRESH, 2));
        v.setViewVisibility(R.id.w_refresh, View.VISIBLE);
        Intent open = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.w_body, PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE));
        return v;
    }
}
