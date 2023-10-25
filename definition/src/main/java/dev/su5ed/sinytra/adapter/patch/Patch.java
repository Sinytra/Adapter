package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMixinType;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public sealed interface Patch permits PatchInstance {
    String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    String MODIFY_VAR = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    String MODIFY_CONST = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    // Interface mixins
    String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    // Mixinextras annotations
    String MODIFY_EXPR_VAL = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

    Pattern METHOD_REF_PATTERN = Pattern.compile("^(?<owner>L[a-zA-Z0-9/_$]+;)?(?<name>[a-zA-Z0-9_]+|<[a-z0-9_]+>)(?<desc>\\((?:\\[?[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_$]+;)*\\)(?:[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_;$]+))$");

    static ClassPatchBuilder builder() {
        return new ClassPatchInstance.ClassPatchBuilderImpl();
    }

    static InterfacePatchBuilder interfaceBuilder() {
        return new InterfacePatchInstance.InterfaceClassPatchBuilderImpl();
    }

    Result apply(ClassNode classNode, PatchEnvironment remaper);

    Codec<? extends Patch> codec();

    enum Result {
        PASS,
        APPLY,
        COMPUTE_FRAMES;

        public Result or(Result other) {
            if (this == PASS && other != PASS) {
                return other;
            }
            if (this == APPLY && other == COMPUTE_FRAMES) {
                return COMPUTE_FRAMES;
            }
            return this;
        }
    }

    interface Builder<T extends Builder<T>> {
        T targetClass(String... targets);

        T targetMixinType(String annotationDesc);

        T targetAnnotationValues(Predicate<AnnotationHandle> values);

        T modifyTargetClasses(Consumer<List<Type>> consumer);

        T modifyParams(Consumer<ModifyMethodParams.Builder> consumer);

        T modifyTarget(String... methods);

        T modifyVariableIndex(int start, int offset);

        T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes);

        T modifyAnnotationValues(Predicate<AnnotationHandle> annotation);

        T extractMixin(String targetClass);

        T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer);

        T transform(ClassTransform transformer);

        T transform(MethodTransform transformer);

        PatchInstance build();
    }

    interface ClassPatchBuilder extends Builder<ClassPatchBuilder> {
        ClassPatchBuilder targetMethod(String... targets);

        default ClassPatchBuilder targetInjectionPoint(String target) {
            return targetInjectionPoint(null, target);
        }

        ClassPatchBuilder targetInjectionPoint(String value, String target);

        default ClassPatchBuilder targetConstant(double doubleValue) {
            return targetMixinType(Patch.MODIFY_CONST)
                .targetAnnotationValues(handle -> handle.getNested("constant")
                    .flatMap(cst -> cst.<Double>getValue("doubleValue")
                        .map(val -> val.get() == doubleValue))
                    .orElse(false));
        }

        default ClassPatchBuilder modifyInjectionPoint(String value, String target) {
            return modifyInjectionPoint(value, target, true);
        }

        ClassPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues);

        default ClassPatchBuilder modifyInjectionPoint(String target) {
            return modifyInjectionPoint(null, target);
        }
        
        ClassPatchBuilder redirectShadowMethod(String original, String target, BiConsumer<MethodInsnNode, InsnList> callFixer);

        ClassPatchBuilder disable();
    }

    interface InterfacePatchBuilder extends Builder<InterfacePatchBuilder> {
        InterfacePatchBuilder targetField(String... targets);

        InterfacePatchBuilder modifyValue(String value);
    }
}
