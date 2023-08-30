package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.*;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Locals;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DynamicLVTPatch implements MethodTransform {
    private static final Pattern METHOD_REF_PATTERN = Pattern.compile("^(?<owner>L.+;)(?<name>.+)(?<desc>\\(.*\\).+)$");
    private static final Type CI_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    private static final Type CIR_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
    private static final String LOCAL_ANN = "Lcom/llamalad7/mixinextras/sugar/Local;";

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Collection<String> getAcceptedAnnotations() {
        // We only support INJECT and MODIFY_EXPR_VAL mixins for now. Other injectors will be supported later
        return Set.of(Patch.INJECT, Patch.MODIFY_EXPR_VAL);
    }

    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        if (methodNode.invisibleParameterAnnotations != null) {
            // Find @Local annotations on method parameters
            Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);
            Map<AnnotationNode, Type> localAnnotations = new HashMap<>();
            for (int i = 0; i < methodNode.invisibleParameterAnnotations.length; i++) {
                List<AnnotationNode> parameterAnnotations = methodNode.invisibleParameterAnnotations[i];
                if (parameterAnnotations != null) {
                    for (AnnotationNode paramAnn : parameterAnnotations) {
                        if (LOCAL_ANN.equals(paramAnn.desc)) {
                            Type type = paramTypes[i];
                            localAnnotations.put(paramAnn, type);
                        }
                    }
                }
            }
            if (localAnnotations.isEmpty()) {
                return Result.PASS;
            }
            Pair<ClassNode, MethodNode> target = findTargetMethod(classNode, annotationValues, context);
            if (target == null) {
                return Result.PASS;
            }
            ClassNode targetClass = target.getFirst();
            MethodNode targetMethod = target.getSecond();
            List<Type> locals = getMethodLocals(classNode, methodNode, targetClass, targetMethod, annotation, annotationValues, context, 0);
            if (locals == null) {
                return Result.PASS;
            }
            boolean applied = false;
            for (Map.Entry<AnnotationNode, Type> entry : localAnnotations.entrySet()) {
                AnnotationNode localAnn = entry.getKey();
                Type expectedType = entry.getValue();
                AnnotationValueHandle<Integer> handle = PatchInstance.<Integer>findAnnotationValue(localAnn.values, "index").orElse(null);
                if (handle != null) {
                    int index = handle.get();
                    // Check if local var matches expected value
                    if (index != -1 && index < locals.size() && !locals.get(index).equals(expectedType)) {
                        // Assume the lvt has new entries added before our variable. Find the nearest variable of the expected type
                        for (int i = index; i < locals.size(); i++) {
                            Type type = locals.get(i);
                            if (type.equals(expectedType)) {
                                LOGGER.info("Updating @Local annotation value in {}.{}{} from index {} to {}", classNode.name, methodNode.name, methodNode.desc, index, i);
                                handle.set(i);
                                applied = true;
                                break;
                            }
                        }
                    }
                }
            }
            return applied ? Result.APPLY : Result.PASS;
        }
        // Check if the mixin captures LVT
        if (Patch.INJECT.equals(annotation.desc) && PatchInstance.findAnnotationValue(annotation.values, "locals").isEmpty()) {
            ParametersDiff diff = compareParameters(classNode, methodNode, annotation, annotationValues, context);
            if (diff != null) {
                // Apply parameter patch
                ModifyMethodParams paramTransform = ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.METHOD);
                return paramTransform.apply(classNode, methodNode, annotation, annotationValues, context);
            }
        }
        return Result.PASS;
    }

    private Pair<ClassNode, MethodNode> findTargetMethod(ClassNode classNode, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        // Get method targets
        List<String> methodRefs = ((AnnotationValueHandle<List<String>>) annotationValues.get("method")).get();
        if (methodRefs.size() > 1) {
            // We only support single method targets for now
            return null;
        }
        // Resolve method reference
        String reference = context.getEnvironment().remap(classNode.name, methodRefs.get(0));
        // Extract owner, name and desc using regex
        Matcher matcher = METHOD_REF_PATTERN.matcher(reference);
        if (!matcher.matches()) {
            LOGGER.debug("Not a valid method reference: {}", reference);
            return null;
        }
        String owner = matcher.group("owner");
        String name = matcher.group("name");
        String desc = matcher.group("desc");
        // Find target class
        // We use mixin's bytecode provider rather than our own interface because it's used by InjectionPoint#find, which is called below,
        // and we'd have to provide it regardless of having our own.
        ClassNode targetClass;
        try {
            targetClass = MixinService.getService().getBytecodeProvider().getClassNode(Type.getType(owner).getInternalName());
        } catch (Throwable t) {
            LOGGER.debug("Target class not found", t);
            return null;
        }
        // Find target method in class
        MethodNode targetMethod = targetClass.methods.stream().filter(mtd -> mtd.name.equals(name) && mtd.desc.equals(desc)).findFirst().orElse(null);
        if (targetMethod == null) {
            LOGGER.debug("Target method not found: {}.{}{}", owner, name, desc);
            return null;
        }
        return Pair.of(targetClass, targetMethod);
    }

    private synchronized List<Type> getMethodLocals(ClassNode classNode, MethodNode methodNode, ClassNode targetClass, MethodNode targetMethod, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context, int startPos) {
        // TODO Provide via method context parameter
        AnnotationNode atNode = PatchInstance.findAnnotationValue(annotation.values, "at")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .orElse(null);
        if (atNode == null) {
            LOGGER.debug("Target @At annotation not found in method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
            return null;
        }
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = new ClassMixinContext(classNode.name, context.getClassNode().name, context.getEnvironment());
        // Parse injection point
        InjectionPoint injectionPoint = InjectionPoint.parse(mixinContext, methodNode, annotation, atNode);
        // Find target instructions
        List<AbstractInsnNode> insns = new ArrayList<>();
        injectionPoint.find(targetMethod.desc, targetMethod.instructions, insns);
        if (insns.isEmpty()) {
            LOGGER.debug("Skipping LVT patch, no target instructions found");
            return null;
        }
        if (insns.size() > 1) {
            LOGGER.debug("Skipping LVT patch due to multiple target instructions: {}", insns.size());
            return null;
        }
        // Get available local variables at the injection point in the target method
        LocalVariableNode[] localVariables;
        // Synchronize to avoid issues in mixin. This is necessary.
        synchronized (this) {
            localVariables = Locals.getLocalsAt(targetClass, targetMethod, insns.get(0), Locals.Settings.DEFAULT);
        }
        Type[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> Type.getType(lv.desc))
            .toArray(Type[]::new);
        return summariseLocals(locals, startPos);
    }

    private ParametersDiff compareParameters(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        // Sanity check to make sure the injector method takes in a CI or CIR argument
        if (Stream.of(params).noneMatch(p -> p.equals(CI_TYPE) || p.equals(CIR_TYPE))) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", annotation.desc);
            return null;
        }
        Pair<ClassNode, MethodNode> target = findTargetMethod(classNode, annotationValues, context);
        if (target == null) {
            return null;
        }
        ClassNode targetClass = target.getFirst();
        MethodNode targetMethod = target.getSecond();
        Type[] targetParams = Type.getArgumentTypes(targetMethod.desc);
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
        // The starting LVT index is of the first var after all method parameters. Offset by 1 for instance methods to skip 'this'
        int targetLocalPos = targetParams.length + (isStatic ? 0 : 1);
        // The first local var in the method's params comes after the target's params plus the CI/CIR parameter
        int paramLocalPos = targetParams.length + 1;
        // Get expected local variables from method parameters
        List<Type> expected = summariseLocals(params, paramLocalPos);
        // Get available local variables at the injection point in the target method
        List<Type> available = getMethodLocals(classNode, methodNode, targetClass, targetMethod, annotation, annotationValues, context, targetLocalPos);
        if (available == null) {
            return null;
        }
        // Compare expected and available params
        ParametersDiff diff = ParametersDiff.compareTypeParameters(expected.toArray(Type[]::new), available.toArray(Type[]::new));
        if (diff.isEmpty()) {
            // No changes required
            return null;
        }
        // Replacements are not supported, as they would require LVT fixups and converters
        if (!diff.replacements().isEmpty()) {
            LOGGER.debug("Tried to replace local variables in mixin method {}.{} using {}", classNode.name, methodNode.name + methodNode.desc, diff.replacements());
        }
        // Find max local index
        int maxLocal = 0;
        for (int i = 0; i < expected.size() && maxLocal < available.size(); maxLocal++) {
            if (!expected.get(i).equals(available.get(maxLocal))) {
                continue;
            }
            i++;
        }
        final int finalMaxLocal = maxLocal;
        // Offset the insertion to the correct parameter indices
        // Also remove any appended variables
        List<Pair<Integer, Type>> offsetInsertions = diff.insertions().stream().filter(pair -> pair.getFirst() < finalMaxLocal).map(pair -> pair.mapFirst(i -> i + paramLocalPos)).toList();
        ParametersDiff offsetDiff = new ParametersDiff(diff.originalCount(), offsetInsertions, List.of(), List.of());
        if (offsetDiff.isEmpty()) {
            // No changes required
            return null;
        }
        return offsetDiff;
    }

    // Adapted from org.spongepowered.asm.mixin.injection.callback.CallbackInjector summariseLocals
    private static List<Type> summariseLocals(Type[] locals, int pos) {
        List<Type> list = new ArrayList<>();
        if (locals != null) {
            for (int i = pos; i < locals.length; i++) {
                if (locals[i] != null) {
                    list.add(locals[i]);
                }
            }
        }
        return list;
    }

    private record ReferenceRemapper(PatchEnvironment env) implements IReferenceMapper {
        @Override
        public String remapWithContext(String context, String className, String reference) {
            return this.env.remap(className, reference);
        }

        //@formatter:off
        @Override public boolean isDefault() {return false;}
        @Override public String getResourceName() {return null;}
        @Override public String getStatus() {return null;}
        @Override public String getContext() {return null;}
        @Override public void setContext(String context) {}
        @Override public String remap(String className, String reference) {return remapWithContext(null, className, reference);}
        //@formatter:on
    }

    public static final class ClassMixinContext implements IMixinContext {
        private final String className;
        private final String targetClass;
        private final ReferenceRemapper referenceRemapper;

        public ClassMixinContext(String className, String targetClass, PatchEnvironment env) {
            this.className = className;
            this.targetClass = targetClass;
            this.referenceRemapper = new ReferenceRemapper(env);
        }

        @Override
        public IMixinInfo getMixin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Extensions getExtensions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getClassName() {
            return this.className.replace('/', '.');
        }

        @Override
        public String getClassRef() {
            return this.className;
        }

        @Override
        public String getTargetClassRef() {
            return this.targetClass;
        }

        @Override
        public IReferenceMapper getReferenceMapper() {
            return this.referenceRemapper;
        }

        @Override
        public boolean getOption(MixinEnvironment.Option option) {
            return false;
        }

        @Override
        public int getPriority() {
            return 0;
        }
    }
}
