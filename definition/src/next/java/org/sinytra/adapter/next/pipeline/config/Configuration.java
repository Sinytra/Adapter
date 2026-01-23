package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

public interface Configuration extends PropertyContainer {
    String getMixinType();

    String getTargetClass();

    MethodQualifier getTargetMethod();

    AtData getAtData();

    MethodParameters getParameters();

    Type getReturnType();

    boolean shouldDelete();

    boolean isCancellable();

    MutableConfiguration childConfig();

    MutableConfiguration copyClean();

    MutableConfiguration copyClean(PropertyContainerTemplate template);

    MutableConfiguration copy();
}
