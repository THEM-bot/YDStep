package com.ydstep;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Generates the root command that publishes the multiplier.
 *
 * The hook runs inside 悦动圈's process and cannot receive values from this app,
 * because the exploit-based root provides no general su, so writes to
 * /data/local/tmp from here are silently denied. This screen therefore produces
 * the exact command to run in whatever root shell is available.
 */
public final class SettingsActivity extends Activity {
    private EditText factorInput;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(title("悦动圈 步数倍率"));

        root.addView(hint("走路 1 步记为 N 步。改完需要执行 root 命令并重启悦动圈。"));
        factorInput = new EditText(this);
        factorInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        factorInput.setText(String.valueOf(BuildConfig.FACTOR));
        root.addView(factorInput);

        root.addView(button("生成并复制 root 命令", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cmd = buildCommand();
                copy(cmd);
                status.setText(cmd);
                Toast.makeText(SettingsActivity.this,
                        "已复制，请到 root shell 执行", Toast.LENGTH_LONG).show();
            }
        }));

        root.addView(section("使用说明"));
        root.addView(hint(
                "1. 点按钮复制命令\n"
                        + "2. 打开 root shell（Dirty Frag）\n"
                        + "3. 粘贴执行\n"
                        + "4. 强制停止悦动圈后重新打开"));

        status = new TextView(this);
        status.setPadding(0, dp(20), 0, 0);
        status.setTextSize(12);
        status.setTextIsSelectable(true);
        root.addView(status);

        setContentView(scroll);
    }

    private String buildCommand() {
        int factor;
        try {
            factor = Integer.parseInt(factorInput.getText().toString().trim());
        } catch (Throwable t) {
            factor = BuildConfig.FACTOR;
        }
        if (factor < 1) {
            factor = 1;
        }
        if (factor > 100) {
            factor = 100;
        }
        // BusyBox-free: printf is available in toybox.
        return "printf 'factor=" + factor
                + "\\n' > /data/local/tmp/ydstep.conf"
                + " && chmod 644 /data/local/tmp/ydstep.conf"
                + " && echo done";
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ydstep", text));
        } catch (Throwable ignored) {
        }
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }

    private TextView title(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setPadding(0, dp(24), 0, dp(4));
        return tv;
    }

    private TextView hint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(0, 0, 0, dp(6));
        return tv;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }
}
