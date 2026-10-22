package cn.g.space;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;

public final class AppStore {
    private final SharedPreferences prefs;
    public AppStore(Context context) { prefs = context.getSharedPreferences("space", Context.MODE_PRIVATE); }
    public boolean initialized() { return prefs.contains("apps"); }
    public AppSelection read() {
        ArrayList<String> names = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(prefs.getString("apps", "[]"));
            for (int i = 0; i < json.length(); i++) names.add(json.optString(i));
        } catch (Exception ignored) {}
        return new AppSelection(names);
    }
    public void write(AppSelection apps) { prefs.edit().putString("apps", new JSONArray(apps.list()).toString()).apply(); }
}
