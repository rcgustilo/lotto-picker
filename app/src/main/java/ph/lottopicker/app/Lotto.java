package ph.lottopicker.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PCSO games, draw schedule, results downloaded from public results pages, saved picks and lucky picks.
 * Times are Philippine time (Asia/Manila).
 */
public final class Lotto {

    public static final TimeZone PH = TimeZone.getTimeZone("Asia/Manila");

    /** A PCSO game. days use Calendar.SUNDAY..SATURDAY; hour is the draw time (24 h). */
    public static final class Game {
        public final String key, name, shortName;
        final String pattern;
        public final int count, max, color;
        public final int[] days;
        public final int hour;
        public final boolean major, digits;
        public final String note;

        Game(String key, String name, String shortName, String pattern, int count, int max, boolean digits,
             int[] days, int hour, int color, boolean major, String note) {
            this.key = key; this.name = name; this.shortName = shortName; this.pattern = pattern;
            this.count = count; this.max = max; this.digits = digits; this.days = days; this.hour = hour;
            this.color = color; this.major = major; this.note = note;
        }

        public boolean drawsOn(int dow) {
            for (int d : days) if (d == dow) return true;
            return false;
        }

        public String dayNames() {
            if (days.length == 7) return "Daily";
            String[] n = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
            StringBuilder b = new StringBuilder();
            for (int d : order) if (drawsOn(d)) { if (b.length() > 0) b.append(", "); b.append(n[d]); }
            return b.toString();
        }

        public String timeName() { return hour == 14 ? "2 PM" : hour == 17 ? "5 PM" : "9 PM"; }
    }

    private static final int SU = Calendar.SUNDAY, MO = Calendar.MONDAY, TU = Calendar.TUESDAY, WE = Calendar.WEDNESDAY,
            TH = Calendar.THURSDAY, FR = Calendar.FRIDAY, SA = Calendar.SATURDAY;
    private static final int[] DAILY = {SU, MO, TU, WE, TH, FR, SA};

    public static final Game[] GAMES = {
            new Game("658", "Ultra Lotto 6/58", "6/58", "Ultra\\s*Lotto\\s*6/58", 6, 58, false, new int[]{TU, FR, SU}, 21, 0xFFD32F2F, true, "Pick 6 numbers from 1 to 58. ₱20 per play."),
            new Game("655", "Grand Lotto 6/55", "6/55", "Grand\\s*Lotto\\s*6/55", 6, 55, false, new int[]{MO, WE, SA}, 21, 0xFF6A1B9A, true, "Pick 6 numbers from 1 to 55. ₱20 per play."),
            new Game("649", "Super Lotto 6/49", "6/49", "Super\\s*Lotto\\s*6/49", 6, 49, false, new int[]{TU, TH, SU}, 21, 0xFF1565C0, true, "Pick 6 numbers from 1 to 49. ₱20 per play."),
            new Game("645", "Mega Lotto 6/45", "6/45", "Mega\\s*Lotto\\s*6/45", 6, 45, false, new int[]{MO, WE, FR}, 21, 0xFF2E7D32, true, "Pick 6 numbers from 1 to 45. ₱20 per play."),
            new Game("642", "Lotto 6/42", "6/42", "(?<!Super\\s)Lotto\\s*6/42", 6, 42, false, new int[]{TU, TH, SA}, 21, 0xFFEF6C00, true, "Pick 6 numbers from 1 to 42. ₱20 per play."),
            new Game("6D", "6D Lotto", "6D", "6D\\s*Lotto", 6, 9, true, new int[]{TU, TH, SA}, 21, 0xFF00838F, false, "6 digits in exact order."),
            new Game("4D", "4D Lotto", "4D", "4D\\s*Lotto", 4, 9, true, new int[]{MO, WE, FR}, 21, 0xFF00897B, false, "4 digits in exact order."),
            new Game("3D2", "Swertres (3D) 2 PM", "3D 2PM", "(?:3D\\s*Lotto|Swertres)\\s*2\\s*PM", 3, 9, true, DAILY, 14, 0xFF5D4037, false, "3 digits."),
            new Game("3D5", "Swertres (3D) 5 PM", "3D 5PM", "(?:3D\\s*Lotto|Swertres)\\s*5\\s*PM", 3, 9, true, DAILY, 17, 0xFF5D4037, false, "3 digits."),
            new Game("3D9", "Swertres (3D) 9 PM", "3D 9PM", "(?:3D\\s*Lotto|Swertres)\\s*9\\s*PM", 3, 9, true, DAILY, 21, 0xFF5D4037, false, "3 digits."),
            new Game("2D2", "EZ2 (2D) 2 PM", "2D 2PM", "(?:2D(?:\\s*Lotto)?|EZ2)\\s*2\\s*PM", 2, 31, false, DAILY, 14, 0xFF455A64, false, "2 numbers from 1 to 31."),
            new Game("2D5", "EZ2 (2D) 5 PM", "2D 5PM", "(?:2D(?:\\s*Lotto)?|EZ2)\\s*5\\s*PM", 2, 31, false, DAILY, 17, 0xFF455A64, false, "2 numbers from 1 to 31."),
            new Game("2D9", "EZ2 (2D) 9 PM", "2D 9PM", "(?:2D(?:\\s*Lotto)?|EZ2)\\s*9\\s*PM", 2, 31, false, DAILY, 21, 0xFF455A64, false, "2 numbers from 1 to 31."),
    };

