package org.sinytra.adapter.patch.config;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.util.MethodQualifier;

public interface MutableConfiguration extends Configuration, MutablePropertyContainer {
    static MutableConfiguration create() {
        return create(null);
    }

    static MutableConfiguration create(@Nullable PropertyContainerTemplate template) {
        return new ConfigurationImpl(template);
    }

    <T> MutableConfiguration setProperty(PropertyKey<T> key, @Nullable T value);

    <T> MutableConfiguration removeProperty(PropertyKey<T> key);

    MutableConfiguration mergeFrom(@Nullable PropertyContainer other);

    MutableConfiguration inheritProperty(PropertyKey<?> key);

    MutableConfiguration inheritProperyIfAbsent(PropertyKey<?> key);

    default MutableConfiguration inheritMixinType() {
        return inheritProperty(ControlKeys.MIXIN_TYPE);
    }

    default MutableConfiguration setMixinType(String mixinType) {
        return setProperty(ControlKeys.MIXIN_TYPE, mixinType);
    }

    default MutableConfiguration inheritTargetClass() {
        return inheritProperty(ControlKeys.TARGET_CLASS);
    }

    default MutableConfiguration setTargetClass(ClassNode targetClass) {
        return setTargetClass(targetClass.name);
    }

    default MutableConfiguration setTargetClass(String targetClass) {
        return setProperty(ControlKeys.TARGET_CLASS, targetClass);
    }

    default MutableConfiguration inheritTargetMethod() {
        return inheritProperty(MixinKeys.TARGET_METHOD);
    }

    default MutableConfiguration setTargetMethod(MethodInsnNode insn) {
        return setTargetMethod(MethodQualifier.create(insn));
    }

    default MutableConfiguration setTargetMethod(MethodNode methodNode) {
        return setTargetMethod(MethodQualifier.create(methodNode));
    }

    default MutableConfiguration setTargetMethod(MethodQualifier targetMethod) {
        return setProperty(MixinKeys.TARGET_METHOD, targetMethod);
    }

    default MutableConfiguration inheritAtData() {
        return inheritProperty(MixinKeys.TARGET_AT);
    }

    default MutableConfiguration setAtData(AtData atData) {
        return setProperty(MixinKeys.TARGET_AT, atData);
    }

    default MutableConfiguration inheritParameters() {
        return inheritProperty(ControlKeys.PARAMETERS);
    }

    default MutableConfiguration setParameters(MethodParameters parameters) {
        return setProperty(ControlKeys.PARAMETERS, parameters);
    }

    default MutableConfiguration inheritReturnType() {
        return inheritProperty(ControlKeys.RETURN_TYPE);
    }

    default MutableConfiguration setReturnType(Type returnType) {
        return setProperty(ControlKeys.RETURN_TYPE, returnType);
    }

    default MutableConfiguration inheritSignature() {
        return inheritParameters().inheritReturnType();
    }

    default MutableConfiguration setShouldDelete(boolean delete) {
        return setProperty(ControlKeys.DELETE, delete);
    }
}
