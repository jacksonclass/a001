package com.workmoney.timer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class HistoryActivity extends Activity {
    private final int bg = Color.rgb(12, 16, 23);
    private final int muted = Color.rgb(150, 162, 173);
    private final int white = Color.rgb(235, 246, 243);
    private final int accent = Color.rgb(101, 230, 177);
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        WorkStore store = new WorkStore(this);
        store.sync();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(25));
        scroll.addView(root);
        TextView back = label("‹  返回", 17, accent, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        TextView title = label("工作记录", 29, white, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(26);
        root.addView(title, tp);
        TextView subtitle = label("近30天 · 根据有效工作时间折算的收入", 13, muted, false);
        subtitle.setPadding(0, dp(8), 0, dp(25));
        root.addView(subtitle);
        SimpleDateFormat sdf = new SimpleDateFormat("MM月dd日 · EEEE", Locale.CHINA);
        Calendar cal = Calendar.getInstance();
        long grandMillis = 0;
        for (int i = 0; i < 30; i++) {
            long ms = store.recordedDay(WorkStore.date(cal.getTimeInMillis()));
            if (ms > 0) {
                grandMillis += ms;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(16), dp(14), dp(16), dp(14));
                GradientDrawable box = new GradientDrawable();
                box.setColor(Color.rgb(25, 32, 42));
                box.setCornerRadius(dp(14));
                card.setBackground(box);
                TextView day = label(sdf.format(cal.getTime()), 15, white, true);
                card.addView(day);
                TextView hours = label("工作 " + SalaryMath.hoursClock(ms)
                    + "    ·    估算 ¥" + store.amount(ms).toPlainString(), 14, accent, false);
                hours.setPadding(0, dp(9), 0, 0);
                card.addView(hours);
                LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
                margin.bottomMargin = dp(10);
                root.addView(card, margin);
            }
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        if (grandMillis == 0) {
            TextView empty = label("还没有工作记录\n今天就开始积累吧 ✦", 16, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(70), 0, dp(70));
            root.addView(empty);
        }
        setContentView(scroll);
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
}
