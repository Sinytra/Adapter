package org.sinytra.adapter.next.env;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OrderedRegistry<U> {
    private final List<U> instances;
    private boolean frozen;

    public OrderedRegistry() {
        this.instances = new ArrayList<>();
    }

    public void freeze() {
        this.frozen = true;
    }

    public List<U> getAll() {
        return this.instances;
    }

    public void addBefore(Class<? extends U> type, U entry) {
        assertNotFrozen();
        assertUnique(entry);

        U original = getOrThrow(type);
        int index = this.instances.indexOf(original);

        this.instances.add(index, entry);
    }

    public void addAfter(Class<? extends U> type, U entry) {
        assertNotFrozen();
        assertUnique(entry);

        U original = getOrThrow(type);
        int index = this.instances.indexOf(original);

        this.instances.add(index + 1, entry);
    }

    public <T extends U> T getOrThrow(Class<T> type) {
        return Objects.requireNonNull(get(type), "Entry not found for type %s".formatted(type));
    }

    @SuppressWarnings("unchecked")
    public void add(U entry) {
        assertNotFrozen();
        assertUnique(entry);

        this.instances.add(entry);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends U> T get(Class<T> type) {
        for (U entry : this.instances) {
            if (type == entry.getClass()) {
                return (T) entry;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void assertUnique(U entry) {
        if (get((Class<? extends U>) entry.getClass()) != null) {
             throw new IllegalArgumentException("Duplicate entry for type %s".formatted(entry.getClass()));
        }
    }

    private void assertNotFrozen() {
        if (this.frozen) {
            throw new IllegalStateException("Already frozen");
        }
    }
}
