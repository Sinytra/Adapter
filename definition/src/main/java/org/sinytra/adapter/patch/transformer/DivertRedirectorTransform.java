package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public record DivertRedirectorTransform(Consumer<InstructionAdapter> patcher) implements MethodTransform {

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.REDIRECT);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        String value = methodContext.injectionPointAnnotation().<String>getValue("value").map(AnnotationValueHandle::get).orElse(null);
        if ("INVOKE".equals(value)) {
            MethodQualifier target = methodContext.getInjectionPointMethodQualifier(context);
            if (target != null) {
                boolean applied = false;
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof MethodInsnNode minsn && target.matches(minsn)) {
                        for (AbstractInsnNode previous = insn.getPrevious(); previous != null; previous = previous.getPrevious()) {
                            if (previous instanceof LabelNode) {
                                MethodNode dummy = new MethodNode();
                                InstructionAdapter adapter = new InstructionAdapter(dummy);

                                LabelNode gotoTarget = new LabelNode();
                                dummy.instructions.add(gotoTarget);
                                this.patcher.accept(adapter);
                                methodNode.instructions.insert(minsn, dummy.instructions);
                                methodNode.instructions.insert(previous, new JumpInsnNode(Opcodes.GOTO, gotoTarget));
                                applied = true;
                                break;
                            }
                        }
                    }
                }
                if (applied) {
                    return Patch.Result.APPLY;
                }
            }
        }
        return Patch.Result.PASS;
    }
}
