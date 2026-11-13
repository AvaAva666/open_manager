#!/usr/bin/env python3
"""Run the real BaseDashboardService against a deterministic host window/queue fixture."""
import pathlib
import subprocess
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = {
    "android/content/Context.java": "package android.content; public class Context {}",
    "android/content/Intent.java": "package android.content; public class Intent {}",
    "android/os/IBinder.java": "package android.os; public interface IBinder {}",
    "android/os/Looper.java": "package android.os; public class Looper { public static Looper getMainLooper() { return new Looper(); } }",
    "android/os/Handler.java": """
package android.os;
import java.util.ArrayList;
public class Handler {
    private static final ArrayList<Runnable> pending = new ArrayList<Runnable>();
    public Handler(Looper looper) {}
    public boolean post(Runnable action) { synchronized (pending) { pending.add(action); } return true; }
    public void removeCallbacksAndMessages(Object token) { synchronized (pending) { pending.clear(); } }
    public static int pendingCount() { synchronized (pending) { return pending.size(); } }
    public static void drain() {
        while (true) {
            Runnable action;
            synchronized (pending) { if (pending.isEmpty()) return; action = pending.remove(0); }
            action.run();
        }
    }
}
""",
    "android/app/Service.java": """
package android.app;
public class Service extends android.content.Context {
    public boolean superDestroyed;
    public android.content.Context getBaseContext() { return this; }
    public android.os.IBinder onBind(android.content.Intent intent) { return null; }
    public void onDestroy() { superDestroyed = true; }
}
""",
    "android/util/Log.java": "package android.util; public class Log { public static int e(String tag, String message, Throwable e) { return 0; } }",
    "android/view/View.java": "package android.view; public class View {}",
    "android/view/WindowManager.java": "package android.view; public class WindowManager { public final int id; public WindowManager(int id) { this.id = id; } }",
    "com/automanager/dashboard/DashboardInfo.java": "package com.automanager.dashboard; public class DashboardInfo {}",
    "com/automanager/dashboard/IDashboardService.java": """
package com.automanager.dashboard;
public interface IDashboardService {
    void dbShow(String model); void dbHide(); void dbOpenConfig(); DashboardInfo dbInfo();
    abstract class Stub implements IDashboardService, android.os.IBinder {}
}
""",
    "com/automanager/dashboard/utils/Util.java": """
package com.automanager.dashboard.utils;
import android.view.View;
import android.view.WindowManager;
import java.util.IdentityHashMap;
public class Util {
    public static int created, removed;
    public static boolean failCreate, failBackground;
    public static final IdentityHashMap<View, WindowManager> backgrounds = new IdentityHashMap<View, WindowManager>();
    public static void reset() { created = removed = 0; failCreate = failBackground = false; backgrounds.clear(); }
    public static WindowManager createWindowManager(android.content.Context context, boolean mock) {
        if (failCreate) throw new RuntimeException("display unavailable");
        return new WindowManager(++created);
    }
    public static View showBlackBackground(android.content.Context context, WindowManager wm, String model, boolean mock) {
        if (failBackground) return null;
        View view = new View(); backgrounds.put(view, wm); return view;
    }
    public static void hideBlackBackground(WindowManager wm, View view) {
        if (view == null) return;
        if (backgrounds.remove(view) != wm) throw new AssertionError("background removed with wrong WindowManager");
        ++removed;
    }
}
""",
    "com/automanager/dashboard/LifecycleCheck.java": """
package com.automanager.dashboard;
import android.os.Handler;
import android.view.WindowManager;
import com.automanager.dashboard.utils.Util;
public class LifecycleCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static class TestService extends BaseDashboardService {
        final StringBuilder events = new StringBuilder();
        int shows, hides;
        boolean failHide;
        WindowManager childOwner;
        final IDashboardService api = (IDashboardService) onBind(null);
        protected void onDashboardShow() {
            ++shows; events.append("show:").append(vehicleModel).append(';');
            if (childOwner != null) check(childOwner == getWindowManager(), "old child used a new WM");
            childOwner = getWindowManager();
        }
        protected void onDashboardHide() {
            ++hides; events.append("hide:").append(vehicleModel).append(';');
            if (childOwner != null) check(childOwner == getWindowManager(), "hide used a new WM");
            childOwner = null;
            if (failHide) throw new RuntimeException("child cleanup failed");
        }
        protected void onDashboardOpenConfig() {}
        protected DashboardInfo onDashboardInfo() { return new DashboardInfo(); }
        void show(String model) { api.dbShow(model); Handler.drain(); }
        void assertDestroyed() {
            check(superDestroyed && Util.backgrounds.isEmpty(), "destroy left background or skipped super");
            try { getWindowManager(); throw new AssertionError("destroy retained WM"); }
            catch (RuntimeException expected) {}
        }
    }
    static void repeatedShowAndHide() {
        Util.reset(); TestService service = new TestService();
        service.show("A"); service.show("A");
        check(service.shows == 2 && Util.created == 1 && Util.backgrounds.size() == 1, "repeat show leaked or swallowed refresh");
        service.api.dbHide(); service.api.dbHide(); Handler.drain();
        check(service.hides == 1 && Util.removed == 1 && Util.backgrounds.isEmpty(), "hide is not idempotent");
        service.show("A");
        check(Util.created == 1 && Util.backgrounds.size() == 1, "same-model WM was not reused");
        service.onDestroy(); service.assertDestroyed();
    }
    static void modelChangeCleansOldChildFirst() {
        Util.reset(); TestService service = new TestService();
        service.show("A"); service.show("B");
        check(service.events.toString().equals("show:A;hide:A;show:B;"), "model change callback order");
        check(Util.created == 2 && Util.removed == 1 && Util.backgrounds.size() == 1, "model change leaked background");
        service.onDestroy(); service.assertDestroyed();
    }
    static void reloadSequenceIsPreserved() {
        Util.reset(); TestService service = new TestService(); service.show("A");
        service.api.dbHide(); service.api.dbShow("A"); Handler.drain();
        check(service.events.toString().equals("show:A;hide:A;show:A;"), "hide/show reload was coalesced");
        service.onDestroy(); service.assertDestroyed();
    }
    static void destroyCancelsRequests() throws Exception {
        Util.reset(); final TestService service = new TestService(); service.show("A");
        service.api.dbHide(); service.api.dbShow("B"); service.onDestroy();
        service.api.dbShow("C"); service.api.dbHide(); Handler.drain();
        check(service.shows == 1 && service.hides == 1 && Handler.pendingCount() == 0, "destroy ran stale requests");
        service.assertDestroyed();
        Util.reset(); final TestService racing = new TestService();
        Thread producer = new Thread(new Runnable() { public void run() {
            for (int i = 0; i < 10000; ++i) { racing.api.dbShow("A"); racing.api.dbHide(); }
        }});
        producer.start(); racing.onDestroy(); producer.join(); Handler.drain();
        check(Handler.pendingCount() == 0 && racing.shows == 0 && Util.created == 0, "request raced past destroy");
        racing.assertDestroyed();
    }
    static void failuresAndChildFirstDestroy() {
        Util.reset(); TestService service = new TestService();
        Util.failCreate = true; service.show("A"); service.api.dbHide(); Handler.drain();
        check(service.shows == 0 && service.hides == 0, "failed create still called child");
        Util.failCreate = false; Util.failBackground = true; service.show("A");
        Util.failBackground = false; service.show("A");
        check(Util.created == 1 && Util.backgrounds.size() == 1, "background retry failed");
        service.childOwner = null; // ScreencapViewService cleans its child before super.onDestroy.
        service.onDestroy(); service.assertDestroyed();
        Util.reset(); service = new TestService(); service.show("A"); service.failHide = true;
        try { service.onDestroy(); throw new AssertionError("expected child failure"); }
        catch (RuntimeException expected) {}
        service.assertDestroyed();
    }
    public static void main(String[] args) throws Exception {
        repeatedShowAndHide(); modelChangeCleansOldChildFirst(); reloadSequenceIsPreserved();
        destroyCancelsRequests(); failuresAndChildFirstDestroy();
        System.out.println("BaseDashboardService lifecycle checks passed");
    }
}
""",
}


def main():
    with tempfile.TemporaryDirectory(prefix="dashboard-lifecycle-") as directory:
        root = pathlib.Path(directory)
        paths = []
        for name, source in SOURCES.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            paths.append(str(path))
        actual = ROOT / "src/main/java/com/automanager/dashboard/BaseDashboardService.java"
        subprocess.run(["javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                        "-d", str(root), *paths, str(actual)], check=True)
        subprocess.run(["java", "-cp", str(root), "com.automanager.dashboard.LifecycleCheck"], check=True)


if __name__ == "__main__":
    main()
