package org.sinytra.adapter.api;

public interface RefmapHolder {
    String remap(String cls, String reference);

    void copyEntries(String from, String to);
}
