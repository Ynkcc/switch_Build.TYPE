package com.ynk.buildtypeprobe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MainActivity extends Activity {
    private static final String TAG = "BuildTypeProbe";

    private TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        text = new TextView(this);
        text.setTextSize(13.0f);
        text.setPadding(24, 24, 24, 24);

        Button again = new Button(this);
        again.setText("重新读取");
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                render();
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(again);
        root.addView(scroll);
        setContentView(root);
        render();
    }

    private static String sysProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("get", String.class);
            Object v = m.invoke(null, key);
            return (String) v;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String reflectType() {
        try {
            Field f = Build.class.getField("TYPE");
            return (String) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String procName() {
        FileInputStream in = null;
        try {
            in = new FileInputStream("/proc/self/cmdline");
            byte[] buf = new byte[256];
            int n = in.read(buf);
            int end = 0;
            while (end < n && buf[end] != 0) {
                end++;
            }
            return new String(buf, 0, end);
        } catch (Throwable t) {
            return "(unknown)";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void render() {
        String direct = Build.TYPE == null ? "(null)" : Build.TYPE;
        String reflect = reflectType();
        String want = sysProp("sys.buildtype");
        String ro = sysProp("ro.build.type");
        String tags = Build.TAGS == null ? "(null)" : Build.TAGS;

        boolean configured = want != null && want.length() > 0;
        boolean applied = configured && direct.equals(want);

        String verdict;
        int color;
        if (!configured) {
            verdict = "SKIP  未设置 sys.buildtype，先执行: su -c 'switch eng'";
            color = Color.rgb(0x99, 0x66, 0x00);
        } else if (applied) {
            verdict = "PASS  Build.TYPE 已改写为 " + want;
            if (want.equals(ro)) {
                verdict += "（弱证据: 与 ro.build.type 相同, 请换一个不同的值再测）";
            }
            color = Color.rgb(0x00, 0x88, 0x00);
        } else {
            verdict = "FAIL  Build.TYPE=" + direct + "，期望 " + want;
            color = Color.rgb(0xCC, 0x00, 0x00);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(verdict).append("\n\n");
        sb.append("Build.TYPE (直接读) = ").append(direct).append('\n');
        sb.append("Build.TYPE (反射读) = ").append(reflect == null ? "(unavailable)" : reflect).append('\n');
        sb.append("sys.buildtype       = ").append(want == null ? "(unavailable)" : want).append('\n');
        sb.append("ro.build.type       = ").append(ro == null ? "(unavailable)" : ro).append('\n');
        sb.append("Build.TAGS          = ").append(tags).append('\n');
        sb.append("Build.FINGERPRINT   = ").append(Build.FINGERPRINT).append('\n');
        sb.append("Build.ID            = ").append(Build.ID).append('\n');
        sb.append("进程名              = ").append(procName()).append('\n');
        sb.append("pid                 = ").append(Process.myPid()).append('\n');
        sb.append('\n');
        sb.append("直接读与反射读都等于 sys.buildtype 才说明补丁落在本进程；\n");
        sb.append("两者不一致说明读取路径被内联。改完属性须杀掉进程重开。\n");

        text.setText(sb.toString());
        text.setTextColor(color);
        Log.i(TAG, "direct=" + direct + " reflect=" + reflect + " sys=" + want + " ro=" + ro
                + " pid=" + Process.myPid() + " proc=" + procName() + " verdict=" + verdict);
    }
}
