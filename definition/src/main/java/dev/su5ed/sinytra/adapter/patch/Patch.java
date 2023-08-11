package dev.su5ed.sinytra.adapter.patch;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface Patch {
    String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    String MODIFY_VAR = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    static Builder builder() {
        return new PatchImpl.BuilderImpl();
    }

    boolean apply(ClassNode classNode, MixinRemaper remaper);

    interface Builder {
        Builder targetClass(String... targets);

        Builder targetMethod(String... targets);

        Builder targetMixinType(String annotationDesc);

        Builder targetAnnotationValues(Predicate<Map<String, AnnotationValueHandle<?>>> values);

        Builder targetInjectionPoint(String target);

        Builder targetInjectionPoint(String value, String target);

        Builder modifyInjectionPoint(String target);

        Builder modifyInjectionPoint(String value, String target);

        Builder modifyParams(Consumer<List<Type>> operator);

        Builder modifyParams(Consumer<List<Type>> operator, @Nullable LVTFixer lvtFixer);

        Builder setParams(List<Type> parameters);

        Builder modifyTarget(String... methods);

        Builder modifyVariableIndex(int start, int offset);

        Builder disable();

        Builder transform(MethodTransform transformer);

        Patch build();
    }
}
