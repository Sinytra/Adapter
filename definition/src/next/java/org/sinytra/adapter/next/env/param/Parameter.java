package org.sinytra.adapter.next.env.param;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.api.MixinConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Parameter {
    private final Type type;
    private final List<Annotation> annotations;

    public Parameter(Type type, List<Annotation> annotations) {
        this.type = type;
        this.annotations = ImmutableList.copyOf(annotations);
    }

    public Type getType() {
        return this.type;
    }

    public List<Annotation> getAnnotations() {
        return this.annotations;
    }

    public boolean isLocal() {
        return hasAnnotation(MixinConstants.LOCAL);
    }

    public boolean hasAnnotation(String desc) {
        return this.annotations.stream()
            .anyMatch(annotation -> annotation.getDesc().equals(desc));
    }
    
    public Builder extend() {
        Builder builder = builder(this.type);
        this.annotations.forEach(builder::annotate);
        return builder;
    }

    public static Parameter simple(Type type) {
        return new Parameter(type, List.of());
    }

    public static Builder builder(String typeDesc) {
        return builder(Type.getType(typeDesc));
    }
    
    public static Builder builder(Type type) {
        return new Builder(type);
    }

    public static class Builder {
        private final Type type;
        private final List<Annotation> annotations = new ArrayList<>();

        public Builder(Type type) {
            this.type = type;
        }

        public Builder annotate(String desc, Consumer<Annotation.Builder> consumer) {
            Annotation.Builder builder = Annotation.builder(desc);
            consumer.accept(builder);
            return annotate(builder.build());
        }

        public Builder annotate(Annotation annotation) {
            this.annotations.add(annotation);
            return this;
        }

        public Parameter build() {
            return new Parameter(this.type, this.annotations);
        }
    }
}
