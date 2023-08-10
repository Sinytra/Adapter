package dev.su5ed.sinytra.adapter.patch;

import java.util.ArrayList;
import java.util.List;

public class PatchContext {
    private final List<Runnable> postApply = new ArrayList<>();

    public void postApply(Runnable consumer) {
        this.postApply.add(consumer);
    }

    public void run() {
        this.postApply.forEach(Runnable::run);
    }
}
