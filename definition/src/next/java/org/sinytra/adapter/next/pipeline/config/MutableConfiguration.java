package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

public interface MutableConfiguration extends Configuration {
    static MutableConfiguration create() {
        return new ConfigurationImpl();
    }

    void inheritTargetClass();
    void setTargetClass(String targetClass);

    MutableConfiguration inheritTargetMethod();
    void setTargetMethod(MethodQualifier targetMethod);
    MutableConfiguration setTargetMethod(MethodNode methodNode);

    MutableConfiguration inheritAtData();
    MutableConfiguration setAtData(AtData atData);

    void inheritParameters();
    void setParameters(MethodParameters parameters);

    void inheritReturnType();
    void setReturnType(Type returnType);

    <T> MutableConfiguration setProperty(String key, T value);

    void mergeFrom(Configuration other);
}
