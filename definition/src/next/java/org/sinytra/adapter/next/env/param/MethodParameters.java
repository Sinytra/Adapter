package org.sinytra.adapter.next.env.param;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.*;
import java.util.function.Predicate;

public class MethodParameters {
    public sealed interface ParamGroup {
        ParamGroup METHOD_PARAMS = new Variable("method_params");
        ParamGroup CAPTURED_PARAMS = new Variable("captured_params");

        ParamGroup SINGLE_ANY = new Single("single_any", t -> true);
        ParamGroup CI_CIR = new Single("ci_cir", t -> t.equals(AdapterUtil.CI_TYPE) || t.equals(AdapterUtil.CIR_TYPE));
        ParamGroup OPERATION = new Single("operation", t -> t.equals(AdapterUtil.OPERATION_TYPE));

        ParamGroup LOCALS = new Variable("locals");

        String name();

        record Single(String name, Predicate<Type> predicate) implements ParamGroup {
            @Override
            public @NotNull String toString() {
                return "Single[%s]".formatted(this.name);
            }
        }

        record Variable(String name) implements ParamGroup {
            @Override
            public @NotNull String toString() {
                return "Variable[%s]".formatted(this.name);
            }
        }
    }

    private final Map<ParamGroup, List<Type>> groups;
    private final List<ParamGroup> order;

    private MethodParameters(Map<ParamGroup, List<Type>> groups, List<ParamGroup> order) {
        this.groups = new HashMap<>(groups);
        this.order = order;
    }

    public List<Type> get(ParamGroup group) {
        return Objects.requireNonNull(this.groups.get(group), "Group %s is not available".formatted(group.name()));
    }

    public void set(ParamGroup group, List<Type> params) {
        if (!this.groups.containsKey(group)) {
            throw new IllegalArgumentException("Group %s is not available".formatted(group.name()));
        }
        this.groups.put(group, params);
    }

    public List<Type> merge() {
        List<Type> result = new ArrayList<>();
        for (ParamGroup group : this.order) {
            result.addAll(this.groups.get(group));
        }
        return result;
    }

    public static List<Type> getParameterTypes(String desc) {
        return Arrays.asList(Type.getArgumentTypes(desc));
    }

    public static MethodParameters create(String desc, List<ParamGroup> groups) {
        return create(getParameterTypes(desc), groups);
    }

    public static MethodParameters create(List<Type> params, List<ParamGroup> types) {
        Map<ParamGroup, List<Type>> groups = new HashMap<>();
        for (ParamGroup type : types) {
            groups.put(type, new ArrayList<>());
        }

        int paramsIndex = 0;
        for (int i = 0; i < types.size(); i++) {
            ParamGroup group = types.get(i);
            ParamGroup next = i < types.size() - 1 ? types.get(i + 1) : null;

            if (group instanceof ParamGroup.Variable && next instanceof ParamGroup.Variable) {
                throw new IllegalStateException("Cannot follow VARIABLE group with another VARIABLE group");
            }

            List<Type> output = groups.get(group);
            for (; paramsIndex < params.size(); paramsIndex++) {
                Type param = params.get(paramsIndex);

                // SINGLE must always match
                if (group instanceof ParamGroup.Single single) {
                    if (!single.predicate.test(param)) {
                        throw new IllegalStateException("Unexpected single parameter: " + param);
                    }

                    output.add(param);
                    paramsIndex++;
                    break;
                }

                // Match VARIABLE params until one does not match
                if (next instanceof ParamGroup.Single single && single.predicate.test(param)) {
                    break;
                }

                output.add(param);
            }
        }

        return new MethodParameters(groups, types);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<ParamGroup, List<Type>> groups = new HashMap<>();
        private final List<ParamGroup> order = new ArrayList<>();

        public Builder put(ParamGroup group, List<Type> params) {
            if (this.order.contains(group)) {
                throw new IllegalStateException("Duplicate group " + group);
            }

            this.groups.put(group, new ArrayList<>(params));
            this.order.add(group);

            return this;
        }

        public MethodParameters build() {
            return new MethodParameters(
                this.groups,
                this.order
            );
        }
    }
}
