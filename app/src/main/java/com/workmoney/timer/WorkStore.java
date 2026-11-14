package com.workmoney.timer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** Keeps a monotonic stopwatch checkpoint, per-day duration and explicit day-close state. */
public final class WorkStore {
    private static final String PREFS = "work_money_v1";
    private static final Object LOCK = new Object();
    private static final String DAY_PREFIX = "duration_";
    private static final String CLOSED_PREFIX = "closed_";

    public final SharedPreferences prefs;
    private final Context context;

    public WorkStore(Context ctx) {
        context = ctx.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String date(long when) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(when));
    }

    public static String month(long when) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date(when));
    }

    public String getSalary() { return prefs.getString("salary", "10000"); }
    public String getDeduction() { return prefs.getString("deduction", "2000"); }
    public String getDays() { return prefs.getString("days", "21.75"); }
    public String getHours() { return prefs.getString("hours", "8"); }
    public boolean isSetup() { return prefs.getBoolean("setup", false); }

    public void saveSettings(String salary, String deduction, String days, String hours) {
        BigDecimal s = SalaryMath.parseMoney(salary);
        BigDecimal d = SalaryMath.parseMoney(deduction);
        SalaryMath.net(s, d);
        BigDecimal wd = SalaryMath.parseDays(days);
        BigDecimal wh = SalaryMath.parseHours(hours);
        synchronized (LOCK) {
            sync();
            prefs.edit().putString("salary", s.toPlainString())
                .putString("deduction", d.toPlainString())
                .putString("days", wd.stripTrailingZeros().toPlainString())
                .putString("hours", wh.stripTrailingZeros().toPlainString())
                .putBoolean("setup", true).commit();
        }
    }

    public BigDecimal net() {
        return SalaryMath.net(SalaryMath.parseMoney(getSalary()), SalaryMath.parseMoney(getDeduction()));
    }

    public BigDecimal perSecond() {
        return SalaryMath.secondRate(net(), SalaryMath.parseDays(getDays()), SalaryMath.parseHours(getHours()));
    }

    public long limitMillis() { return SalaryMath.dailyLimitMillis(SalaryMath.parseHours(getHours())); }

    public BigDecimal amount(long millis) {
        return SalaryMath.earned(millis, net(), SalaryMath.parseDays(getDays()), SalaryMath.parseHours(getHours()));
    }

    private int bootCount() {
        try { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT); }
        catch (Exception e) { return -1; }
    }

    public boolean isRunning() { return prefs.getBoolean("running", false); }
    public boolean isClosedToday() { return prefs.getBoolean(CLOSED_PREFIX + date(System.currentTimeMillis()), false); }

    public boolean start() {
        synchronized (LOCK) {
            sync();
            long now = System.currentTimeMillis();
            if (isRunning()) return true;
            if (!isSetup() || isClosedToday() || recordedDay(date(now)) >= limitMillis()) return false;
            return prefs.edit().putBoolean("running", true)
                .putLong("checkpointWall", now)
                .putLong("checkpointElapsed", SystemClock.elapsedRealtime())
                .putInt("checkpointBoot", bootCount()).commit();
        }
    }

    public void pause() {
        synchronized (LOCK) {
            sync();
            prefs.edit().putBoolean("running", false).commit();
        }
    }

    public void endDay() {
        synchronized (LOCK) {
            sync();
            prefs.edit().putBoolean("running", false)
                .putBoolean(CLOSED_PREFIX + date(System.currentTimeMillis()), true).commit();
        }
    }

    /** Persist elapsed time and split the interval at local midnight. Stop as soon as the day's cap is reached. */
    public boolean sync() {
        synchronized (LOCK) {
            if (!isRunning()) return false;
            long lastElapsed = prefs.getLong("checkpointElapsed", -1L);
            long nowElapsed = SystemClock.elapsedRealtime();
            long lastWall = prefs.getLong("checkpointWall", System.currentTimeMillis());
            int previousBoot = prefs.getInt("checkpointBoot", -1);
            int currentBoot = bootCount();
            if (lastElapsed < 0 || nowElapsed < lastElapsed
                || (previousBoot >= 0 && currentBoot >= 0 && previousBoot != currentBoot)) {
                prefs.edit().putBoolean("running", false).commit();
                return false;
            }
            long difference = nowElapsed - lastElapsed;
            if (difference == 0) return true;
            // elapsedRealtime counts screen-off time, independent of UI refresh rate.
            // Wall clock is used only to locate midnight boundaries.
            long wallNow = System.currentTimeMillis();
            long wallBegin = wallNow - difference;
            long pos = wallBegin;
            long end = wallNow;
            long cap = limitMillis();
            SharedPreferences.Editor edit = prefs.edit();
            boolean running = true;
            while (pos < end) {
                String day = date(pos);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(pos);
                cal.add(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long nextMidnight = cal.getTimeInMillis();
                if (nextMidnight <= pos) nextMidnight = pos + 1L;
                long segmentMs = Math.min(end, nextMidnight) - pos;
                long existing = recordedDay(day);
                long available = Math.max(0L, cap - existing);
                long credited = Math.min(available, segmentMs);
                if (credited > 0) edit.putLong(DAY_PREFIX + day, existing + credited);
                if (credited < segmentMs || existing + credited >= cap) {
                    running = false;
                    break;
                }
                pos += segmentMs;
            }
            edit.putBoolean("running", running)
                .putLong("checkpointElapsed", nowElapsed)
                .putLong("checkpointWall", wallNow)
                .putInt("checkpointBoot", currentBoot).commit();
            return running;
        }
    }

    public long recordedDay(String day) { return prefs.getLong(DAY_PREFIX + day, 0L); }
    public long todayMillis() { return recordedDay(date(System.currentTimeMillis())); }

    public long monthMillis() {
        String prefix = DAY_PREFIX + month(System.currentTimeMillis());
        long total = 0L;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof Long) {
                total += (Long) entry.getValue();
            }
        }
        return total;
    }

    public int progressPercent() {
        long max = limitMillis();
        if (max <= 0) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(todayMillis() * 100.0 / max)));
    }

    public int monthPercent() {
        BigDecimal expected = SalaryMath.parseDays(getDays())
            .multiply(BigDecimal.valueOf(limitMillis()));
        if (expected.signum() == 0) return 0;
        return BigDecimal.valueOf(monthMillis() * 100L).divide(expected, 0, RoundingMode.HALF_UP).intValue();
    }
}
