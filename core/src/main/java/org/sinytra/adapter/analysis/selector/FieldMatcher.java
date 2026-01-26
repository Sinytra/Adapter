package org.sinytra.adapter.analysis.selector;

import org.jetbrains.annotations.Nullable;

public class FieldMatcher {
    private final String name;
    @Nullable
    private final String desc;

    public FieldMatcher(String field) {
        int descIndex = field.indexOf(':');
        this.name = descIndex == -1 ? field : field.substring(0, descIndex);
        this.desc = descIndex == -1 ? null : field.substring(descIndex + 1);
    }

    public String getName() {
        return this.name;
    }

    public boolean matches(FieldMatcher other) {
        return matches(other.name, other.desc);
    }

    public boolean matches(String name, String desc) {
        return this.name.equals(name) && (this.desc == null || desc == null || this.desc.equals(desc));
    }
}
