package org.sinytra.adapter.patch.analysis.selector;

import org.jetbrains.annotations.Nullable;

public class MethodMatcher {
    private final String name;
    @Nullable
    private final String desc;

    public MethodMatcher(String method) {
        int descIndex = method.indexOf('(');
        this.name = descIndex == -1 ? method : method.substring(0, descIndex);
        this.desc = descIndex == -1 ? null : method.substring(descIndex);
    }

    public boolean matches(String name, String desc) {
        return this.name.equals(name) && (this.desc == null || desc == null || this.desc.equals(desc));
    }
}
