package com.ynk.buildtypeprobe;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 只读探针：跑在 app_process(ART) 里，从 Java 层读取构建类型相关属性。
 * 全程只读，不写任何属性、不改任何文件。
 *
 * 用法：
 *   CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin com.ynk.buildtypeprobe.ProbeMain
 */
public final class ProbeMain {

    public static void main(String[] args) {
        String want = sysProp("sys.buildtype");
        String ro = sysProp("ro.build.type");
        String type = reflect("android.os.Build", "TYPE");
        String tags = reflect("android.os.Build", "TAGS");

        boolean configured = want != null && want.length() > 0;
        boolean applied = configured && want.equals(type);

        String verdict;
        if (!configured) {
            verdict = "SKIP  未设置 sys.buildtype";
        } else if (applied) {
            verdict = "PASS  Build.TYPE 已是 " + want;
        } else {
            verdict = "FAIL  Build.TYPE=" + type + " 期望 " + want;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("VERDICT = ").append(verdict).append('\n');
        sb.append("SystemProperties.get(sys.buildtype) = ").append(want).append('\n');
        sb.append("SystemProperties.get(ro.build.type) = ").append(ro).append('\n');
        sb.append("Build.TYPE      (reflect)          = ").append(type).append('\n');
        sb.append("Build.TAGS      (reflect)          = ").append(tags).append('\n');
        sb.append("System.getProperty(sys.buildtype)  = ").append(System.getProperty("sys.buildtype")).append('\n');
        sb.append("pid                                = ").append(callStatic("android.os.Process", "myPid")).append('\n');
        sb.append("uid                                = ").append(callStatic("android.os.Process", "myUid")).append('\n');
        sb.append("selinux                            = ").append(readText("/proc/self/attr/current")).append('\n');
        sb.append("cmdline                            = ").append(readText("/proc/self/cmdline")).append('\n');
        sb.append("java.class.path                    = ").append(System.getProperty("java.class.path")).append('\n');
        System.out.print(sb);
    }

    /** android.os.SystemProperties.get(key, def) 反射读，get(key) 兜底。 */
    private static String sysProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            try {
                Method m = c.getMethod("get", String.class, String.class);
                Object v = m.invoke(null, key, null);
                if (v != null) {
                    return (String) v;
                }
            } catch (NoSuchMethodException ignored) {
            }
            Method m = c.getMethod("get", String.class);
            Object v = m.invoke(null, key);
            return v == null ? null : (String) v;
        } catch (Throwable t) {
            return "(err:" + t.getClass().getSimpleName() + ")";
        }
    }

    private static String reflect(String cls, String field) {
        try {
            Field f = Class.forName(cls).getField(field);
            Object v = f.get(null);
            return v == null ? "(null)" : String.valueOf(v);
        } catch (Throwable t) {
            return "(err:" + t.getClass().getSimpleName() + ")";
        }
    }

    private static String callStatic(String cls, String method) {
        try {
            Method m = Class.forName(cls).getMethod(method);
            return String.valueOf(m.invoke(null));
        } catch (Throwable t) {
            return "(err:" + t.getClass().getSimpleName() + ")";
        }
    }

    /** 读小文本文件；\0 截断，去掉不可见尾字符。 */
    private static String readText(String path) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(path);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            byte[] raw = bos.toByteArray();
            int end = 0;
            while (end < raw.length && raw[end] != 0) {
                end++;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < end; i++) {
                char ch = (char) (raw[i] & 0xFF);
                if (ch >= 0x20 && ch != 0x7F) {
                    sb.append(ch);
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "(err:" + t.getClass().getSimpleName() + ")";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private ProbeMain() {
    }
}
