package org.sinytra.adapter.next.pipeline.config;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

public interface MutableConfiguration extends Configuration {
    static MutableConfiguration create() {
        return new ConfigurationImpl();
    }

    MutableConfiguration inheritMixinType();
    MutableConfiguration setMixinType(String mixinType);

    MutableConfiguration inheritTargetClass();
    void setTargetClass(String targetClass);

    MutableConfiguration inheritTargetMethod();
    MutableConfiguration setTargetMethod(MethodInsnNode insn);
    MutableConfiguration setTargetMethod(MethodQualifier targetMethod);
    MutableConfiguration setTargetMethod(MethodNode methodNode);

    MutableConfiguration inheritAtData();
    MutableConfiguration setAtData(AtData atData);

    MutableConfiguration inheritParameters();
    void setParameters(MethodParameters parameters);

    MutableConfiguration inheritReturnType();
    void setReturnType(Type returnType);

    MutableConfiguration inheritShouldDelete();
    MutableConfiguration setShouldDelete(boolean delete);

    <T> MutableConfiguration setProperty(String key, @Nullable T value);

    void mergeFrom(Configuration other);
}
