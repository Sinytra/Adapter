package dev.su5ed.sinytra.adapter.patch.fixes;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.api.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

public class FieldTypeUsageTransformer implements ClassTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        boolean applied = false;
        if (bfu != null) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof FieldInsnNode finsn) {
                        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(finsn.owner, GlobalReferenceMapper.remapReference(finsn.name));
                        if (updatedTypes != null) {
                            FieldTypeFix typeAdapter = bfu.getFieldTypeAdapter(updatedTypes.getSecond(), updatedTypes.getFirst());
                            if (typeAdapter != null) {
                                LOGGER.info("Running fixup for field {}.{}{} in method {}{}", finsn.owner, finsn.name, finsn.desc, method.name, method.desc);
                                finsn.desc = updatedTypes.getSecond().getDescriptor();
                                typeAdapter.typePatch().apply(method.instructions, insn);
                                applied = true;
                            }
                        }
                    }
                }
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }
}
