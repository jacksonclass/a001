package com.workmoney.timer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure Java calculation logic. Money never uses floating-point arithmetic. */
public final class SalaryMath {
    private static final BigDecimal MILLIS_PER_HOUR = new BigDecimal("3600000");
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");

    private SalaryMath() {}

    public static BigDecimal parseMoney(String input) {
        BigDecimal v = new BigDecimal(input.trim()).setScale(2, RoundingMode.UNNECESSARY);
        if (v.signum() < 0) throw new IllegalArgumentException("金额不能小于零");
        return v;
    }

    public static BigDecimal parseDays(String input) {
        BigDecimal v = new BigDecimal(input.trim());
        if (v.signum() <= 0 || v.compareTo(new BigDecimal("31")) > 0)
            throw new IllegalArgumentException("每月天数应在0到31之间");
        return v;
    }

    public static BigDecimal parseHours(String input) {
        BigDecimal v = new BigDecimal(input.trim());
        if (v.signum() <= 0 || v.compareTo(new BigDecimal("24")) > 0)
            throw new IllegalArgumentException("每天工时应在0到24小时之间");
        return v;
    }

    public static BigDecimal net(BigDecimal gross, BigDecimal deductions) {
        if (gross.compareTo(deductions) < 0)
            throw new IllegalArgumentException("扣除金额不能超过月工资");
        return gross.subtract(deductions);
    }

    public static long dailyLimitMillis(BigDecimal hours) {
        return hours.multiply(MILLIS_PER_HOUR).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public static BigDecimal secondRate(BigDecimal net, BigDecimal days, BigDecimal hours) {
        return net.divide(days.multiply(hours).multiply(SECONDS_PER_HOUR), 10, RoundingMode.HALF_UP);
    }

    public static BigDecimal earned(long workingMillis, BigDecimal net, BigDecimal days, BigDecimal hours) {
        if (workingMillis < 0) throw new IllegalArgumentException("工作时间不能小于0");
        return net.multiply(BigDecimal.valueOf(workingMillis))
            .divide(days.multiply(hours).multiply(MILLIS_PER_HOUR), 4, RoundingMode.HALF_UP);
    }

    public static String hoursClock(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", seconds / 3600L,
            (seconds / 60L) % 60L, seconds % 60L);
    }
}
