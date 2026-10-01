package org.sinytra.adapter.patch.processor.extract;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.transform.param.TransformParameters;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

// TODO Cleanup extraction process + proxy/mirroring
public class ExtractMixinProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (recipe.clean().getTargetClass().equals(dirty.getTargetClass())) return TxResult.PASS;

        PatchResult result = apply(dirty.getTargetClass(), context, recipe);
        if (result == PatchResult.PASS && dirty.hasProperty(SpecialKeys.EXTRACT_TARGET)) {
            MethodInsnNode minsn = dirty.getProperty(SpecialKeys.EXTRACT_TARGET).orElseThrow();
            result = MirrorableExtractMixin.apply(context, recipe, dirty.getTargetClass(), minsn);
        }

        // Defer to ProxyExtractMixinSub
        if (result == PatchResult.PASS && dirty.hasProperty(SpecialKeys.EXTRACT_ORIGIN_PARAM)) {
            return ProxyExtractMixinSub.INSTANCE.process(context, dirty, recipe);
        }

        return result == PatchResult.PASS ? TxResult.FAIL : TxResult.SUCCESS;
    }

    // TODO Prioritize mirroring over extraction, remove locals recreation
    public static PatchResult apply(String targetClassName, MixinContext context, Recipe recipe) {
        // Sanity check
        boolean isStatic = context.isStatic();
        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();

        TargetPair cleanTarget = recipe.getCleanTarget();
        String owner = Optional.ofNullable(cleanTarget).map(t -> t.classNode().name).orElse(targetClassName);
        boolean isInherited = context.environment().inheritanceHandler(context.dirtyLookup()).isClassInherited(targetClassName, owner);
        Candidates candidates = findCandidates(classNode, methodNode);
        if (!candidates.canMove(classNode, isInherited)) return PatchResult.PASS;

        // The member may have moved to a STATIC method on the new target - a Fabric method mapped onto a
        // NeoForge static hook. Mixin rejects that combination outright and the error is fatal:
        //
        //   InvalidInjectionException: non-static callback method ... targets a static method which is not
        //   supported
        //
        // StaticAccessProcessor cannot catch this: it runs before extraction, when the callback still matches
        // its original target. So the modifier has to be synced here - but it has to be DECIDED here, before
        // anything is moved: a body that reads local 0 cannot be made static at all, and bailing out after the
        // move would leave the member in both the original and the generated class.
        MethodQualifier movedTarget = recipe.dirty().getTargetMethod();
        TargetPair movedPair = movedTarget == null ? null
            : context.methods().findOwnMethodPair(context.dirtyLookup(), movedTarget);
        boolean syncModifier = movedPair != null && MethodHelper.isStatic(movedPair.methodNode()) && !MethodHelper.isStatic(methodNode);
        if (syncModifier && readsReceiverSlot(methodNode)) {
            // A @Shadow field or method access compiles to "aload 0 + getfield/invokevirtual", and exactly that
            // shape is allowed through Candidates#canMove (a shadowed member does not count as "accessing the
            // class instance"). A static target has no receiver, so there is nothing local 0 could hold.
            // Report the mixin as unadapted instead: Patcher#stripFailedMixins then drops the handler and the
            // mod loads with the feature disabled, which is strictly better than emitting a class that no
            // longer passes verification.
            return PatchResult.PASS;
        }

        ClassNode targetClass = context.environment().dirtyClassLookup().getClass(targetClassName).orElse(null);
        // Get or generate new mixin class
        ClassNode generatedTarget = context.environment().classGenerator().getOrGenerateMixinClass(classNode, targetClassName, targetClass != null ? targetClass.superName : null);
        context.environment().refmapHolder().copyEntries(classNode.name, generatedTarget.name);

        // Add mixin methods from original to generated class
        for (MethodNode method : candidates.methods()) {
            generatedTarget.methods.add(method);
            updateOwnerRefereces(method, classNode, targetClassName);
            if (!isStatic && method.localVariables != null) {
                method.localVariables.stream().filter(l -> l.index == 0).findFirst().ifPresent(lvn -> lvn.desc = Type.getObjectType(generatedTarget.name).getDescriptor());
            }
        }

        candidates.handleUpdates().forEach(c -> c.accept(generatedTarget));

        if (syncModifier) {
            context.recordCtxAudit("Made extracted callback static to match static target %s", movedTarget.asDescriptor());
            methodNode.access |= Opcodes.ACC_STATIC;

            // The callback keeps its original body, and as an instance method that body reserved local 0 for the
            // receiver - its parameters sat at 1..n. Now that the receiver is gone everything shifts down by one,
            // exactly as StaticAccessProcessor does in the opposite direction ("Remove static"). Slot numbers are
            // fixed at compile time and do not depend on branches, exception handlers or variable lifetimes, so
            // the uniform shift is correct; the one shape it is NOT safe for - a body that reads local 0 - was
            // rejected before anything was moved. Without the shift Mixin sees a body whose locals no longer line
            // up and the game dies with "VerifyError: Bad local variable type" (locals[3] = top).
            if (methodNode.localVariables != null) {
                for (LocalVariableNode lvn : methodNode.localVariables) {
                    lvn.index--;
                }
                methodNode.localVariables.removeIf(lvn -> lvn.index < 0);
            }
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof VarInsnNode varInsn) {
                    varInsn.var--;
                } else if (insn instanceof IincInsnNode iincInsn) {
                    iincInsn.var--;
                }
            }
            methodNode.maxLocals = Math.max(0, methodNode.maxLocals - 1);
        }

        // Take care of captured locals
        // Dropping the receiver also invalidates the method's StackMapTable: a full_frame still describes local 0
        // and every slot after it. Connector only recomputes frames when the result is COMPUTE_FRAMES, so ask
        // for them here - the uniform shift above is exactly the kind of change that silently leaves stale
        // frames behind (measured: delta frames survive, an explicit full_frame fails verification).
        PatchResult result = syncModifier ? PatchResult.COMPUTE_FRAMES : PatchResult.PASS;
        boolean capturesLocals = recipe.clean().hasProperty(MixinKeys.LOCALS);
        if (capturesLocals) {
            result = result.or(recreateLocalVariables(methodNode, context, recipe, generatedTarget));
        }

        context.recordCtxAudit("Extract mixin to target %s", targetClassName);
        // Remove original method
        context.patchContext().postApply(() -> classNode.methods.removeAll(candidates.methods));
        return result.or(PatchResult.APPLY);
    }

    record Candidates(List<MethodNode> methods, List<Consumer<ClassNode>> handleUpdates) {
        public boolean canMove(ClassNode classNode, boolean isInherited) {
            List<Runnable> accessFixes = new ArrayList<>();
            for (MethodNode methodNode : this.methods) {
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name) && !isInheritedField(classNode, finsn, isInherited, accessFixes)
                        || insn instanceof MethodInsnNode minsn && minsn.owner.equals(classNode.name) && !isInheritedMethod(classNode, minsn, isInherited, accessFixes)
                    ) {
                        // We can't move methods that access their class instance
                        return false;
                    }
                }
            }
            accessFixes.forEach(Runnable::run);
            return true;
        }
    }

    private static Candidates findCandidates(ClassNode classNode, MethodNode methodNode) {
        List<MethodNode> methods = new ArrayList<>();
        List<Consumer<ClassNode>> handleUpdates = new ArrayList<>();
        methods.add(methodNode);
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length >= 3) {
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (indy.bsmArgs[i] instanceof Handle handle && handle.getOwner().equals(classNode.name) && handle.getName().startsWith(AdapterUtil.LAMBDA_PREFIX + methodNode.name)) {
                        final int finalI = i;
                        classNode.methods.stream()
                            .filter(m -> m.name.equals(handle.getName()) && m.desc.equals(handle.getDesc()))
                            .findFirst()
                            .ifPresent(m -> {
                                methods.add(m);
                                handleUpdates.add(t -> indy.bsmArgs[finalI] = new Handle(handle.getTag(), t.name, handle.getName(), handle.getDesc(), handle.isInterface()));
                            });
                    }
                }
            }
        }
        return new Candidates(methods, handleUpdates);
    }

    private static void updateOwnerRefereces(MethodNode methodNode, ClassNode originalClass, String targetClass) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(originalClass.name)) {
                minsn.owner = targetClass;
            } else if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(originalClass.name)) {
                finsn.owner = targetClass;
            }
        }
    }

    private static boolean isInheritedField(ClassNode cls, FieldInsnNode finsn, boolean isTargetInherited, List<Runnable> accessUpdates) {
        FieldNode field = cls.fields.stream()
            .filter(f -> f.name.equals(finsn.name))
            .findFirst()
            .orElse(null);
        if (field != null) {
            if (AdapterUtil.isShadowField(field)) {
                return true;
            }
            if (isTargetInherited) {
                accessUpdates.add(() -> field.access = fixAccess(field.access));
                return true;
            }
        }
        return false;
    }

    private static boolean isInheritedMethod(ClassNode cls, MethodInsnNode minsn, boolean isTargetInherited, List<Runnable> accessUpdates) {
        MethodNode method = cls.methods.stream()
            .filter(m -> m.name.equals(minsn.name) && m.desc.equals(minsn.desc))
            .findFirst()
            .orElse(null);
        if (method != null) {
            List<AnnotationNode> annotations = method.visibleAnnotations != null ? method.visibleAnnotations : List.of();
            if (AdapterUtil.hasAnnotation(annotations, MixinAnnotations.SHADOW)) {
                return true;
            }
            if (isTargetInherited) {
                accessUpdates.add(() -> method.access = fixAccess(method.access));
                return true;
            }
        }
        return false;
    }

    private static int fixAccess(int access) {
        int visibility = OpcodeUtil.getAccessVisibility(access);
        // Lower than protected
        if (visibility == Opcodes.ACC_PRIVATE || visibility == 0) {
            // Widen to protected
            // Add synthetic to avoid mixin complaining about non-private static members being present
            // Setting the access to public will prevent the member from being renamed
            return OpcodeUtil.setAccessVisibility(visibility, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC);
        }
        return access;
    }

    /**
     * Whether the body reads local 0, which is the receiver of an instance method.
     * <p>
     * javac never assigns to slot 0 of an instance method, so any reference at all means the body uses
     * {@code this}. A {@code @Shadow} field or method access compiles to exactly that shape ({@code aload 0}
     * followed by {@code getfield}/{@code invokevirtual}), and a lambda that captured {@code this} is reached
     * through the same {@code aload 0}. Checking only the handler (not the moved lambdas) is sufficient for the
     * same reason: a lambda that needs the receiver forces the handler to load it.
     */
    private static boolean readsReceiverSlot(MethodNode methodNode) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == 0) {
                return true;
            }
            if (insn instanceof IincInsnNode iincInsn && iincInsn.var == 0) {
                return true;
            }
        }
        return false;
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
    private static PatchResult recreateLocalVariables(MethodNode methodNode, MixinContext context, Recipe recipe, ClassNode extractClass) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(context, recipe);
        if (capturedLocals == null) {
            return PatchResult.PASS;
        }
        LocalVariableLookup table = capturedLocals.lvt();
        int paramLocalStart = capturedLocals.paramLocalStart();

        // Mixin requires capturing locals in their original order, so we must filter out unused ones 
        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(capturedLocals, methodNode);
        LocalVarAnalyzer.CapturedLocalsUsage usage = transform.getUsage(capturedLocals, context);
        Collection<Integer> used = transform.used();
        LocalVariableLookup targetTable = usage.targetTable();
        Int2ObjectMap<InsnList> varInsnLists = usage.varInsnLists();
        Int2IntMap usageCount = usage.usageCount();

        if (table.isEmpty() || targetTable == null || used.stream().anyMatch(ordinal -> targetTable.getByOrdinalOrNull(ordinal) == null)) {
            return PatchResult.PASS;
        }

        PatchResult result = transform.remover().apply(context);
        if (result == PatchResult.PASS) {
            return PatchResult.PASS;
        }

        // Create a copy of the method to call
        MethodNode copy = new MethodNode(Opcodes.ACC_PRIVATE | (capturedLocals.isStatic() ? Opcodes.ACC_STATIC : 0), "adapter$bridge$" + methodNode.name, methodNode.desc, null, null);
        methodNode.accept(copy);
        copy.visibleAnnotations = null;
        copy.invisibleAnnotations = null;
        methodNode.instructions = new InsnList();
        // Remove used locals from the original method, as we'll be providing them ourselves
        TransformParameters cleanupPatch = TransformParameters.builder()
            .chain(b -> IntStream.range(paramLocalStart, paramLocalStart + used.size()).boxed()
                .sorted(Collections.reverseOrder())
                .forEach(b::remove))
            .build();
        PatchResult cleanupResult = cleanupPatch.apply(context);
        if (cleanupResult == PatchResult.PASS) {
            return PatchResult.PASS;
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
        Map<Integer, Integer> newIndices = new HashMap<>();
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
                varInsnLists.remove((int) index);
                varInsnLists.forEach((varIndex, varInsns) -> {
                    for (AbstractInsnNode insn : varInsns) {
                        if (insn instanceof VarInsnNode varInsn && varInsn.var == index) {
                            varInsn.var = newIndex;
                        }
                    }
                });
                newIndices.put(index, newIndex);
            }
        });

        // Load parameters
        for (int i = 0; i < paramLocalStart + 1; i++) {
            Type type = Type.getType(table.getByIndex(i).desc);
            replacementInsns.add(AdapterUtil.loadType(type, i));
        }
        // Load recreated locals
        used.forEach(ordinal -> {
            LocalVariableNode node = targetTable.getByOrdinal(ordinal);
            InsnList insns = varInsnLists.get(node.index);
            if (insns != null) {
                replacementInsns.add(insns);
            } else {
                Type type = Type.getType(node.desc);
                int newIndex = newIndices.getOrDefault(node.index, -1);
                if (newIndex == -1) {
                    throw new IllegalArgumentException("Missing new index for var " + node.index);
                }
                replacementInsns.add(new VarInsnNode(OpcodeUtil.getLoadOpcode(type.getSort()), newIndex));
            }
        });
        // Call overloaded method
        replacementInsns.add(new MethodInsnNode(capturedLocals.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL, extractClass.name, copy.name, copy.desc));
        replacementInsns.add(new LabelNode());
        replacementInsns.add(new InsnNode(OpcodeUtil.getReturnOpcode(methodNode)));
        replacementInsns.add(end);
        methodNode.instructions = replacementInsns;

        extractClass.methods.add(copy);
        return PatchResult.COMPUTE_FRAMES;
    }
}


