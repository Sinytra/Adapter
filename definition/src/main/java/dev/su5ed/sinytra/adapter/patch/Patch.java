package dev.su5ed.sinytra.adapter.patch;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public interface Patch {
    Predicate<String> INJECT = PatchImpl.INJECT_ANN::equals;
    Predicate<String> REDIRECT = PatchImpl.REDIRECT_ANN::equals;
    Predicate<String> MODIFY_ARG = PatchImpl.MODIFY_ARG_ANN::equals;

    static Builder builder() {
        return new PatchImpl.BuilderImpl();
    }

    boolean apply(ClassNode classNode);

    interface Builder {
        Builder targetClass(String... targets);

        Builder targetMethod(String... targets);

        Builder targetMixinType(String annotationDesc);

        Builder targetAnnotationValues(Predicate<Map<String, AnnotationValueHandle<?>>> values);

        Builder targetInjectionPoint(String target);

        Builder targetInjectionPoint(String value, String target);

        Builder modifyInjectionPoint(String target);

        Builder modifyInjectionPoint(String value, String target);

        Builder modifyParams(List<Type> replacementTypes);

        Builder modifyParams(List<Type> replacementTypes, @Nullable LVTFixer lvtFixer);

        Builder modifyTarget(String... methods);

        Builder modifyVariableIndex(int start, int offset);

        Builder disable();

        Builder transform(MethodTransform transformer);

        Patch build();
    }
}
