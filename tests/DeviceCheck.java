package cn.g.space;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;

/** Exercises production persistence using the Android JSON implementation without installing. */
public final class DeviceCheck {
    static void check(boolean v, String reason) { if (!v) throw new AssertionError(reason); }
    static final class Prefs {
        final Map<String, String> values = new HashMap<>();
        final SharedPreferences prefs;
        Prefs() {
            final Object[] editor = new Object[1];
            editor[0] = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.Editor.class}, (o, m, a) -> {
                if (m.getName().equals("putString")) { values.put((String) a[0], (String) a[1]); return editor[0]; }
                if (m.getName().equals("apply")) return null;
                if (m.getName().equals("commit")) return true;
                throw new UnsupportedOperationException(m.getName());
            });
            prefs = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.class}, (o, m, a) -> {
                if (m.getName().equals("getString")) return values.containsKey(a[0]) ? values.get(a[0]) : a[1];
                if (m.getName().equals("contains")) return values.containsKey(a[0]);
                if (m.getName().equals("edit")) return editor[0];
                throw new UnsupportedOperationException(m.getName());
            });
        }
    }
    public static void main(String[] args) {
        try { run(new File(args[0])); System.exit(0); }
        catch (Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    static void run(File directory) throws Exception {
        Prefs prefs = new Prefs();
        Context local = new ContextWrapper(null) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs.prefs; }
        };
        AppStore store = new AppStore(local);
        check(!store.initialized(), "fresh install is distinct from deliberately empty selection");
        AppSelection selection = new AppSelection(Arrays.asList("cn.pulse.player", "app.ai", "app.tool"));
        store.write(selection);
        check(new AppStore(local).read().list().equals(selection.list()), "Android JSON preferences roundtrip");
        ReorderSession drag = new ReorderSession(selection, "app.tool"); drag.moveTo(0);
        check(store.read().list().equals(selection.list()), "hover order is not persisted");
        check(drag.commit(selection), "valid drag commits");
        selection.remove("app.ai"); store.write(selection);
        check(new AppStore(local).read().list().equals(Arrays.asList("app.tool", "cn.pulse.player")), "reorder and removal persist");
        ReorderSession canceled = new ReorderSession(selection, "cn.pulse.player"); canceled.moveTo(0); canceled.cancel();
        check(!canceled.commit(selection) && store.read().list().equals(selection.list()), "cancel does not change persisted order");
        store.write(new AppSelection(Collections.emptyList()));
        check(store.initialized() && store.read().list().isEmpty(), "empty selection stays empty on next launch");
        prefs.values.put("apps", "malformed"); check(store.read().list().isEmpty(), "recover malformed preference data");
        System.out.println("PASS: Android persistence, drag commit/cancel, removal, explicit empty selection and corrupt-data recovery");
    }
}
