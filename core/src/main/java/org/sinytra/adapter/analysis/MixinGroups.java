package org.sinytra.adapter.analysis;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;
import org.spongepowered.asm.util.Annotations;

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
        AnnotationNode node = Annotations.getInvisible(methodNode, Group.class);
        if (node == null) return null;
        
        String name = Annotations.getValue(node, "name");
        if (name == null || name.isEmpty()) return null;
        
        return this.groups.get(name);
    }
    
    public static MixinGroups create(List<MethodNode> methods) {
        InjectorGroupInfo.Map infos = new InjectorGroupInfo.Map();
        Multimap<String, MethodNode> members = HashMultimap.create();

        for (MethodNode method : methods) {
            InjectorGroupInfo info = infos.parseGroup(method, "default");
            if (info.isDefault() || info.getName().equals("default")) continue;
            
            members.put(info.getName(), method);
        }
        
        Map<String, GroupInfo> groups = new HashMap<>();
        members.asMap().forEach((name, mems) -> {
            InjectorGroupInfo info = infos.forName(name);
            groups.put(name, new GroupInfo(mems, info.getMinRequired()));
        });

        return new MixinGroups(groups);
    }

    public record GroupInfo(Collection<MethodNode> members, int minRequired) {}
}