    public static Game game(String key) {
        for (Game g : GAMES) if (g.key.equals(key)) return g;
        return GAMES[0];
    }

    /** One draw result. winners is -1 when the source did not say. */
    public static final class Result {
        public final String game, date, numbers;   // date yyyy-MM-dd, numbers "51-22-34-27-01-24"
        public final double jackpot;
        public final int winners;

        Result(String game, String date, String numbers, double jackpot, int winners) {
            this.game = game; this.date = date; this.numbers = numbers; this.jackpot = jackpot; this.winners = winners;
        }

        JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("g", game); o.put("d", date); o.put("n", numbers); o.put("j", jackpot); o.put("w", winners);
            return o;
        }

        static Result from(JSONObject o) {
            return new Result(o.optString("g"), o.optString("d"), o.optString("n"), o.optDouble("j", 0), o.optInt("w", -1));
        }
    }

    private Lotto() { }

    // ---------------- dates ----------------

    public static Calendar now() { return Calendar.getInstance(PH, Locale.US); }

    static String iso(Calendar c) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(PH);
        return f.format(c.getTime());
    }

    public static String pretty(String isoDate) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(PH);
            SimpleDateFormat out = new SimpleDateFormat("EEE, MMM d", Locale.US);
            out.setTimeZone(PH);
            return out.format(in.parse(isoDate));
        } catch (Exception e) {
            return isoDate;
        }
    }

    /** The next draw time of a game, at or after now. */
    public static Calendar nextDraw(Game g) {
        Calendar c = now();
        for (int i = 0; i < 8; i++) {
            Calendar d = (Calendar) c.clone();
            d.add(Calendar.DAY_OF_YEAR, i);
            d.set(Calendar.HOUR_OF_DAY, g.hour);
            d.set(Calendar.MINUTE, 0);
            d.set(Calendar.SECOND, 0);
            d.set(Calendar.MILLISECOND, 0);
            if (g.drawsOn(d.get(Calendar.DAY_OF_WEEK)) && d.getTimeInMillis() > c.getTimeInMillis()) return d;
        }
        return c;
    }

    public static String whenText(Calendar draw) {
        Calendar n = now();
        long mins = (draw.getTimeInMillis() - n.getTimeInMillis()) / 60000;
        String time = draw.get(Calendar.HOUR_OF_DAY) == 14 ? "2 PM" : draw.get(Calendar.HOUR_OF_DAY) == 17 ? "5 PM" : "9 PM";
        int dayDiff = draw.get(Calendar.DAY_OF_YEAR) - n.get(Calendar.DAY_OF_YEAR);
        if (dayDiff < 0) dayDiff += n.getActualMaximum(Calendar.DAY_OF_YEAR);
        String day = dayDiff == 0 ? "Today" : dayDiff == 1 ? "Tomorrow"
                : new SimpleDateFormat("EEEE", Locale.US).format(draw.getTime());
        String left = mins < 60 ? mins + " min" : (mins / 60) + " h " + (mins % 60) + " min";
        return day + " " + time + "  (in " + left + ")";
    }

    // ---------------- money ----------------

    public static String peso(double v) {
        if (v >= 1_000_000) return String.format(Locale.US, "₱%,.2fM", v / 1_000_000);
        return String.format(Locale.US, "₱%,.0f", v);
    }

    public static String pesoFull(double v) { return String.format(Locale.US, "₱%,.2f", v); }

    // ---------------- cache ----------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("lotto", Context.MODE_PRIVATE);
    }

    public static synchronized List<Result> cached(Context c) {
        List<Result> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("results", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Result.from(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public static long updatedAt(Context c) { return prefs(c).getLong("updated", 0); }

    private static synchronized void store(Context c, List<Result> list) {
        try {
            JSONArray a = new JSONArray();
            for (Result r : list) a.put(r.json());
            prefs(c).edit().putString("results", a.toString()).putLong("updated", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    /** Latest known result for each game. */
    public static Map<String, Result> latest(Context c) {
        Map<String, Result> m = new HashMap<>();
        for (Result r : cached(c)) {
            Result old = m.get(r.game);
            if (old == null || r.date.compareTo(old.date) > 0) m.put(r.game, r);
        }
        return m;
    }

    /** Recent results of one game, newest first. */
    public static List<Result> history(Context c, String game) {
        List<Result> out = new ArrayList<>();
        for (Result r : cached(c)) if (r.game.equals(game)) out.add(r);
        out.sort((a, b) -> b.date.compareTo(a.date));
        return out;
    }

    // ---------------- downloading ----------------

    private static final String[] MONTHS = {"january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december"};

    static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(12000);
        h.setReadTimeout(15000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        h.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        h.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = h.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString(StandardCharsets.UTF_8.name());
        } finally {
            h.disconnect();
        }
    }

    /** HTML to plain text, with cell and line boundaries turned into " | ". */
    static String text(String html) {
        String s = html.replaceAll("(?is)<script.*?</script>", " ").replaceAll("(?is)<style.*?</style>", " ");
        s = s.replaceAll("(?s)<[^>]+>", " | ");
        s = s.replace("&nbsp;", " ").replace("&#8369;", "₱").replace("&amp;", "&").replace("&#8211;", "-").replace("&#x20B1;", "₱");
        return s.replaceAll("[\\s]+", " ");
    }

    /** Cuts away "recent results" and schedule sections so only the page's own date is read. */
    static String mainPart(String t) {
        String lower = t.toLowerCase(Locale.US);
        int cut = t.length();
        for (String m : new String[]{"recent lotto results", "lotto schedule of games", "previous results"}) {
            int i = lower.indexOf(m);
            if (i > 200 && i < cut) cut = i;
        }
        return t.substring(0, cut);
    }

    /** Reads every game result scheduled on that day from a page's text. */
    static List<Result> parse(String t, String date, int dow, boolean needWinners) {
        List<Result> out = new ArrayList<>();
        for (Game g : GAMES) {
            if (!g.drawsOn(dow)) continue;
            String num = g.digits ? "\\d" : "\\d{1,2}";
            String re = "(?i)" + g.pattern + "\\s*:?[\\s|]*(" + num + "(?:[\\s|\\-]+" + num + "){" + (g.count - 1) + "})"
                    + "[\\s|]*(?:P|PHP|₱)\\s*([\\d,]+(?:\\.\\d{1,2})?)"
                    + (needWinners ? "[\\s|]*(\\d{1,7})(?![\\d,.\\-/A-Za-z])" : "");
            Matcher m = Pattern.compile(re).matcher(t);
            if (!m.find()) continue;
            String[] parts = m.group(1).trim().split("[\\s|\\-]+");
            StringBuilder nb = new StringBuilder();
            boolean ok = parts.length == g.count;
            for (String p : parts) {
                int v = Integer.parseInt(p);
                if (g.digits ? v > 9 : (v < 1 || v > g.max)) ok = false;
                if (nb.length() > 0) nb.append('-');
                nb.append(g.digits ? String.valueOf(v) : String.format(Locale.US, "%02d", v));
            }
            if (!ok) continue;
            double jp;
            try { jp = Double.parseDouble(m.group(2).replace(",", "")); } catch (NumberFormatException e) { continue; }
            int w = needWinners ? Integer.parseInt(m.group(3)) : -1;
            out.add(new Result(g.key, date, nb.toString(), jp, w));
        }
        return out;
    }

    /** Downloads the last `days` days of results. Returns a short status message. */
    public static String refresh(Context c, int days) {
        Map<String, Result> merged = new HashMap<>();
        for (Result r : cached(c)) merged.put(r.game + "@" + r.date, r);
        int okDays = 0, failed = 0;
        Calendar day = now();
        for (int i = 0; i < days; i++) {
            Calendar d = (Calendar) day.clone();
            d.add(Calendar.DAY_OF_YEAR, -i);
            String date = iso(d);
            int dow = d.get(Calendar.DAY_OF_WEEK);
            String mon = MONTHS[d.get(Calendar.MONTH)];
            int dd = d.get(Calendar.DAY_OF_MONTH), yy = d.get(Calendar.YEAR);
            List<Result> found = new ArrayList<>();
            String[] inquirer = {
                    "https://www.inquirer.net/lotto-results/" + mon + "-" + dd + "-" + yy + "/",
                    "https://www.inquirer.net/lotto-results/" + Character.toUpperCase(mon.charAt(0)) + mon.substring(1) + "-" + dd + "-" + yy};
            for (String u : inquirer) {
                try {
                    found = parse(mainPart(text(get(u))), date, dow, true);
                    if (!found.isEmpty()) break;
                } catch (Exception ignored) { }
            }
            if (found.isEmpty()) {
                try {
                    String u = String.format(Locale.US, "https://www.gmanetwork.com/news/lotto/results-%s-%02d-%d/", mon, dd, yy);
                    found = parse(mainPart(text(get(u))), date, dow, false);
                } catch (Exception ignored) { }
            }
            if (found.isEmpty()) { if (i > 0) failed++; continue; }
            okDays++;
            for (Result r : found) {
                Result old = merged.get(r.game + "@" + r.date);
                // keep a known winner count if the new source lacks one
                if (old != null && r.winners < 0 && old.winners >= 0) continue;
                merged.put(r.game + "@" + r.date, r);
            }
        }
        // drop anything older than 21 days, and copies of an earlier draw (a page repeating old numbers)
        String cutoff;
        { Calendar k = now(); k.add(Calendar.DAY_OF_YEAR, -21); cutoff = iso(k); }
        List<Result> list = new ArrayList<>();
        for (Result r : merged.values()) if (r.date.compareTo(cutoff) >= 0) list.add(r);
        list.sort((a, b) -> a.date.compareTo(b.date));
        List<Result> clean = new ArrayList<>();
        for (Result r : list) {
            boolean dup = false;
            if (!game(r.game).digits && r.game.startsWith("6")) {
                for (Result o : clean) if (o.game.equals(r.game) && o.numbers.equals(r.numbers)) { dup = true; break; }
            }
            if (!dup) clean.add(r);
        }
        if (okDays > 0) store(c, clean);
        if (okDays == 0) return "Could not reach the results sites. Check your internet connection.";
        return failed > 0 ? "Updated (" + failed + " day(s) unavailable)" : "Updated";
    }

    // ---------------- picks ----------------

    public static int[] luckyPick(Game g, Random r) {
        int[] out = new int[g.count];
        if (g.digits) {
            for (int i = 0; i < g.count; i++) out[i] = r.nextInt(10);
            return out;
        }
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= g.max; i++) pool.add(i);
        java.util.Collections.shuffle(pool, r);
        for (int i = 0; i < g.count; i++) out[i] = pool.get(i);
        if (g.count > 2) Arrays.sort(out);
        return out;
    }

    public static String format(Game g, int[] nums) {
        StringBuilder b = new StringBuilder();
        for (int n : nums) {
            if (b.length() > 0) b.append('-');
            b.append(g.digits ? String.valueOf(n) : String.format(Locale.US, "%02d", n));
        }
        return b.toString();
    }

    public static List<String> savedPicks(Context c, String game) {
        List<String> out = new ArrayList<>();
        for (String s : prefs(c).getString("picks_" + game, "").split(";")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public static void savePick(Context c, String game, String pick) {
        List<String> l = savedPicks(c, game);
        if (l.contains(pick)) return;
        l.add(0, pick);
        while (l.size() > 10) l.remove(l.size() - 1);
        prefs(c).edit().putString("picks_" + game, String.join(";", l)).apply();
    }

    public static void removePick(Context c, String game, String pick) {
        List<String> l = savedPicks(c, game);
        l.remove(pick);
        prefs(c).edit().putString("picks_" + game, String.join(";", l)).apply();
    }

    /** How many numbers of a pick match a result (any order for 6/xx and 2D, exact position for digit games). */
    public static int matches(Game g, String pick, String result) {
        String[] p = pick.split("-"), r = result.split("-");
        int n = 0;
        if (g.digits) {
            for (int i = 0; i < Math.min(p.length, r.length); i++) if (p[i].equals(r[i])) n++;
            return n;
        }
        List<String> rr = new ArrayList<>(Arrays.asList(r));
        for (String s : p) if (rr.remove(s)) n++;
        return n;
    }

    public static String prizeHint(Game g, int matched) {
        if (g.digits || g.count == 2) return matched == g.count ? "All numbers match: you won!" : matched + " in the right place";
        switch (matched) {
            case 6: return "JACKPOT! Claim at PCSO main office";
            case 5: return "5 of 6: second prize";
            case 4: return "4 of 6: third prize";
            case 3: return "3 of 6: fourth prize (small prize)";
            default: return matched + " of 6";
        }
    }

    /** The game with the soonest next draw among the major games. */
    public static Game nextMajor() {
        Game best = GAMES[0];
        long bt = Long.MAX_VALUE;
        for (Game g : GAMES) {
            if (!g.major) continue;
            long t = nextDraw(g).getTimeInMillis();
            if (t < bt || (t == bt && g.max > best.max)) { bt = t; best = g; }
        }
        return best;
    }
}
