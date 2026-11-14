package com.workmoney.timer;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class SalaryMathTest {
    @Test public void exampleNetAndSecondRate() {
        BigDecimal net = SalaryMath.net(new BigDecimal("10000"), new BigDecimal("2000"));
        assertEquals("8000", net.toPlainString());
        assertEquals("0.0127713921", SalaryMath.secondRate(net, new BigDecimal("21.75"), new BigDecimal("8")).toPlainString());
    }
    @Test public void exampleEightHours() {
        assertEquals("367.8161", SalaryMath.earned(8L * 3600000,
            new BigDecimal("8000"), new BigDecimal("21.75"), new BigDecimal("8")).toPlainString());
    }
    @Test public void exampleOneHour() {
        assertEquals("45.9770", SalaryMath.earned(3600000,
            new BigDecimal("8000"), new BigDecimal("21.75"), new BigDecimal("8")).toPlainString());
    }
    @Test public void dailyCap() {
        assertEquals(28800000L, SalaryMath.dailyLimitMillis(new BigDecimal("8")));
    }
    @Test(expected = IllegalArgumentException.class) public void deductionCannotExceedSalary() {
        SalaryMath.net(new BigDecimal("1000"), new BigDecimal("2000"));
    }
}
