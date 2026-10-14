package org.sinytra.adapter.types;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;

public final class AccessorAdapterGenerator {
    private static final String PREFIX = "adapter$";
    private static final int PRIORITY_MAX = 9999;

    public static String getOrCreateGetter(BytecodeFixerJarGenerator generator, Type owner, String field, boolean isStatic, TypeAdapter adapter) {
        ClassNode mixin = generator.getOrCreateClass(owner.getInternalName().replace('/', '_'), name -> createMixin(name, owner));
        String name = PREFIX + field;
        String desc = Type.getMethodDescriptor(adapter.to());
        synchronized (mixin) {
            if (mixin.methods.stream().noneMatch(m -> m.name.equals(name))) {
                mixin.methods.add(createGetter(owner, field, isStatic, adapter, name, desc));
            }
        }
        return name + desc;
    }

    private static MethodNode createGetter(Type owner, String field, boolean isStatic, TypeAdapter adapter, String name, String desc) {
        MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
        getter.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MixinAnnotations.UNIQUE)));
        if (!isStatic) {
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        getter.instructions.add(new FieldInsnNode(isStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD, owner.getInternalName(), field, adapter.from().getDescriptor()));
        adapter.apply(getter.instructions, getter.instructions.getLast());
        getter.instructions.add(new InsnNode(OpcodeUtil.getReturnOpcode(adapter.to())));
        return getter;
    }

    private static ClassNode createMixin(String name, Type target) {
        ClassNode mixin = new ClassNode();
        mixin.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, TypeConstants.OBJECT_TYPE.getInternalName(), null);
        AnnotationNode annotation = new AnnotationNode(MixinAnnotations.MIXIN);
        annotation.visit("value", List.of(target));
        annotation.visit("priority", PRIORITY_MAX);
        mixin.invisibleAnnotations = new ArrayList<>(List.of(annotation));
        return mixin;
    }

    private AccessorAdapterGenerator() {
    }
}
