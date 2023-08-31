package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record RedirectShadowMethod(MethodQualifier original, MethodQualifier replacement,
                                   BiConsumer<MethodInsnNode, InsnList> callFixer) implements ClassTransform {
    private static final String SHADOW_ANN = "Lorg/spongepowered/reloc/asm/mixin/Shadow;";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RedirectShadowMethod(String original, String replacement, BiConsumer<MethodInsnNode, InsnList> callFixer) {
        this(MethodQualifier.create(original).orElseThrow(), MethodQualifier.create(replacement).orElseThrow(), callFixer);
    }

    @Override
    public Patch.Result apply(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            MethodQualifier qualifier = new MethodQualifier(method.name, method.desc);
            if (this.original.equals(qualifier) && method.visibleAnnotations != null) {
                for (AnnotationNode methodAnn : method.visibleAnnotations) {
                    if (SHADOW_ANN.equals(methodAnn.desc)) {
                        LOGGER.info(MIXINPATCH, "Redirecting shadow method {}.{} to {}{}", classNode.name, method.name, this.replacement.name(), this.replacement.desc());
                        method.name = this.replacement.name();
                        method.desc = this.replacement.desc();
                        patchMethodCalls(classNode);
                        return Patch.Result.APPLY;
                    }
                }
            }
        }
        return Patch.Result.PASS;
    }

    private void patchMethodCalls(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(classNode.name) && minsn.name.equals(this.original.name()) && minsn.desc.equals(this.original.desc())) {
                    minsn.name = this.replacement.name();
                    minsn.desc = this.replacement.desc();
                    this.callFixer.accept(minsn, method.instructions);
                }
            }
        }
    }
}
