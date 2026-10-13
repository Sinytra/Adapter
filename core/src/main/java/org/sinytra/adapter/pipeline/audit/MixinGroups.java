package org.sinytra.adapter.pipeline.audit;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.util.MixinBridge;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixinGroups {
    private final Map<String, GroupInfo> groups;

    private MixinGroups(Map<String, GroupInfo> groups) {
        this.groups = Map.copyOf(groups);
    }

    @Nullable
    public GroupInfo getGroupInfo(MethodNode methodNode) {
        String name = MixinBridge.getGroupName(methodNode);
        return name != null ? this.groups.get(name) : null;
    }

    public static MixinGroups create(List<MethodNode> methods) {
        Map<String, GroupInfo> groups = new HashMap<>();
        for (MixinBridge.InjectorGroup group : MixinBridge.findInjectorGroups(methods)) {
            groups.put(group.name(), new GroupInfo(group.members(), group.minRequired()));
        }
        return new MixinGroups(groups);
    }

    public record GroupInfo(Collection<MethodNode> members, int minRequired) {
    }
}
