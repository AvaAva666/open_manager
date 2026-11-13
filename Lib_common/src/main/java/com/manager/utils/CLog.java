package com.manager.utils;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CLog {
    private static String Tag = "CLogJ";

    /** 文件日志输出接口：由宿主按需注入，库本身不依赖任何宿主 AAR */
    public interface DLogHandler {
        void dlog(String message);
    }

    /** 未注入时 dfile 不输出 */
    public static DLogHandler dlogHandler = null;

    // 库内不依赖宿主 BuildConfig；由宿主 Application 启动时按需置 true（如 MainApplication.onCreate）
    public static boolean isDebug = false;

    public static void d(String tag, String message) {
        if (isDebug) {
            Log.d(tag, message);
        }
    }

    public static void d(String message) {
        if (isDebug) {
            Log.d(Tag, message);
        }
    }

    public static void e(String tag, String message) {
        if (isDebug) {
            Log.e(tag, message);
        }
    }

    public static void i(String tag, String message) {
        if (isDebug) {
            Log.i(tag, message);
        }
    }

    public static void w(String tag, String message) {
        if (isDebug) {
            Log.w(tag, message);
        }
    }

    public static void e(String tag, String message, Throwable tr) {
        if (isDebug) {
            Log.e(tag, message, tr);
        }
    }

    public static void w(String tag, String message, Throwable tr) {
        if (isDebug) {
            Log.w(tag, message, tr);
        }
    }

    public static boolean isDebug() {
        return isDebug;
    }

    public static void e(String tag, Exception e) {
        if (isDebug) {
            CLog.e(e);
        }
    }

    public static void e(Exception e) {
        if (!isDebug) {
            return;
        }
        try {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            CLog.dfile(trace.toString());
            Log.d(Tag, trace.toString());
        } catch (Exception e1) {
            CLog.e(e1);
        }
    }

    public static void e(Throwable e) {
        if (!isDebug) {
            return;
        }
        try {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            CLog.dfile(trace.toString());
            Log.d(Tag, trace.toString());
        } catch (Exception e1) {
            CLog.e(e1);
        }
    }

    public static void dfile(String message) {
        if (!isDebug) {
            return;
        }
        DLogHandler handler = dlogHandler;
        if (handler == null) {
            return;
        }
        try {
            handler.dlog(message);
        } catch (Exception e) {

        }
    }

    public static void enter(String message) {
        if (isDebug) {
            Log.e(Tag + "_enter", message);
        }
    }

    public static void warning(String message) {
        if (isDebug) {
            Log.e(Tag + "_warning", message);
        }
    }

    public static void trace(String message) {
        if (isDebug) {
            Log.e(Tag + "_trace", message);
        }
    }

    public static void exit(String message) {
        if (isDebug) {
            Log.e(Tag + "_exit", message);
        }
    }

    public static void error(String message) {
        if (isDebug) {
            Log.e(Tag + "_error", message);
        }
    }

//    public static void df(String message) {
////        try {
////            String fileName = "/data/data/com.automanager/error.log";
////            PrintWriter logWriter = null;
////            File logFile = new File(fileName);
////            if (logFile.length() > 5 * 1024 * 1024)
////                logFile.renameTo(new File(fileName + System.currentTimeMillis()));
////            logWriter = new PrintWriter(new FileWriter(logFile, true));
////            logFile.setReadable(true, false);
////            logFile.setWritable(true, false);
////
////            logWriter.println(message);
////            logWriter.flush();
////        } catch (Exception e) {
////
////        }
//    }
}
