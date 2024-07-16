package org.sinytra.adapter.patch.api;

import com.mojang.serialization.Codec;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.patch.ClassPatchInstance;
import org.sinytra.adapter.patch.InterfacePatchInstance;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;

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

    Result apply(ClassNode classNode, PatchEnvironment environment);

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

    interface Builder<T extends Builder<T>> extends MethodTransformBuilder<T> {
        T targetClass(String... targets);

        T targetMixinType(String... annotationDescs);

        T targetAnnotationValues(Predicate<AnnotationHandle> values);

        T modifyTargetClasses(Consumer<List<Type>> consumer);

        T transform(List<ClassTransform> classTransforms);

        T transform(ClassTransform transformer);

        PatchInstance build();
    }

    interface ClassPatchBuilder extends Builder<ClassPatchBuilder> {
        ClassPatchBuilder targetMethod(String... targets);

        default ClassPatchBuilder targetInjectionPoint(String target) {
            return targetInjectionPoint(null, target);
        }

        ClassPatchBuilder targetInjectionPoint(String value, String target);

        default ClassPatchBuilder targetConstant(double doubleValue) {
            return targetAnnotationValues(handle -> handle.getNested("constant")
                .flatMap(cst -> cst.<Double>getValue("doubleValue")
                    .map(val -> val.get() == doubleValue))
                .orElseGet(() -> handle.getNested("at")
                    .flatMap(at -> at.<String>getValue("value").map(s -> s.get().equals("CONSTANT") &&
                        at.<List<String>>getValue("args").map(AnnotationValueHandle::get).map(t -> t.size() == 1
                                && (t.getFirst().equals("doubleValue=" + doubleValue + "D") || t.getFirst().equals("doubleValue=" + doubleValue)))
                            .orElse(false)))
                    .orElse(false)));
        }

        default ClassPatchBuilder modifyInjectionPoint(String value, String target) {
            return modifyInjectionPoint(value, target, false);
        }

        ClassPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues);

        ClassPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues, boolean dontUpgrade);

        default ClassPatchBuilder modifyInjectionPoint(String target) {
            return modifyInjectionPoint(null, target);
        }

        ClassPatchBuilder redirectShadowMethod(String original, String target, BiConsumer<MethodInsnNode, InsnList> callFixer);

        ClassPatchBuilder divertRedirector(Consumer<InstructionAdapter> patcher);

        ClassPatchBuilder updateRedirectTarget(String originalTarget, String newTarget);

        ClassPatchBuilder disable();
    }

    interface InterfacePatchBuilder extends Builder<InterfacePatchBuilder> {
        InterfacePatchBuilder targetField(String... targets);

        InterfacePatchBuilder modifyValue(String value);
    }
}
