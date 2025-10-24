package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Optional;

public interface Configuration {
    String getTargetClass();

    MethodQualifier getTargetMethod();

    AtData getAtData();

    MethodParameters getParameters();
    
    Type getReturnType();

    boolean hasProperty(String key);

    <T> Optional<T> getProperty(String key);
}
