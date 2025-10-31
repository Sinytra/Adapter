package org.sinytra.adapter.next.env.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.*;
import java.util.function.Predicate;

public class MethodParameters {
    public enum ParamGroupType {
        SINGLE,
        VARIABLE
    }

    public record ParamInfo(Type type, boolean isLocal) {
    }

    public record ParamGroup(ParamGroupType type, String name, Predicate<ParamInfo> predicate) {
        public static final ParamGroup METHOD_PARAMS = new ParamGroup(ParamGroupType.VARIABLE, "method_params", i -> true);
        public static final ParamGroup CAPTURED_PARAMS = new ParamGroup(ParamGroupType.VARIABLE, "captured_params", i -> !i.isLocal());

        public static final ParamGroup SINGLE_ANY = new ParamGroup(ParamGroupType.SINGLE, "single_any", i -> true);
        public static final ParamGroup CI_CIR = new ParamGroup(ParamGroupType.SINGLE, "ci_cir", i -> i.type().equals(AdapterUtil.CI_TYPE) || i.type().equals(AdapterUtil.CIR_TYPE));
        public static final ParamGroup OPERATION = new ParamGroup(ParamGroupType.SINGLE, "operation", i -> i.type().equals(AdapterUtil.OPERATION_TYPE));

        public static final ParamGroup LOCALS = new ParamGroup(ParamGroupType.VARIABLE, "locals", ParamInfo::isLocal);
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

    private static List<ParamInfo> getParamInfo(MethodNode method) {
        List<Type> params = getParameterTypes(method.desc);
        List<ParamInfo> infos = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            infos.add(new ParamInfo(params.get(i), AdapterUtil.isParamAnnotated(method, i, MixinConstants.LOCAL)));
        }
        return infos;
    }

    public static MethodParameters create(MethodNode method, List<ParamGroup> groups) {
        return create(getParamInfo(method), groups);
    }

    private static MethodParameters create(List<ParamInfo> params, List<ParamGroup> groups) {
        Map<ParamGroup, List<Type>> results = new HashMap<>();
        for (ParamGroup type : groups) {
            results.put(type, new ArrayList<>());
        }

        int groupIndex = 0;

        for (int paramIndex = 0; paramIndex < params.size() && groupIndex < groups.size(); ) {
            ParamGroup group = groups.get(groupIndex);
            ParamGroup nextGroup = groupIndex + 1 < groups.size() ? groups.get(groupIndex + 1) : null;

            ParamInfo param = params.get(paramIndex);
            List<Type> output = results.get(group);

            if (group.type() == ParamGroupType.SINGLE) {
                if (!group.predicate().test(param)) {
                    throw new IllegalStateException("Unexpected single parameter: " + param);
                }

                output.add(param.type());
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

                    output.add(param.type());
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
        private final Map<ParamGroup, List<Type>> groups = new HashMap<>();
        private final List<ParamGroup> order = new ArrayList<>();

        public Builder put(ParamGroup group, Type param) {
            return put(group, List.of(param));
        }

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
