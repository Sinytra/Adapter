package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

public interface MutableConfiguration extends Configuration {
    void inheritTargetClass();
    void setTargetClass(String targetClass);

    void inheritTargetMethod();
    void setTargetMethod(MethodQualifier targetMethod);
    void setTargetMethod(MethodNode methodNode);

    void inheritAtData();
    void setAtData(AtData atData);

    void inheritParameters();
    void setParameters(MethodParameters parameters);

    void inheritReturnType();
    void setReturnType(Type returnType);

    <T> void setProperty(String key, T value);
}
