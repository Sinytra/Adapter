package org.sinytra.adapter.env.ctx;

public interface RefmapHolder {
    String remap(String cls, String reference);

    void copyEntries(String from, String to);
}
