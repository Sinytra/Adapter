package org.sinytra.adapter.env.param;

import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.util.TypeConstants;

import java.util.*;
import java.util.function.Predicate;

public class MethodParameters implements Copiable<MethodParameters> {

    public enum ParamGroupType {
        SINGLE,
        VARIABLE
    }

    public record ParamGroup(ParamGroupType type, String name, Predicate<Parameter> predicate) {
        public static final ParamGroup METHOD_PARAMS = new ParamGroup(ParamGroupType.VARIABLE, "method_params", i -> true);
        public static final ParamGroup CAPTURED_PARAMS = new ParamGroup(ParamGroupType.VARIABLE, "captured_params", i -> !i.isLocalOrShare());

        public static final ParamGroup SINGLE_ANY = new ParamGroup(ParamGroupType.SINGLE, "single_any", i -> true);
        public static final ParamGroup CI_CIR = new ParamGroup(ParamGroupType.SINGLE, "ci_cir", i -> i.type().equals(TypeConstants.CI_TYPE) || i.type().equals(TypeConstants.CIR_TYPE));
        public static final ParamGroup OPERATION = new ParamGroup(ParamGroupType.SINGLE, "operation", i -> i.type().equals(TypeConstants.OPERATION_TYPE));

        public static final ParamGroup LOCALS = new ParamGroup(ParamGroupType.VARIABLE, "locals", Parameter::isLocalOrShare);
    }

    private final Map<ParamGroup, List<Parameter>> groups;
    private final List<ParamGroup> order;
    private final Map<Parameter, Parameter> mapping;

    private MethodParameters(Map<ParamGroup, List<Parameter>> groups, List<ParamGroup> order) {
        this(groups, order, new HashMap<>());
    }

    private MethodParameters(Map<ParamGroup, List<Parameter>> groups, List<ParamGroup> order, Map<Parameter, Parameter> mapping) {
        this.groups = new HashMap<>();
        groups.forEach((k,v) -> this.groups.put(k, new ArrayList<>(v)));
        
        this.order = order;
        this.mapping = mapping;
    }

    public List<ParamGroup> getOrder() {
        return this.order;
    }

    public void mapParameter(Parameter old, Parameter replacement) {
        this.mapping.put(old, replacement);
    }

    public boolean has(ParamGroup group) {
        return this.groups.containsKey(group);
    }

    public List<Type> getTypes(ParamGroup group) {
        return get(group).stream()
            .map(Parameter::type)
            .toList();
    }

    public List<Parameter> get(ParamGroup group) {
        return Objects.requireNonNull(this.groups.get(group), "Group %s is not available".formatted(group.name()));
    }

    public void add(ParamGroup group, Parameter param) {
        if (this.groups.containsKey(group)) {
            this.groups.get(group).add(param);
        }
    }

    public void set(ParamGroup group, Parameter param) {
        set(group, List.of(param));
    }

    public void setTypes(ParamGroup group, List<Type> params) {
        set(group, params.stream().map(Parameter::simple).toList());
    }

    public void set(ParamGroup group, List<Parameter> params) {
        if (!this.groups.containsKey(group)) {
            throw new IllegalArgumentException("Group %s is not available".formatted(group.name()));
        }
        this.groups.put(group, new ArrayList<>(params));
    }

    public List<Type> mergeTypes() {
        return merge().stream()
            .map(Parameter::type)
            .toList();
    }

    public List<Parameter> merge() {
        List<Parameter> result = new ArrayList<>();
        for (ParamGroup group : this.order) {
            result.addAll(get(group));
        }
        return result;
    }

    public Map<Parameter, Parameter> getMapping() {
        return ImmutableMap.copyOf(this.mapping);
    }

    @Override
    public MethodParameters copy() {
        Map<ParamGroup, List<Parameter>> groups = new HashMap<>();
        this.groups.forEach((group, params) -> groups.put(group, new ArrayList<>(params)));

        List<ParamGroup> order = new ArrayList<>(this.order);
        Map<Parameter, Parameter> mapping = new HashMap<>(this.mapping);
        return new MethodParameters(groups, order, mapping);
    }

    public static MethodParameters create(MethodNode method, List<ParamGroup> groups) {
        List<Parameter> parameters = Parameters.parse(method);
        return create(parameters, groups);
    }

    private static MethodParameters create(List<Parameter> params, List<ParamGroup> groups) {
        Map<ParamGroup, List<Parameter>> results = new HashMap<>();
        for (ParamGroup type : groups) {
            results.put(type, new ArrayList<>());
        }

        int groupIndex = 0;

        for (int paramIndex = 0; paramIndex < params.size() && groupIndex < groups.size(); ) {
            ParamGroup group = groups.get(groupIndex);
            ParamGroup nextGroup = groupIndex + 1 < groups.size() ? groups.get(groupIndex + 1) : null;

            Parameter param = params.get(paramIndex);
            List<Parameter> output = results.get(group);

            if (group.type() == ParamGroupType.SINGLE) {
                if (!group.predicate().test(param)) {
                    throw new IllegalStateException("Unexpected single parameter: " + param);
                }

                output.add(param);
                paramIndex++;
                groupIndex++;
            } else if (group.type() == ParamGroupType.VARIABLE) {
                if (group.predicate().test(param)) {
                    if (nextGroup != null && nextGroup.predicate().test(param)) {
                        if (nextGroup.type() == ParamGroupType.SINGLE) {
                            groupIndex++;
                            continue;
                        }
                        // Two subsequent groups cannot both match a parameter
                        else {
                            throw new IllegalStateException("Ambiguous match for param %s in groups %s and %s"
                                .formatted(param.type(), group.name(), groups.get(groupIndex + 1).name()));
                        }
                    }

                    output.add(param);
                    paramIndex++;
                } else {
                    groupIndex++;
                }
            }
        }

        return new MethodParameters(results, groups);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<ParamGroup, List<Parameter>> groups = new HashMap<>();
        private final List<ParamGroup> order = new ArrayList<>();

        public Builder putType(ParamGroup group, Type param) {
            return put(group, Parameter.simple(param));
        }

        public Builder put(ParamGroup group, Parameter param) {
            return put(group, List.of(param));
        }

        public Builder putTypes(ParamGroup group, List<Type> params) {
            return put(group, params.stream().map(Parameter::simple).toList());
        }

        public Builder put(ParamGroup group, List<Parameter> params) {
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
