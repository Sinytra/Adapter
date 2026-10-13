package org.sinytra.adapter.model;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.util.MixinBridge;

import java.util.Optional;

public record MethodQualifier(@Nullable String owner, @Nullable String name, @Nullable String desc) {

    public MethodQualifier(@Nullable String name, @Nullable String desc) {
        this(null, name, desc);
    }

    public static Optional<MethodQualifier> parse(String qualifier) {
        MixinBridge.MemberSelector selector = MixinBridge.parseMemberSelector(qualifier);
        if (selector == null || selector.field() || selector.maxMatches() != 1 || selector.name() == null && selector.desc() == null) {
            return Optional.empty();
        }
        String owner = selector.owner() != null ? Type.getObjectType(selector.owner()).getDescriptor() : null;
        return Optional.of(new MethodQualifier(owner, selector.name(), selector.desc()));
    }

    public static MethodQualifier create(ClassNode classNode, MethodNode methodNode) {
        return new MethodQualifier(Type.getObjectType(classNode.name).getDescriptor(), methodNode.name, methodNode.desc);
    }

    public static MethodQualifier create(MethodNode methodNode) {
        return new MethodQualifier(null, methodNode.name, methodNode.desc);
    }

    public static MethodQualifier create(MethodInsnNode minsn) {
        return new MethodQualifier(Type.getObjectType(minsn.owner).getDescriptor(), minsn.name, minsn.desc);
    }

    @Nullable
    public String internalOwnerName() {
        return this.owner != null ? Type.getType(this.owner).getInternalName() : null;
    }

    public boolean matches(MethodQualifier other) {
        return matches(other.owner(), other.name(), other.desc());
    }

    public boolean matches(MethodInsnNode insn) {
        return matches(Type.getObjectType(insn.owner).getDescriptor(), insn.name, insn.desc);
    }

    public boolean matches(@Nullable String owner, @Nullable String name, @Nullable String desc) {
        return (this.owner == null || this.owner.equals(owner))
            && (this.name != null ? this.name.equals(name) : this.desc != null && name != null && !name.startsWith("<"))
            && (this.desc == null || this.desc.equals(desc));
    }

    public boolean isFull() {
        return this.owner != null && this.name != null && this.desc != null;
    }

    public String asDescriptor() {
        String result = "";
        if (this.owner != null) {
            result += this.owner;
        }
        return result + (this.name != null ? this.name : "") + (this.desc != null ? this.desc : "");
    }

    public MethodQualifier withOwner(@Nullable Type owner) {
        return new MethodQualifier(owner == null ? null : owner.getDescriptor(), this.name, this.desc);
    }

    public MethodQualifier ignoreDesc() {
        return new MethodQualifier(this.owner, this.name, null);
    }
}
