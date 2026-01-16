package org.sinytra.adapter.next.env.param;

import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;

import java.util.HashMap;
import java.util.Map;

public class Annotation {
    private final String desc;
    private final boolean visible;
    private final Map<String, Object> properties;

    public Annotation(String desc, boolean visible, Map<String, Object> properties) {
        this.desc = desc;
        this.visible = visible;
        this.properties = ImmutableMap.copyOf(properties);
    }

    public void accept(AnnotationVisitor visitor) {
        this.properties.forEach(visitor::visit);
    }

    public AnnotationNode toAnnotationNode() {
        AnnotationNode node = new AnnotationNode(this.desc);
        this.properties.forEach(node::visit);
        return node;
    }

    public String getDesc() {
        return this.desc;
    }

    public boolean isVisible() {
        return this.visible;
    }
    
    public static Annotation parse(AnnotationNode node, boolean visible) {
        Builder builder = builder(node.desc).visible(visible);
        if (node.values != null) {
            for (int i = 0; i + 1 < node.values.size(); i += 2) {
                String name = (String) node.values.get(i);
                Object value = node.values.get(i + 1);

                builder.put(name, value);
            }
        }
        return builder.build();
    }

    public static Builder builder(String desc) {
        return new Builder(desc);
    }

    public static class Builder {
        private final String desc;
        private boolean visible = false;
        private final Map<String, Object> properties = new HashMap<>();

        public Builder(String desc) {
            this.desc = desc;
        }

        public Builder put(String name, Object value) {
            this.properties.put(name, value);
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Annotation build() {
            return new Annotation(this.desc, this.visible, this.properties);
        }
    }
}
