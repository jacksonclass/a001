package com.workmoney.timer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;

/** Minimal offline work-pay timer: salary setup, accurate duration, rolling money, daily/monthly totals. */
public final class MainActivity extends Activity {
    private final int background = Color.rgb(12, 16, 23);
    private final int card = Color.rgb(25, 32, 42);
    private final int accent = Color.rgb(101, 230, 177);
    private final int white = Color.rgb(235, 246, 243);
    private final int muted = Color.rgb(153, 168, 180);
    private WorkStore store;
    private RollingAmountView amountView;
    private TextView status, rate, netSalary, todayTime, leftTime, progressLabel, monthMoney, monthProgress, mainAction;
    private ProgressBar progress;
    private TextView endAction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastUiSync = 0L;
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 250);
        }
    };

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        store = new WorkStore(this);
        // Recover the last checkpoint before resuming; do not auto-restart after a reboot.
        store.sync();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 60);
        }
        drawUi();
        if (!store.isSetup()) handler.postDelayed(() -> showSettings(true), 350L);
        if (store.isRunning()) command(TimingService.ACTION_START);
    }

    private void drawUi() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(24));
        outer.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView appName = text("✦  打工赚钱计时器", 20, white, true);
        header.addView(appName, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = text("⚙  设置", 14, muted, false);
        settings.setPadding(dp(10), dp(10), 0, dp(10));
        settings.setOnClickListener(v -> showSettings(false));
        header.addView(settings);
        root.addView(header);

        TextView slogan = text("每一秒的努力，都有价值。", 14, muted, false);
        slogan.setPadding(0, dp(8), 0, dp(30));
        root.addView(slogan);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(16), dp(24), dp(16), dp(24));
        hero.setBackground(round(card, 22));
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        status = text("●  尚未开始", 13, accent, true);
        status.setGravity(Gravity.CENTER);
        hero.addView(status);
        TextView title = text("今天已经赚了", 15, muted, false);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(25), 0, dp(7));
        hero.addView(title);
        amountView = new RollingAmountView(this);
        hero.addView(amountView, new LinearLayout.LayoutParams(-1, dp(82)));
        rate = text("每秒 ¥0.0127713921", 15, accent, true);
        rate.setGravity(Gravity.CENTER);
        rate.setPadding(0, dp(6), 0, 0);
        hero.addView(rate);
        netSalary = text("预计到手月薪 ¥8000.00", 13, muted, false);
        netSalary.setGravity(Gravity.CENTER);
        netSalary.setPadding(0, dp(9), 0, 0);
        hero.addView(netSalary);

        LinearLayout timeCard = new LinearLayout(this);
        timeCard.setOrientation(LinearLayout.VERTICAL);
        timeCard.setPadding(dp(18), dp(20), dp(18), dp(18));
        timeCard.setBackground(round(card, 18));
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(-1, -2);
        timeLp.topMargin = dp(15);
        root.addView(timeCard, timeLp);

        TextView todayTitle = text("今日工作时间", 13, muted, false);
        timeCard.addView(todayTitle);
        todayTime = text("00:00:00", 28, white, true);
        todayTime.setPadding(0, dp(7), 0, dp(7));
        timeCard.addView(todayTime);
        leftTime = text("剩余 08:00:00", 13, muted, false);
        timeCard.addView(leftTime);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(55, 67, 77)));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(7));
        progressLp.topMargin = dp(19);
        timeCard.addView(progress, progressLp);
        progressLabel = text("今日完成 0%", 12, muted, false);
        progressLabel.setPadding(0, dp(7), 0, 0);
        timeCard.addView(progressLabel);

        LinearLayout monthly = new LinearLayout(this);
        monthly.setOrientation(LinearLayout.VERTICAL);
        monthly.setPadding(dp(18), dp(20), dp(18), dp(17));
        monthly.setBackground(round(card, 18));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(15);
        root.addView(monthly, mp);
        TextView mt = text("本月累计（折算收入）", 13, muted, false);
        monthly.addView(mt);
        monthMoney = text("¥0.00", 27, white, true);
        monthMoney.setPadding(0, dp(5), 0, dp(4));
        monthly.addView(monthMoney);
        monthProgress = text("本月预计完成 0%", 12, muted, false);
        monthly.addView(monthProgress);

        mainAction = text("开始赚钱  ▶", 18, background, true);
        mainAction.setGravity(Gravity.CENTER);
        mainAction.setBackground(round(accent, 16));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(59));
        ap.topMargin = dp(23);
        root.addView(mainAction, ap);
        mainAction.setOnClickListener(v -> toggleTimer());

        endAction = text("下班 · 保存今天", 14, muted, false);
        endAction.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(48));
        ep.topMargin = dp(2);
        root.addView(endAction, ep);
        endAction.setOnClickListener(v -> askEnd());

        TextView history = text("查看工作记录   →", 14, accent, false);
        history.setGravity(Gravity.CENTER);
        history.setPadding(0, dp(8), 0, dp(14));
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        root.addView(history);

        TextView foot = text("按约定工时折算的参考金额，并非实际工资到账。", 11, muted, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);
        setContentView(outer);
    }

    private TextView text(String v, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void refresh() {
        if (store == null || amountView == null) return;
        // The foreground service persistently commits the clock; on-screen refresh is lightweight.
        if (store.isRunning() && android.os.SystemClock.uptimeMillis() - lastUiSync >= 950L) {
            store.sync();
            lastUiSync = android.os.SystemClock.uptimeMillis();
        }
        long workedMs = store.todayMillis();
        long displayMs = (workedMs / 1000L) * 1000L;
        amountView.setAmount(store.amount(displayMs).toPlainString());
        todayTime.setText(SalaryMath.hoursClock(workedMs));
        leftTime.setText("剩余 " + SalaryMath.hoursClock(Math.max(0, store.limitMillis() - workedMs)));
        progress.setProgress(store.progressPercent());
        progressLabel.setText("今日完成 " + store.progressPercent() + "%");
        rate.setText("每秒 ¥" + store.perSecond().toPlainString());
        netSalary.setText("预计到手月薪 ¥" + store.net().setScale(2).toPlainString());
        monthMoney.setText("¥" + store.amount(store.monthMillis()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        monthProgress.setText("月度工时完成 " + store.monthPercent() + "%");
        boolean finished = store.todayMillis() >= store.limitMillis() || store.isClosedToday();
        if (finished) {
            status.setText("✓  今日已完成 · 辛苦啦");
            mainAction.setText("明天继续加油");
            mainAction.setAlpha(.55f);
            mainAction.setEnabled(false);
            endAction.setVisibility(View.GONE);
        } else {
            mainAction.setAlpha(1f);
            mainAction.setEnabled(true);
            endAction.setVisibility(View.VISIBLE);
            if (store.isRunning()) {
                status.setText("●  正在积累中");
                mainAction.setText("暂停赚钱  Ⅱ");
                endAction.setVisibility(View.VISIBLE);
            } else if (workedMs > 0L) {
                status.setText("●  已暂停");
                mainAction.setText("继续赚钱  ▶");
            } else {
                status.setText("●  准备开始");
                mainAction.setText("开始赚钱  ▶");
            }
        }
    }

    private void toggleTimer() {
        if (!store.isSetup()) { showSettings(true); return; }
        if (store.isRunning()) {
            store.pause();
            command(TimingService.ACTION_PAUSE);
        } else if (store.start()) {
            command(TimingService.ACTION_START);
        } else Toast.makeText(this, "今天的工作计时已结束", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void command(String action) {
        Intent intent = new Intent(this, TimingService.class);
        intent.setAction(action);
        if (TimingService.ACTION_START.equals(action)) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } else startService(intent);
    }

    private void askEnd() {
        if (!store.isSetup()) { showSettings(true); return; }
        new AlertDialog.Builder(this)
            .setTitle("今天下班了？")
            .setMessage("会保存今日已工作时间，并结束今天的计时。明天可重新开始。")
            .setNegativeButton("再干一会", null)
            .setPositiveButton("确认下班", (dialog, which) -> {
                store.endDay();
                command(TimingService.ACTION_PAUSE);
                refresh();
            }).show();
    }

    private EditText input(String current, String hint) {
        EditText t = new EditText(this);
        t.setText(current);
        t.setSingleLine(true);
        t.setHint(hint);
        t.setTextSize(19f);
        t.setTextColor(white);
        t.setHintTextColor(muted);
        t.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        t.setSelectAllOnFocus(true);
        return t;
    }

    private void addField(LinearLayout form, String label, EditText field) {
        TextView caption = text(label, 13, muted, false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        form.addView(caption, cp);
        form.addView(field, new LinearLayout.LayoutParams(-1, dp(53)));
    }

    private void showSettings(boolean first) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(22), dp(7), dp(22), dp(7));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText salary = input(store.getSalary(), "例：10000");
        EditText deduction = input(store.getDeduction(), "社保、公积金、其他扣除");
        EditText days = input(store.getDays(), "例：21.75");
        EditText hours = input(store.getHours(), "例：8");
        addField(form, "每月工资（元）", salary);
        addField(form, "每月扣除项合计（元）", deduction);
        addField(form, "每月工作天数", days);
        addField(form, "每天工作小时", hours);
        TextView info = text("按税后月薪 ÷ 月工作天数 ÷ 每天工时折算。\n修改设置会按新标准重算历史计时对应的估算金额。", 12, muted, false);
        info.setPadding(0, dp(14), 0, dp(5));
        form.addView(info);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(first ? "先设置你的工资" : "工资与工时设置")
            .setView(scroller)
            .setCancelable(!first)
            .setNegativeButton(first ? "稍后设置" : "取消", (di, which) -> {})
            .setPositiveButton("保存", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                store.saveSettings(salary.getText().toString(), deduction.getText().toString(),
                    days.getText().toString(), hours.getText().toString());
                dialog.dismiss();
                Toast.makeText(this, "已保存 · 可以开始计时啦", Toast.LENGTH_SHORT).show();
                refresh();
            } catch (Exception e) {
                new AlertDialog.Builder(this).setMessage("输入有误：" + e.getMessage()).setPositiveButton("知道了", null).show();
            }
        }));
        dialog.show();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }
}
