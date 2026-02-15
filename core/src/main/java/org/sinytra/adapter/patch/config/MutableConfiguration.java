package org.sinytra.adapter.patch.config;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.util.MethodQualifier;

public interface MutableConfiguration extends Configuration, MutablePropertyContainer {
    static MutableConfiguration create() {
        return create(null);
    }

    static MutableConfiguration create(@Nullable PropertyContainerTemplate template) {
        return new ConfigurationImpl(template);
    }

    MutableConfiguration inheritMixinType();

    MutableConfiguration setMixinType(String mixinType);

    MutableConfiguration inheritTargetClass();
    
    default MutableConfiguration setTargetClass(ClassNode targetClass) {
        return setTargetClass(targetClass.name);
    }

    MutableConfiguration setTargetClass(String targetClass);

    MutableConfiguration inheritTargetMethod();

    MutableConfiguration setTargetMethod(MethodInsnNode insn);

    MutableConfiguration setTargetMethod(MethodQualifier targetMethod);

    MutableConfiguration setTargetMethod(MethodNode methodNode);

    MutableConfiguration inheritAtData();

    MutableConfiguration setAtData(AtData atData);

    MutableConfiguration inheritParameters();

    MutableConfiguration setParameters(MethodParameters parameters);

    MutableConfiguration inheritReturnType();

    MutableConfiguration setReturnType(Type returnType);

    MutableConfiguration inheritShouldDelete();

    MutableConfiguration setShouldDelete(boolean delete);

    <T> MutableConfiguration setProperty(PropertyKey<T> key, @Nullable T value);

    <T> MutableConfiguration removeProperty(PropertyKey<T> key);

    MutableConfiguration mergeFrom(@Nullable PropertyContainer other);

    MutableConfiguration inheritProperyIfAbsent(PropertyKey<?> key);
}
