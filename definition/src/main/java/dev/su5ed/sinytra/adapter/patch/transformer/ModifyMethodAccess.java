package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyMethodAccess(List<AccessChange> changes) implements MethodTransform {
    public static final Codec<ModifyMethodAccess> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        AccessChange.CODEC.listOf().fieldOf("changes").forGetter(ModifyMethodAccess::changes)
    ).apply(instance, ModifyMethodAccess::new));
    private static final Logger LOGGER = LogUtils.getLogger();

    public record AccessChange(boolean add, int modifier) {
        public static final Codec<AccessChange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("add").forGetter(AccessChange::add),
            Codec.INT.fieldOf("modifier").forGetter(AccessChange::modifier)
        ).apply(instance, AccessChange::new));
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        Result result = Result.PASS;
        for (AccessChange change : this.changes) {
            if (change.add) {
                if ((methodNode.access & change.modifier) == 0) {
                    LOGGER.info(MIXINPATCH, "Adding access modifier {} to method {}.{}{}", change.modifier, classNode.name, methodNode.name, methodNode.desc);
                    methodNode.access |= change.modifier;
                    result = Result.APPLY;
                }
            } else {
                if ((methodNode.access & change.modifier) != 0) {
                    LOGGER.info(MIXINPATCH, "Removing access modifier {} from method {}.{}{}", change.modifier, classNode.name, methodNode.name, methodNode.desc);
                    methodNode.access &= ~change.modifier;
                    if (change.modifier == Opcodes.ACC_STATIC) {
                        LocalVariableNode firstParam = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();
                        // Offset everything by 1
                        for (LocalVariableNode lvn : methodNode.localVariables) {
                            lvn.index++;
                        }
                        for (AbstractInsnNode insn : methodNode.instructions) {
                            if (insn instanceof VarInsnNode varInsn) {
                                varInsn.var++;
                            }
                        }
                        // Insert instance local variable
                        methodNode.localVariables.add(new LocalVariableNode("this", Type.getObjectType(classNode.name).getDescriptor(), null, firstParam.start, firstParam.end, 0));
                        result = Result.COMPUTE_FRAMES;
                    } else {
                        result = Result.APPLY;
                    }
                }
            }
        }
        return result;
    }
}
