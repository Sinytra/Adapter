package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.LocalVariableLookup;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import dev.su5ed.sinytra.adapter.patch.util.OpcodeUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public record ExtractMixin(String targetClass) implements MethodTransform {
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.WRAP_OPERATION, MixinConstants.MODIFY_CONST, MixinConstants.MODIFY_ARG, MixinConstants.INJECT, MixinConstants.REDIRECT, MixinConstants.MODIFY_VAR, MixinConstants.MODIFY_EXPR_VAL);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        // Sanity check
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        MethodQualifier qualifier = methodContext.getTargetMethodQualifier(context);
        if (qualifier == null) {
            return Patch.Result.PASS;
        }
        String owner = Objects.requireNonNullElse(qualifier.internalOwnerName(), this.targetClass);
        boolean isInherited = context.environment().inheritanceHandler().isClassInherited(this.targetClass, owner);
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name) && !isInheritedField(classNode, finsn, isInherited)
                || insn instanceof MethodInsnNode minsn && minsn.owner.equals(classNode.name) && !isInheritedMethod(classNode, minsn, isInherited)
            ) {
                // We can't move methods that access their class instance
                return Patch.Result.PASS;
            }
        }
        ClassNode targetClass = AdapterUtil.getClassNode(this.targetClass);
        // Get or generate new mixin class
        ClassNode generatedTarget = context.environment().classGenerator().getOrGenerateMixinClass(classNode, this.targetClass, targetClass != null ? targetClass.superName : null);
        context.environment().refmapHolder().copyEntries(classNode.name, generatedTarget.name);
        // Add mixin method from original to generated class
        generatedTarget.methods.add(methodNode);

        // Take care of captured locals
        Patch.Result result = Patch.Result.PASS;
        if (methodContext.methodAnnotation().getValue("locals").isPresent()) {
            result = result.or(recreateLocalVariables(classNode, methodNode, methodContext, context, generatedTarget));
        }

        // Remove original method
        context.postApply(() -> classNode.methods.remove(methodNode));
        if (!isStatic && methodNode.localVariables != null) {
            methodNode.localVariables.stream().filter(l -> l.index == 0).findFirst().ifPresent(lvn -> {
                lvn.desc = Type.getObjectType(generatedTarget.name).getDescriptor();
            });
        }
        return result.or(Patch.Result.APPLY);
    }

    private static boolean isInheritedField(ClassNode cls, FieldInsnNode finsn, boolean isTargetInherited) {
        FieldNode field = cls.fields.stream()
            .filter(f -> f.name.equals(finsn.name))
            .findFirst()
            .orElse(null);
        if (field != null) {
            if (AdapterUtil.isShadowField(field)) {
                return true;
            }
            if (isTargetInherited && finsn.getOpcode() != Opcodes.GETSTATIC) {
                field.access = fixAccess(field.access);
                return true;
            }
        }
        return false;
    }

    private static boolean isInheritedMethod(ClassNode cls, MethodInsnNode minsn, boolean isTargetInherited) {
        MethodNode method = cls.methods.stream()
            .filter(m -> m.name.equals(minsn.name) && m.desc.equals(minsn.desc))
            .findFirst()
            .orElse(null);
        if (method != null) {
            List<AnnotationNode> annotations = method.visibleAnnotations != null ? method.visibleAnnotations : List.of();
            if (AdapterUtil.hasAnnotation(annotations, MixinConstants.SHADOW)) {
                return true;
            }
            if (isTargetInherited && minsn.getOpcode() != Opcodes.INVOKESTATIC) {
                method.access = fixAccess(method.access);
                return true;
            }
        }
        return false;
    }

    private static int fixAccess(int access) {
        int visibility = access & 0x7;
        // Lower than protected
        if (visibility == Opcodes.ACC_PRIVATE || visibility == 0) {
            // Widen to protected
            return access & ~0x7 | Opcodes.ACC_PROTECTED;
        }
        return visibility;
    }

    /**
     * We can't bring captured locals along when extracting a mixin, so our best bet is to recreate them from scratch.
     * <p/>
     * Consider the following mixin injector into Gui#renderPlayerHealth, capturing 15 local variables of which only 2 are used.
     * <pre>{@code @Inject(
     *     method = "renderPlayerHealth",
     *     at = @At(
     *         value = "INVOKE",
     *         target = "someInvocation",
     *     ),
     *     locals = LocalCapture.CAPTURE_FAILEXCEPTION
     * )
     * private void renderArmor(GuiGraphics context, CallbackInfo info, Player player, int i, boolean bl, long l, int j, FoodData foodData, int k, int left, int n, int o, float f, int p, int q, int r, int top) {
     *     return drawArmor(context, left, top);
     * }
     * }</pre>
     * <p/>
     * We first filter out unused captured locals.
     * <pre>{@code @Inject(
     *     method = "renderPlayerHealth",
     *     at = @At(
     *         value = "INVOKE",
     *         target = "someInvocation",
     *     ),
     *     locals = LocalCapture.CAPTURE_FAILEXCEPTION
     * )
     * private void renderArmor(GuiGraphics context, CallbackInfo info, int left, int top) {
     *     return drawArmor(context, left, top);
     * }
     * }</pre>
     * <p/>
     * Next, we create a prefixed copy of the method for overloading.
     * The remaining captured locals are also removed from the original, as we provide them ourselves.
     * <pre>{@code @Inject(
     *     method = "renderPlayerHealth",
     *     at = @At(
     *         value = "INVOKE",
     *         target = "someInvocation",
     *     )
     * )
     * private void renderArmor(GuiGraphics context, CallbackInfo info) {
     * 
     * }
     * 
     * private void adapter$bridge$renderArmor(GuiGraphics context, CallbackInfo info, int left, int top) {
     *     return drawArmor(context, left, top);
     * }}</pre>
     * <p/>
     * Finally, we load the local variable initializer instructions and call the overloaded method.
     * Local variables that are referenced multiple times in the original target's code will be stored as local variables
     * in the injector for reuse.
     * <pre>{@code @Inject(
     *     method = "renderPlayerHealth",
     *     at = @At(
     *         value = "INVOKE",
     *         target = "someInvocation",
     *     )
     * )
     * private void renderArmor(GuiGraphics context, CallbackInfo info) {
     *     int shared = context.someMethod();
     *     return adapter$bridge$renderArmor(context, shared.getLeft(), shared.getTop());
     * }
     * 
     * private void adapter$bridge$renderArmor(GuiGraphics context, CallbackInfo info, int left, int top) {
     *     return drawArmor(context, left, top);
     * }
     * }</pre>
     */
    private static Patch.Result recreateLocalVariables(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, ClassNode extractClass) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(methodNode, methodContext);
        if (capturedLocals == null) {
            return Patch.Result.PASS;
        }
        int paramLocalStart = capturedLocals.paramLocalStart();

        // Mixin requires capturing locals in their original order, so we must filter out unused ones 
        // Find used captured locals
        LocalVariableLookup table = capturedLocals.lvt();
        List<Integer> used = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn) {
                LocalVariableNode node = table.getByIndex(varInsn.var);
                int ordinal = table.getOrdinal(node);
                if (ordinal >= paramLocalStart && ordinal <= capturedLocals.paramLocalEnd()) {
                    used.add(ordinal - 1); // Subtract 1, which represents the CI param
                }
            }
        }
        // Find instructions used to initialize each used variable in the target method
        LocalVariableLookup targetTable = new LocalVariableLookup(capturedLocals.target().methodNode().localVariables);
        Int2ObjectMap<InsnList> varInsnLists = new Int2ObjectOpenHashMap<>();
        Int2IntMap usageCount = new Int2IntOpenHashMap();
        used.forEach(ordinal -> {
            int index = targetTable.getByOrdinal(ordinal).index;
            findVariableInitializerInsns(capturedLocals.target().methodNode(), capturedLocals.isStatic(), index, varInsnLists, usageCount);
        });
        // Remove unused captured locals
        ModifyMethodParams patch = ModifyMethodParams.builder()
            .chain(b -> IntStream.range(paramLocalStart, capturedLocals.paramLocalEnd())
                .filter(i -> !used.contains(i))
                .forEach(b::remove))
            .build();
        Patch.Result result = patch.apply(classNode, methodNode, methodContext, context);
        if (result == Patch.Result.PASS) {
            return Patch.Result.PASS;
        }

        // Create a copy of the method to call
        MethodNode copy = new MethodNode(Opcodes.ACC_PRIVATE | (capturedLocals.isStatic() ? Opcodes.ACC_STATIC : 0), "adapter$bridge$" + methodNode.name, methodNode.desc, null, null);
        methodNode.accept(copy);
        copy.visibleAnnotations = null;
        copy.invisibleAnnotations = null;
        methodNode.instructions = new InsnList();
        // Remove used locals from the original method, as we'll be providing them ourselves
        ModifyMethodParams.Builder cleanupBuilder = ModifyMethodParams.builder();
        IntStream.range(paramLocalStart, paramLocalStart + used.size()).forEach(cleanupBuilder::remove);
        ModifyMethodParams cleanupPatch = cleanupBuilder.build();
        Patch.Result cleanupResult = cleanupPatch.apply(classNode, methodNode, methodContext, context);
        if (cleanupResult == Patch.Result.PASS) {
            return Patch.Result.PASS;
        }

        // Save reused variables. Locals that are only referenced once will be inlined instead
        InsnList replacementInsns = new InsnList();
        Type lastVar = Type.getType(table.getLast().desc);
        AtomicInteger nextAvailableIndex = new AtomicInteger(methodNode.localVariables.size() - 1 + AdapterUtil.getLVTOffsetForType(lastVar));
        usageCount.forEach((index, count) -> {
            if (count == 1) {
                int usages = 0;
                for (Int2ObjectMap.Entry<InsnList> entry : varInsnLists.int2ObjectEntrySet()) {
                    for (AbstractInsnNode insn : entry.getValue()) {
                        if (insn instanceof VarInsnNode varInsn && varInsn.var == index) {
                            InsnList varInitializers = varInsnLists.get(varInsn.var);
                            entry.getValue().insert(varInsn, varInitializers);
                            entry.getValue().remove(varInsn);
                            usages++;
                        }
                    }
                }
                if (usages > 1) {
                    throw new IllegalStateException("Expected only one reference to variable " + index);
                }
            }
        });
        LabelNode end = new LabelNode();
        usageCount.forEach((index, count) -> {
            if (count > 1) {
                LocalVariableNode node = targetTable.getByIndex(index);
                Type type = Type.getType(node.desc);
                int newIndex = nextAvailableIndex.getAndAdd(AdapterUtil.getLVTOffsetForType(type));
                LabelNode start = new LabelNode();
                methodNode.localVariables.add(new LocalVariableNode(node.name, node.desc, node.signature, start, end, newIndex));
                InsnList insns = varInsnLists.get((int) index);
                replacementInsns.add(insns);
                replacementInsns.add(new VarInsnNode(OpcodeUtil.getStoreOpcode(type.getSort()), newIndex));
                replacementInsns.add(start);
                // Update initializers to point to the new index instead
                varInsnLists.remove(index);
                varInsnLists.forEach((varIndex, varInsns) -> {
                    for (AbstractInsnNode insn : varInsns) {
                        if (insn instanceof VarInsnNode varInsn && varInsn.var == index) {
                            varInsn.var = newIndex;
                        }
                    }
                });
            }
        });

        // Load parameters
        for (int i = 0; i < paramLocalStart + 1; i++) {
            Type type = Type.getType(table.getByIndex(i).desc);
            int opcode = OpcodeUtil.getLoadOpcode(type.getSort());
            replacementInsns.add(new VarInsnNode(opcode, i));
        }
        // Load recreated locals
        used.forEach(ordinal -> {
            int index = targetTable.getByOrdinal(ordinal).index;
            replacementInsns.add(varInsnLists.get(index)); 
        });
        // Call overloaded method
        replacementInsns.add(new MethodInsnNode(capturedLocals.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL, extractClass.name, copy.name, copy.desc));
        replacementInsns.add(new LabelNode());
        replacementInsns.add(new InsnNode(OpcodeUtil.getReturnOpcode(methodNode)));
        replacementInsns.add(end);
        methodNode.instructions = replacementInsns;

        extractClass.methods.add(copy);
        return Patch.Result.COMPUTE_FRAMES;
    }

    private static void findVariableInitializerInsns(MethodNode methodNode, boolean isStatic, int index, Int2ObjectMap<InsnList> varInsnLists, Int2IntMap usageCount) {
        InsnList insns = new InsnList();
        outer:
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == index && OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
                for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                    if (prev instanceof LabelNode) {
                        break outer;
                    }
                    if (prev instanceof FrameNode || prev instanceof LineNumberNode) {
                        continue;
                    }
                    if (prev instanceof VarInsnNode vInsn && (isStatic || vInsn.var != 0) && OpcodeUtil.isLoadOpcode(vInsn.getOpcode())) {
                        // TODO Handle method params
                        if (!varInsnLists.containsKey(vInsn.var)) {
                            findVariableInitializerInsns(methodNode, isStatic, vInsn.var, varInsnLists, usageCount);
                        }
                        usageCount.compute(vInsn.var, (key, existing) -> existing == null ? 1 : existing + 1);
                    }
                    insns.insert(prev.clone(Map.of()));
                }
            }
        }
        varInsnLists.put(index, insns);
    }
}
