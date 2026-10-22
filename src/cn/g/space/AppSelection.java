package cn.g.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Ordered package names. Removing an item only changes this local list. */
public final class AppSelection {
    private final ArrayList<String> packages = new ArrayList<>();
    public AppSelection(Collection<String> initial) { for (String p : initial) add(p); }
    public boolean add(String p) {
        if (p == null || !p.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || packages.contains(p)) return false;
        packages.add(p); return true;
    }
    public boolean remove(String p) { return packages.remove(p); }
    public boolean contains(String p) { return packages.contains(p); }
    public int indexOf(String p) { return packages.indexOf(p); }
    public boolean move(String p, int destination) {
        int source = packages.indexOf(p);
        if (source < 0 || destination < 0 || destination >= packages.size() || source == destination) return false;
        packages.remove(source); packages.add(destination, p); return true;
    }
    public List<String> list() { return new ArrayList<>(packages); }
}
