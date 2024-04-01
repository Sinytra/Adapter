package org.sinytra.adapter.gradle;

import com.google.common.collect.*;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.gradle.analysis.AnalysisContext;
import org.sinytra.adapter.gradle.analysis.OverloadedMethods;
import org.sinytra.adapter.gradle.analysis.ReplacedMethodCalls;
import org.sinytra.adapter.gradle.util.MatchResult;
import org.sinytra.adapter.gradle.util.TraceCallback;
import org.sinytra.adapter.patch.LVTOffsets;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.InheritanceHandler;
import org.sinytra.adapter.patch.analysis.LocalVarRearrangement;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.params.ParametersDiff;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.ModifyInjectionTarget;
import org.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import org.sinytra.adapter.patch.transformer.SoftMethodParamsPatch;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.sinytra.adapter.patch.util.AdapterUtil.isAnonymousClass;

public class ClassAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClassAnalyzer");
    public static final String LAMBDA_PREFIX = "lambda$";

    private final ClassNode cleanNode;
    private final ClassNode dirtyNode;
    private final IMappingFile mappings;
    private final ClassLookup cleanClassProvider;
    private final ClassLookup dirtyClassProvider;
    private final InheritanceHandler inheritanceHandler;
    private final TraceCallback trace;

    // All method of each respective class node
    private final Multimap<String, MethodNode> cleanMethods;
    private final Multimap<String, MethodNode> dirtyMethods;
    // Methods that exist exclusively in one class and not the other
    private final Multimap<String, MethodNode> cleanOnlyMethods = HashMultimap.create();
    private final Multimap<String, MethodNode> dirtyOnlyMethods = HashMultimap.create();
    // Methods that exist in both classes, uses patched MethodNodes from the dirty class
    private final Multimap<String, MethodNode> dirtyCommonMethods = HashMultimap.create();
    // Clean class method to their dirty equivalents
    private final BiMap<MethodNode, MethodNode> originalCleanToDirty;
    private final BiMap<MethodNode, MethodNode> cleanToDirty = HashBiMap.create();

    private final Map<String, FieldNode> cleanFields;
    private final Map<String, FieldNode> dirtyFields;

    public static ClassAnalyzer create(byte[] cleanData, byte[] dirtyData, IMappingFile mappings, ClassLookup cleanClassProvider, ClassLookup dirtyClassProvider) {
        return new ClassAnalyzer(readClassNode(cleanData), readClassNode(dirtyData), mappings, cleanClassProvider, dirtyClassProvider);
    }

    private static ClassNode readClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    public ClassAnalyzer(ClassNode cleanNode, ClassNode dirtyNode, IMappingFile mappings, ClassLookup cleanClassProvider, ClassLookup dirtyClassProvider) {
        this.cleanNode = cleanNode;
        this.dirtyNode = dirtyNode;
        this.mappings = mappings;
        this.cleanClassProvider = cleanClassProvider;
        this.dirtyClassProvider = dirtyClassProvider;
        ClassLookup joinedClassProvider = name -> dirtyClassProvider.getClass(name).or(() -> cleanClassProvider.getClass(name));
        this.inheritanceHandler = new InheritanceHandler(joinedClassProvider);
        this.trace = new TraceCallback(LOGGER, this.cleanNode);

        this.cleanMethods = indexClassMethods(cleanNode);
        this.dirtyMethods = indexClassMethods(dirtyNode);
        Collection<MethodNode> allClean = this.cleanMethods.values();
        Collection<MethodNode> allDirty = this.dirtyMethods.values();
        this.cleanMethods.forEach((name, method) -> {
            final String cleanQualifier = method.name + method.desc;
            if (allDirty.stream().noneMatch(dirtyMethod -> {
                final String dirtyQualifier = dirtyMethod.name + dirtyMethod.desc;
                return cleanQualifier.equals(dirtyQualifier);
            })) {
                this.cleanOnlyMethods.put(name, method);
            }
        });
        this.dirtyMethods.forEach((name, method) -> {
            final String dirtyQualifier = method.name + method.desc;
            MethodNode cleanMethod = allClean.stream()
                .filter(clean -> {
                    final String cleanQualifier = clean.name + clean.desc;
                    return dirtyQualifier.equals(cleanQualifier);
                })
                .findFirst()
                .orElse(null);
            if (cleanMethod != null) {
                this.dirtyCommonMethods.put(name, method);
                this.cleanToDirty.put(cleanMethod, method);
            } else {
                this.dirtyOnlyMethods.put(name, method);
            }
        });
        this.cleanFields = new HashMap<>();
        for (FieldNode field : this.cleanNode.fields) {
            if (this.cleanFields.put(field.name, field) != null) {
                throw new RuntimeException("Found duplicate field " + field.name + " in class " + this.cleanNode.name);
            }
        }
        this.dirtyFields = new HashMap<>();
        for (FieldNode field : this.dirtyNode.fields) {
            if (this.dirtyFields.put(field.name, field) != null) {
                throw new RuntimeException("Found duplicate field " + field.name + " in class " + this.dirtyNode.name);
            }
        }
        this.originalCleanToDirty = ImmutableBiMap.copyOf(this.cleanToDirty);
    }

    public void analyze(List<? super Patch> patches, Multimap<ChangeCategory, String> info, Map<? super String, String> replacementCalls,
                        Map<String, Map<MethodQualifier, List<LVTOffsets.Swap>>> reorders
    ) {
        AnalysisContext context = new AnalysisContext(patches, this.dirtyNode, this.mappings, this.cleanToDirty, this.trace);
        // Try to find added dirtyMethod patches
        findOverloadedMethods(context, replacementCalls);
        if (!isAnonymousClass(this.cleanNode.name)) {
            findExpandedMethods(patches, replacementCalls);
            findExpandedLambdas(patches, replacementCalls);
        }
        ReplacedMethodCalls.findReplacedMethodCalls(context, this.dirtyNode, this.cleanToDirty);
        findUpdatedLambdaNames(patches);
        checkAccess(patches);
        calculateLVTOffsets(reorders);
        this.trace.space();

        Collection<String> removedFields = new HashSet<>();
        this.cleanFields.forEach((name, field) -> {
            FieldNode dirtyField = this.dirtyFields.get(name);
            if (dirtyField == null && !isAnonymousClass(this.dirtyNode.name)) {
                info.put(ChangeCategory.REMOVE_FIELD, "Removed field %s.%s %s".formatted(this.dirtyNode.name, name, field.desc));
                removedFields.add(name);
            }
        });
        this.dirtyFields.forEach((name, field) -> {
            FieldNode cleanField = this.cleanFields.get(name);
            if (cleanField == null && !isAnonymousClass(this.dirtyNode.name) && !removedFields.isEmpty()) {
                info.put(ChangeCategory.ADD_FIELD, "Added field %s.%s %s".formatted(this.dirtyNode.name, name, field.desc));
            } else if (cleanField != null && !field.desc.equals(cleanField.desc)) {
                info.put(ChangeCategory.MODIFY_FIELD, "Field %s.%s changed its type from %s to %s".formatted(this.dirtyNode.name, name, cleanField.desc, field.desc));
            }
        });
    }

    public void postAnalyze(List<? super PatchInstance> patches, Map<? extends String, String> replacementCalls) {
        this.trace.reset();
        updateReplacedInjectionPoints(patches, replacementCalls);
        this.trace.space();
    }

    private void calculateLVTOffsets(Map<String, Map<MethodQualifier, List<LVTOffsets.Swap>>> reorders) {
        this.originalCleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            if (cleanMethod.localVariables != null && dirtyMethod.localVariables != null && cleanMethod.localVariables.size() == dirtyMethod.localVariables.size()) {
                Int2IntMap swaps = LocalVarRearrangement.getRearrangedParametersFromLocals(cleanMethod.localVariables, dirtyMethod.localVariables);
                if (swaps != null) {
                    List<LVTOffsets.Swap> methodReorders = swaps.int2IntEntrySet().stream()
                        .map(entry -> new LVTOffsets.Swap(entry.getIntKey(), entry.getIntValue()))
                        .toList();
                    Map<MethodQualifier, List<LVTOffsets.Swap>> classReorders = reorders.computeIfAbsent(this.dirtyNode.name, s -> new HashMap<>());
                    MethodQualifier qualifier = new MethodQualifier(dirtyMethod.name, dirtyMethod.desc);
                    classReorders.put(qualifier, methodReorders);
                }
            }
        });
    }

    private void findUpdatedLambdaNames(List<? super PatchInstance> patches) {
        this.cleanToDirty.forEach((clean, dirty) -> {
            String dirtyMappedName = remapMethodName(this.dirtyNode, dirty.name, dirty.desc);
            if (!dirtyMappedName.startsWith(LAMBDA_PREFIX)) {
                List<String> cleanLambdas = findLambdasInMethod(this.cleanNode, clean, this.cleanMethods);
                List<MethodNode> dirtyLambdas = findLambdasInMethod(this.dirtyNode, dirty, this.dirtyMethods).stream()
                    .map(str -> findUniqueMethod(this.dirtyMethods, str))
                    .toList();

                Multimap<MethodNode, MethodNode> replacements = HashMultimap.create();
                List<String> cleanQualifiers = cleanLambdas.stream()
                    .map(str -> {
                        MethodNode method = findUniqueMethod(this.cleanMethods, str);
                        return method.name + method.desc;
                    })
                    .toList();
                for (MethodNode dirtyLambda : dirtyLambdas) {
                    if (!cleanQualifiers.contains(dirtyLambda.name + dirtyLambda.desc)) {
                        findLambdaReplacements(dirtyLambdas, dirtyLambda, cleanLambdas).forEach(m -> replacements.put(m, dirtyLambda));
                    }
                }
                replacements.asMap().forEach((original, replacementsMethods) -> {
                    if (replacementsMethods.size() == 1) {
                        MethodNode replacement = replacementsMethods.iterator().next();
                        this.trace.logHeader();
                        LOGGER.info("LAMBDA UPDATE");
                        LOGGER.info(" << {} {}", original.name, original.desc);
                        LOGGER.info(" >> {} {}", remapMethodName(this.dirtyNode, replacement.name, replacement.desc), replacement.desc);

                        PatchInstance patch = Patch.builder()
                            .targetClass(this.dirtyNode.name)
                            .targetMethod(original.name + original.desc)
                            .modifyTarget(ModifyInjectionTarget.Action.REPLACE, replacement.name + replacement.desc)
                            .build();
                        patches.add(patch);
                    }
                });
            }
        });
    }

    private List<MethodNode> findLambdaReplacements(List<? extends MethodNode> dirtyLambdas, MethodNode dirtyLambda, List<String> cleanLambdas) {
        List<MethodNode> replacements = new ArrayList<>();
        List<String> cleanDescs = new ArrayList<>();
        for (String cleanLambda : cleanLambdas) {
            MethodNode cleanMethod = findUniqueMethod(this.cleanMethods, cleanLambda);
            cleanDescs.add(cleanMethod.desc);
            Type dirtyReturn = Type.getReturnType(dirtyLambda.desc);
            Type cleanReturn = Type.getReturnType(cleanMethod.desc);
            if (dirtyReturn.equals(cleanReturn)) {
                Type[] dirtyParams = Type.getArgumentTypes(dirtyLambda.desc);
                Type[] cleanParams = Type.getArgumentTypes(cleanMethod.desc);
                if (dirtyParams.length == cleanParams.length && OverloadedMethods.checkParameters(cleanParams, dirtyParams) == MatchResult.FULL) {
                    replacements.add(cleanMethod);
                }
            }
        }
        if (replacements.size() > 1) {
            List<? extends MethodNode> dirtyDescs = dirtyLambdas.stream().filter(m -> m.desc.equals(dirtyLambda.desc)).toList();
            if (cleanDescs.stream().filter(s -> dirtyLambda.desc.equals(s)).count() == dirtyDescs.size()) {
                int index = dirtyDescs.indexOf(dirtyLambda);
                return List.of(replacements.get(index));
            }
        }
        return replacements;
    }

    private void updateReplacedInjectionPoints(List<? super PatchInstance> patches, Map<? extends String, String> replacementCalls) {
        Collection<String> seen = new HashSet<>();
        this.cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            for (AbstractInsnNode insn : dirtyMethod.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    String callQualifier = MethodCallAnalyzer.getCallQualifier(minsn);
                    String oldQualifier = replacementCalls.get(callQualifier);
                    if (oldQualifier != null && !seen.contains(oldQualifier)) {
                        // Check if it was called in the original method insns
                        for (AbstractInsnNode cInsn : cleanMethod.instructions) {
                            if (cInsn instanceof MethodInsnNode cminsn && oldQualifier.equals(MethodCallAnalyzer.getCallQualifier(cminsn))) {
                                this.trace.logHeader();
                                LOGGER.info("Replacing call in method {}", dirtyMethod.name + dirtyMethod.desc);
                                LOGGER.info(" << {}", oldQualifier);
                                LOGGER.info(" >> {}", callQualifier);

                                MethodNode cleanTargetMethod = this.cleanClassProvider.findMethod(cminsn.owner, cminsn.name, cminsn.desc).orElseThrow();
                                MethodNode dirtyTargetMethod = this.dirtyClassProvider.findMethod(minsn.owner, minsn.name, minsn.desc).orElseThrow();
                                ParametersDiff diff = ParametersDiff.compareMethodParameters(cleanTargetMethod, dirtyTargetMethod);

                                PatchInstance patch = Patch.builder()
                                    .targetClass(this.dirtyNode.name)
                                    .targetMethod(dirtyMethod.name + dirtyMethod.desc)
                                    .targetInjectionPoint(oldQualifier)
                                    // Avoid automatic method upgrades when a parameter transformation is being applied
                                    .modifyInjectionPoint(null, callQualifier, false, true)
                                    .transformMethods(diff.createTransforms(ParamTransformTarget.INJECTION_POINT))
                                    .build();
                                patches.add(patch);
                                seen.add(oldQualifier);
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    private void findOverloadedMethods(AnalysisContext context, Map<? super String, String> replacementCalls) {
        this.dirtyOnlyMethods.values().forEach(method -> {
            OverloadedMethods.MethodOverload overloader = OverloadedMethods.findOverloadMethod(context, this.dirtyNode.name, method, this.dirtyCommonMethods.values());
            if (overloader != null) {
                MethodNode overloaderMethod = overloader.methodNode();
                ParametersDiff diff = ParametersDiff.compareMethodParameters(overloaderMethod, method);
                if (!diff.insertions().isEmpty() || !diff.replacements().isEmpty()) {
                    String overloaderQualifier = overloaderMethod.name + overloaderMethod.desc;
                    String dirtyQualifier = method.name + method.desc;

                    if (overloader.isFullMatch()) {
                        this.trace.logHeader();
                        LOGGER.info("OVERLOAD");
                        LOGGER.info("   " + overloaderQualifier);
                        LOGGER.info("=> " + dirtyQualifier);
                        LOGGER.info("===");
                        PatchInstance patch = Patch.builder()
                            .targetClass(this.dirtyNode.name)
                            .targetMethod(overloaderQualifier)
                            .chain(b -> overloader.applyPatchTargetModifier(b, method))
                            .transformMethods(diff.createTransforms(ParamTransformTarget.METHOD))
                            .build();
                        context.addPatch(patch);
                        replacementCalls.put(Type.getObjectType(this.dirtyNode.name).getDescriptor() + dirtyQualifier, Type.getObjectType(this.cleanNode.name).getDescriptor() + overloaderQualifier);

                        this.cleanToDirty.put(this.cleanToDirty.inverse().get(overloaderMethod), method);
                    } else if (diff.insertions().isEmpty()) {
                        this.trace.logHeader();
                        LOGGER.info("SOFT OVERLOAD");
                        LOGGER.info("   " + overloaderQualifier);
                        LOGGER.info("=> " + dirtyQualifier);
                        LOGGER.info("===");
                        PatchInstance patch = Patch.builder()
                            .targetClass(this.dirtyNode.name)
                            .targetMethod(overloaderQualifier)
                            .transform(new SoftMethodParamsPatch(method.name + method.desc))
                            // IMPORTANT: Target modification must come AFTER soft params patch
                            .chain(b -> overloader.applyPatchTargetModifier(b, method))
                            .build();
                        context.addPatch(patch);
                    }
                }
            }
        });
    }

    private void checkAccess(List<? super PatchInstance> patches) {
        this.cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            String dirtyQualifier = dirtyMethod.name + dirtyMethod.desc;

            if ((cleanMethod.access & Opcodes.ACC_STATIC) != 0 && (dirtyMethod.access & Opcodes.ACC_STATIC) == 0) {
                this.trace.logHeader();
                LOGGER.info("UNSTATIC method {}", dirtyQualifier);

                PatchInstance patch = Patch.builder()
                    .targetClass(this.dirtyNode.name)
                    .targetMethod(dirtyQualifier)
                    .modifyMethodAccess(new ModifyMethodAccess.AccessChange(false, Opcodes.ACC_STATIC))
                    .build();
                patches.add(patch);
            } else if ((cleanMethod.access & Opcodes.ACC_STATIC) == 0 && (dirtyMethod.access & Opcodes.ACC_STATIC) != 0) {
                this.trace.logHeader();
                LOGGER.info("STATIC'd method {}", dirtyQualifier);
            }
        });
    }

    private void findExpandedMethods(List<? super PatchInstance> patches, Map<? super String, String> replacementCalls) {
        // Find "expanded" methods where forge replaces a dirtyMethod with one that takes in additional parameters
        this.cleanOnlyMethods.forEach((name, method) -> {
            // Skip lambdas for now
            String mappedClean = remapMethodName(this.cleanNode, method.name, method.desc);
            if (mappedClean.startsWith(LAMBDA_PREFIX)) {
                return;
            }

            this.dirtyOnlyMethods.forEach((dirtyName, dirtyMethod) -> {
                // Skip lambdas for now
                if (!dirtyMethod.name.startsWith(LAMBDA_PREFIX)) {
                    tryFindExpandedMethod(patches, replacementCalls, method, dirtyMethod);
                }
            });
        });
    }

    private void findExpandedLambdas(List<? super Patch> patches, Map<? super String, String> replacementCalls) {
        this.cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            // Find lambdas sorted by their call order. This increases our precision when looking for replaced lambdas that had their suffix number changed.
            List<String> cleanLambdas = findLambdasInMethod(this.cleanNode, cleanMethod, null);
            List<String> dirtyLambdas = findLambdasInMethod(this.dirtyNode, dirtyMethod, null);
            if (cleanLambdas.isEmpty() && !dirtyLambdas.isEmpty()) {
                findMovedInsnsToLambdas(patches, cleanMethod, dirtyMethod, dirtyLambdas);
                return;
            }
            for (int cleanIdx = 0, dirtyIdx = 0; cleanIdx < cleanLambdas.size() && dirtyIdx < dirtyLambdas.size(); ) {
                String cleanLambda = cleanLambdas.get(cleanIdx);
                String dirtyLambda = dirtyLambdas.get(cleanIdx);
                if (cleanLambda.equals(dirtyLambda)) {
                    cleanIdx++;
                    dirtyIdx++;
                } else {
                    boolean noDirty;
                    // Lambda removed in Forge, ignore
                    if (noDirty = !dirtyLambdas.contains(cleanLambda)) {
                        cleanIdx++;
                    }
                    // Lambda added by Forge, ignore
                    if (!cleanLambdas.contains(dirtyLambda)) {
                        dirtyIdx++;

                        // Lambda (likely) modified by Forge, proceed
                        if (noDirty) {
                            MethodNode cleanLambdaMethod = findUniqueMethod(this.cleanMethods, cleanLambda);
                            MethodNode dirtyLambdaMethod = findUniqueMethod(this.dirtyMethods, dirtyLambda);
                            tryFindExpandedMethod(patches, replacementCalls, cleanLambdaMethod, dirtyLambdaMethod);
                        }
                    } else {
                        cleanIdx++;
                        dirtyIdx++;
                    }
                }
            }
        });
    }

    private void findMovedInsnsToLambdas(List<? super Patch> patches, MethodNode cleanMethod, MethodNode dirtyMethod, List<String> lambdas) {
        for (String lambda : lambdas) {
            MethodNode lambdaMethod = this.dirtyOnlyMethods.get(lambda).iterator().next();
            Set<String> redirectInjectionPoints = new HashSet<>();
            for (AbstractInsnNode insn : lambdaMethod.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    String qualifier = MethodCallAnalyzer.getCallQualifier(minsn);
                    if (containsMethodCall(cleanMethod, minsn) && !containsMethodCall(dirtyMethod, minsn)) {
                        redirectInjectionPoints.add(qualifier);
                    }
                }
            }
            if (!redirectInjectionPoints.isEmpty()) {
                // TODO handle method desc changes or filter injectors
                Patch.ClassPatchBuilder patch = Patch.builder()
                    .targetClass(this.dirtyNode.name)
                    .targetMethod(dirtyMethod.name + dirtyMethod.desc)
                    .modifyTarget(lambdaMethod.name + lambdaMethod.desc);
                redirectInjectionPoints.forEach(patch::targetInjectionPoint);
                patches.add(patch.build());
            }
        }
    }

    private void tryFindExpandedMethod(List<? super PatchInstance> patches, Map<? super String, String> replacementCalls, MethodNode clean, MethodNode dirty) {
        // Skip methods with different return types
        if (!Type.getReturnType(clean.desc).equals(Type.getReturnType(dirty.desc))
            // Make an educated guess and assume all dirtyMethod replacements keep the same name.
            || !dirty.name.equals(remapMethodName(this.cleanNode, clean.name, clean.desc))
        ) {
            return;
        }

        Type[] parameterTypes = Type.getArgumentTypes(clean.desc);
        ParametersDiff diff = ParametersDiff.compareMethodParameters(clean, dirty);
        if (!diff.isEmpty()) {
            if (!diff.replacements().isEmpty()) {
                List<Pair<Integer, Type>> newReplacements = new ArrayList<>(diff.replacements());
                List<Pair<Integer, Integer>> swaps = new ArrayList<>();
                boolean valid = false;
                // Detect swapped parameters
                if (newReplacements.size() == 2) {
                    Pair<Integer, Type> first = newReplacements.get(0);
                    Pair<Integer, Type> second = newReplacements.get(1);
                    int distance = Math.abs(second.getFirst() - first.getFirst());
                    if (distance == 1 && parameterTypes[first.getFirst()].equals(second.getSecond()) && parameterTypes[second.getFirst()].equals(first.getSecond())) {
                        this.trace.logHeader();
                        LOGGER.info("Found swapped parameter types {} <-> {} in method {}", first.getSecond(), second.getSecond(), dirty.name);
                        newReplacements.clear();
                        swaps.add(Pair.of(first.getFirst(), second.getFirst()));
                        valid = true;
                    }
                }
                if (!valid) {
                    for (Pair<Integer, Type> replacement : diff.replacements()) {
                        Type original = parameterTypes[replacement.getFirst()];
                        Type substitute = replacement.getSecond();
                        if (original.getSort() == Type.OBJECT && substitute.getSort() == Type.OBJECT && this.inheritanceHandler.isClassInherited(substitute.getInternalName(), original.getInternalName())) {
                            this.trace.logHeader();
                            LOGGER.info("Found valid replacement {} -> {} in method {}", original.getInternalName(), substitute.getInternalName(), clean.name);
                            valid = true;
                            continue;
                        }
                        newReplacements.remove(replacement);
                        LOGGER.debug("Ignoring replacement {} -> {} in method {}", replacement.getFirst(), replacement.getSecond(), dirty.name);
                    }
                }
                if (valid) {
                    diff = new ParametersDiff(diff.originalCount(), diff.insertions(), newReplacements, swaps, List.of(), List.of());
                } else {
                    return;
                }
            }
            String cleanQualifier = clean.name + clean.desc;
            String dirtyQualifier = dirty.name + dirty.desc;
            this.trace.logHeader();
            LOGGER.info("REPLACE");
            LOGGER.info("   {}", cleanQualifier);
            LOGGER.info("\\> {}", dirtyQualifier);
            LOGGER.info("===");

            if (replacementCalls.put(Type.getObjectType(this.dirtyNode.name).getDescriptor() + dirtyQualifier, Type.getObjectType(this.cleanNode.name).getDescriptor() + cleanQualifier) != null) {
                throw new IllegalStateException("Duplicate replacement for %s.%s".formatted(this.cleanNode.name, cleanQualifier));
            }

            PatchInstance patch = Patch.builder()
                .targetClass(this.dirtyNode.name)
                .targetMethod(cleanQualifier)
                .modifyTarget(dirtyQualifier)
                .transformMethods(diff.createTransforms(ParamTransformTarget.METHOD))
                .build();
            patches.add(patch);
        }
    }

    private List<String> findLambdasInMethod(ClassNode cls, MethodNode method, @Nullable Multimap<String, MethodNode> methods) {
        List<String> list = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length >= 3) {
                for (Object bsmArg : indy.bsmArgs) {
                    if (bsmArg instanceof Handle handle && handle.getOwner().equals(cls.name)) {
                        String lambdaName = remapMethodName(cls, handle.getName(), handle.getDesc());
                        if (lambdaName.startsWith(LAMBDA_PREFIX)) {
                            String name = handle.getName();
                            list.add(name);
                            if (methods != null) {
                                MethodNode lambda = findUniqueMethod(methods, name);
                                list.addAll(findLambdasInMethod(cls, lambda, methods));
                            }
                            break;
                        }
                    }
                }
            }
        }
        return list;
    }

    private String remapMethodName(ClassNode cls, String name, String desc) {
        return Optional.ofNullable(this.mappings.getClass(cls.name))
            .map(c -> c.getMethod(name, desc))
            .map(IMappingFile.INode::getMapped)
            .orElse(name);
    }

    public static boolean containsMethodCall(MethodNode methodNode, MethodInsnNode targetMinsn) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(targetMinsn.owner) && minsn.name.equals(targetMinsn.name) && minsn.desc.equals(targetMinsn.desc)) {
                return true;
            }
        }
        return false;
    }

    private static MethodNode findUniqueMethod(Multimap<String, MethodNode> methods, String name) {
        Collection<MethodNode> values = methods.get(name);
        if (values != null && !values.isEmpty()) {
            if (values.size() > 1) {
                throw new IllegalStateException("Found multiple candidates for method " + name);
            }
            return values.iterator().next();
        }
        throw new NullPointerException("Method " + name + " not found");
    }

    private static Multimap<String, MethodNode> indexClassMethods(ClassNode classNode) {
        final Multimap<String, MethodNode> methods = HashMultimap.create();
        for (MethodNode method : classNode.methods) {
            methods.put(method.name, method);
        }
        return methods;
    }
}
