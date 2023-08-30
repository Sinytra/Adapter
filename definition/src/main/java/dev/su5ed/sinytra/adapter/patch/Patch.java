package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;
import java.util.Map;
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
    // Mixinextras annotations
    String MODIFY_EXPR_VAL = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

    Pattern METHOD_REF_PATTERN = Pattern.compile("^(?<owner>L[a-zA-Z0-9/_$]+;)?(?<name>[a-zA-Z0-9_]+|<[a-z0-9_]+>)(?<desc>\\((?:\\[?[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_$]+;)*\\)(?:[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_;$]+))$");

    static Builder builder() {
        return new PatchInstance.BuilderImpl();
    }

    Result apply(ClassNode classNode, PatchEnvironment remaper);

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

    interface Builder {
        Builder targetClass(String... targets);

        Builder targetMethod(String... targets);

        Builder targetMixinType(String annotationDesc);

        Builder targetAnnotationValues(Predicate<Map<String, AnnotationValueHandle<?>>> values);

        default Builder targetInjectionPoint(String target) {
            return targetInjectionPoint(null, target);
        }

        Builder targetInjectionPoint(String value, String target);

        default Builder modifyInjectionPoint(String target) {
            return modifyInjectionPoint(null, target);
        }

        Builder modifyTargetClasses(Consumer<List<Type>> consumer);

        Builder modifyInjectionPoint(String value, String target);

        Builder modifyParams(Consumer<ModifyMethodParams.Builder> consumer);

        Builder modifyTarget(String... methods);

        Builder modifyVariableIndex(int start, int offset);

        Builder modifyMethodAccess(ModifyMethodAccess.AccessChange... changes);

        Builder modifyAnnotationValues(Predicate<AnnotationNode> annotation);

        Builder redirectShadowMethod(String original, String target, BiConsumer<MethodInsnNode, InsnList> callFixer);

        Builder disable();

        Builder transform(ClassTransform transformer);

        Builder transform(MethodTransform transformer);

        PatchInstance build();
    }
}
