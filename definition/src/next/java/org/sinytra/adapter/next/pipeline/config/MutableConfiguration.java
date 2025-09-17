package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

public interface MutableConfiguration extends Configuration {
    void setTargetClass(String targetClass);

    void setTargetMethod(MethodQualifier targetMethod);

    void setAtData(AtData atData);

    void setParameters(MethodParameters parameters);

    void setReturnType(Type returnType);

    <T> void setProperty(String key, T value);
}
