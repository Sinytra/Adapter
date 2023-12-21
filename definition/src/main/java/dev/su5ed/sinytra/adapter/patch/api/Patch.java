package dev.su5ed.sinytra.adapter.patch.api;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.ClassPatchInstance;
import dev.su5ed.sinytra.adapter.patch.InterfacePatchInstance;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyInjectionTarget;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMixinType;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface Patch {
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

        T targetMixinType(String... annotationDescs);

        T targetAnnotationValues(Predicate<AnnotationHandle> values);

        T modifyTargetClasses(Consumer<List<Type>> consumer);

        T modifyParams(Consumer<ModifyMethodParams.Builder> consumer);

        T modifyTarget(String... methods);

        T modifyTarget(ModifyInjectionTarget.Action action, String... methods);

        T modifyVariableIndex(int start, int offset);

        T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes);

        T extractMixin(String targetClass);

        T improveModifyVar();

        T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer);

        T transform(List<ClassTransform> classTransforms);

        T transform(ClassTransform transformer);

        T transform(MethodTransform transformer);

        T chain(Consumer<T> consumer);

        PatchInstance build();
    }

    interface ClassPatchBuilder extends Builder<ClassPatchBuilder> {
        ClassPatchBuilder targetMethod(String... targets);

        default ClassPatchBuilder targetInjectionPoint(String target) {
            return targetInjectionPoint(null, target);
        }

        ClassPatchBuilder targetInjectionPoint(String value, String target);

        default ClassPatchBuilder targetConstant(double doubleValue) {
            return targetMixinType(MixinConstants.MODIFY_CONST)
                .targetAnnotationValues(handle -> handle.getNested("constant")
                    .flatMap(cst -> cst.<Double>getValue("doubleValue")
                        .map(val -> val.get() == doubleValue))
                    .orElse(false));
        }

        default ClassPatchBuilder modifyInjectionPoint(String value, String target) {
            return modifyInjectionPoint(value, target, false);
        }

        ClassPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues);

        default ClassPatchBuilder modifyInjectionPoint(String target) {
            return modifyInjectionPoint(null, target);
        }
        
        ClassPatchBuilder redirectShadowMethod(String original, String target, BiConsumer<MethodInsnNode, InsnList> callFixer);

        ClassPatchBuilder divertRedirector(Consumer<InstructionAdapter> patcher);

        ClassPatchBuilder disable();
    }

    interface InterfacePatchBuilder extends Builder<InterfacePatchBuilder> {
        InterfacePatchBuilder targetField(String... targets);

        InterfacePatchBuilder modifyValue(String value);
    }
}
