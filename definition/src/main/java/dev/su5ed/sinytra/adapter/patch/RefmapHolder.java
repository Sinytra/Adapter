package dev.su5ed.sinytra.adapter.patch;

public interface RefmapHolder {
    String remap(String cls, String reference);

    void copyEntries(String from, String to);
}
