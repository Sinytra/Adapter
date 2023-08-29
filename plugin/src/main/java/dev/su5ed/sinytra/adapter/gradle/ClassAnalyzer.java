package dev.su5ed.sinytra.adapter.gradle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.gradle.provider.ClassProvider;
import dev.su5ed.sinytra.adapter.patch.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClassAnalyzer");
    private static final Collection<Integer> RETURN_OPCODES = Set.of(Opcodes.RETURN, Opcodes.ARETURN, Opcodes.DRETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN);
    private static final String LAMBDA_PREFIX = "lambda$";
    private static final Pattern LAMBDA_PATTERN = Pattern.compile("^lambda\\$(.+)\\$.+$");

    private final ClassNode cleanNode;
    private final ClassNode dirtyNode;
    private final IMappingFile mappings;
    private final ClassProvider cleanClassProvider;
    private final ClassProvider dirtyClassProvider;
    private final ClassProvider joinedClassProvider;
    private final InheritanceHandler inheritanceHandler;

    // All method of each respective class node
    private final Multimap<String, MethodNode> cleanMethods;
    private final Multimap<String, MethodNode> dirtyMethods;
    // Methods that exist exclusively in one class and not the other
    private final Multimap<String, MethodNode> cleanOnlyMethods = HashMultimap.create();
    private final Multimap<String, MethodNode> dirtyOnlyMethods = HashMultimap.create();
    // Methods that exist in both classes, uses patched MethodNodes from the dirty class
    private final Multimap<String, MethodNode> dirtyCommonMethods = HashMultimap.create();
    // Clean class method to their dirty equivalents
    private final Map<MethodNode, MethodNode> cleanToDirty = new HashMap<>();

    private final Map<String, FieldNode> cleanFields;
    private final Map<String, FieldNode> dirtyFields;

    private boolean loggedHeader = false;

    public static ClassAnalyzer create(byte[] cleanData, byte[] dirtyData, IMappingFile mappings, ClassProvider cleanClassProvider, ClassProvider dirtyClassProvider) {
        return new ClassAnalyzer(readClassNode(cleanData), readClassNode(dirtyData), mappings, cleanClassProvider, dirtyClassProvider);
    }

    private static ClassNode readClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    public ClassAnalyzer(ClassNode cleanNode, ClassNode dirtyNode, IMappingFile mappings, ClassProvider cleanClassProvider, ClassProvider dirtyClassProvider) {
        this.cleanNode = cleanNode;
        this.dirtyNode = dirtyNode;
        this.mappings = mappings;
        this.cleanClassProvider = cleanClassProvider;
        this.dirtyClassProvider = dirtyClassProvider;
        this.joinedClassProvider = name -> dirtyClassProvider.getClass(name).or(() -> cleanClassProvider.getClass(name));
        this.inheritanceHandler = new InheritanceHandler(this.joinedClassProvider);

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
    }

    private void logHeader() {
        if (!this.loggedHeader) {
            LOGGER.info("Class {}", this.cleanNode.name);
            this.loggedHeader = true;
        }
    }

    public void analyze(List<? super PatchInstance> patches, Multimap<ChangeCategory, String> info, Map<? super String, String> replacementCalls) {
        // Try to find added dirtyMethod patches
        findOverloadedMethods(patches, replacementCalls);
        if (!isAnonymousClass(this.cleanNode.name)) {
            findExpandedMethods(patches, replacementCalls);
            findExpandedLambdas(patches, replacementCalls);
        }
        findUpdatedLambdaNames(patches);
        checkAccess(patches);
        if (this.loggedHeader) {
            LOGGER.info("");
        }

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
        loggedHeader = false;
        updateReplacedInjectionPoints(patches, replacementCalls);
        if (this.loggedHeader) {
            LOGGER.info("");
        }
    }

    private void findUpdatedLambdaNames(List<? super PatchInstance> patches) {
        Multimap<MethodNode, MethodNode> replacements = HashMultimap.create();
        this.dirtyOnlyMethods.forEach((name, method) -> {
            String dirtyMappedName = remapMethodName(this.dirtyNode, method.name, method.desc);
            Matcher dirtyMatcher = LAMBDA_PATTERN.matcher(dirtyMappedName);
            if (dirtyMatcher.matches()) {
                // Find lambda in the same outer method, with the same descriptor but different number suffix
                String dirtyLambdaName = dirtyMatcher.group(1);
                this.cleanMethods.forEach((cleanName, cleanMethod) -> {
                    String cleanLambdaName = getLambdaMethodName(this.cleanNode, cleanMethod);
                    if (dirtyLambdaName.equals(cleanLambdaName)) {
                        Type dirtyReturn = Type.getReturnType(method.desc);
                        Type cleanReturn = Type.getReturnType(cleanMethod.desc);
                        if (dirtyReturn.equals(cleanReturn)) {
                            Type[] dirtyParams = Type.getArgumentTypes(method.desc);
                            Type[] cleanParams = Type.getArgumentTypes(cleanMethod.desc);
                            if (dirtyParams.length == cleanParams.length && checkParameters(cleanParams, dirtyParams, true)) {
                                replacements.put(cleanMethod, method);
                            }
                        }
                    }
                });
            }
        });
        replacements.asMap().forEach((original, replacementsMethods) -> {
            if (replacementsMethods.size() == 1) {
                MethodNode replacement = replacementsMethods.iterator().next();
                logHeader();
                LOGGER.info("LAMBDA UPDATE");
                LOGGER.info(" << {} {}", remapMethodName(this.cleanNode, original.name, original.desc), original.desc);
                LOGGER.info(" >> {} {}", remapMethodName(this.dirtyNode, replacement.name, replacement.desc), replacement.desc);

                PatchInstance patch = Patch.builder()
                    .targetClass(this.dirtyNode.name)
                    .targetMethod(original.name + original.desc)
                    .modifyTarget(replacement.name + replacement.desc)
                    .build();
                patches.add(patch);
            }
        });
    }

    @Nullable
    private String getLambdaMethodName(ClassNode cls, MethodNode method) {
        String mappedName = remapMethodName(cls, method.name, method.desc);
        Matcher matcher = LAMBDA_PATTERN.matcher(mappedName);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private void updateReplacedInjectionPoints(List<? super PatchInstance> patches, Map<? extends String, String> replacementCalls) {
        Collection<String> seen = new HashSet<>();
        this.cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            for (AbstractInsnNode insn : dirtyMethod.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    String callQualifier = getCallQualifier(minsn);
                    String oldQualifier = replacementCalls.get(callQualifier);
                    if (oldQualifier != null && !seen.contains(oldQualifier)) {
                        // Check if it was called in the original method insns
                        for (AbstractInsnNode cInsn : cleanMethod.instructions) {
                            if (cInsn instanceof MethodInsnNode cminsn && oldQualifier.equals(getCallQualifier(cminsn))) {
                                logHeader();
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
                                    .modifyInjectionPoint(callQualifier)
                                    .transform(ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.INJECTION_POINT))
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

    private String getCallQualifier(MethodInsnNode insn) {
        return Type.getObjectType(insn.owner).getDescriptor() + insn.name + insn.desc;
    }

    private void findOverloadedMethods(List<? super PatchInstance> patches, Map<? super String, String> replacementCalls) {
        this.dirtyOnlyMethods.values().forEach(method -> {
            MethodNode overloader = findOverloadMethod(this.dirtyNode.name, method, this.dirtyCommonMethods.values());
            if (overloader != null) {
                ParametersDiff diff = ParametersDiff.compareMethodParameters(overloader, method);
                if (!diff.insertions().isEmpty() || !diff.replacements().isEmpty()) {
                    String overloaderQualifier = overloader.name + overloader.desc;
                    String dirtyQualifier = method.name + method.desc;
                    logHeader();
                    LOGGER.info("OVERLOAD");
                    LOGGER.info("   " + overloaderQualifier);
                    LOGGER.info("=> " + dirtyQualifier);
                    LOGGER.info("===");

                    PatchInstance patch = Patch.builder()
                        .targetClass(this.dirtyNode.name)
                        .targetMethod(overloaderQualifier)
                        .modifyTarget(method.name + method.desc)
                        .transform(ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.METHOD))
                        .build();
                    patches.add(patch);
                    replacementCalls.put(Type.getObjectType(this.dirtyNode.name).getDescriptor() + dirtyQualifier, Type.getObjectType(this.cleanNode.name).getDescriptor() + overloaderQualifier);
                }
            }
        });
    }

    private void checkAccess(List<? super PatchInstance> patches) {
        this.cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            String dirtyQualifier = dirtyMethod.name + dirtyMethod.desc;

            if ((cleanMethod.access & Opcodes.ACC_STATIC) != 0 && (dirtyMethod.access & Opcodes.ACC_STATIC) == 0) {
                logHeader();
                LOGGER.info("UNSTATIC method {}", dirtyQualifier);

                PatchInstance patch = Patch.builder()
                    .targetClass(this.dirtyNode.name)
                    .targetMethod(dirtyQualifier)
                    .modifyMethodAccess(new ModifyMethodAccess.AccessChange(false, Opcodes.ACC_STATIC))
                    .build();
                patches.add(patch);
            } else if ((cleanMethod.access & Opcodes.ACC_STATIC) == 0 && (dirtyMethod.access & Opcodes.ACC_STATIC) != 0) {
                logHeader();
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

    private void findExpandedLambdas(List<? super PatchInstance> patches, Map<? super String, String> replacementCalls) {
        this.cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            // Find lambdas sorted by their call order. This increases our precision when looking for replaced lambdas that had their suffix number changed.
            List<String> cleanLambdas = findLambdasInMethod(this.cleanNode, cleanMethod);
            List<String> dirtyLambdas = findLambdasInMethod(this.dirtyNode, dirtyMethod);
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
                        logHeader();
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
                            logHeader();
                            LOGGER.info("Found valid replacement {} -> {} in method {}", original.getInternalName(), substitute.getInternalName(), clean.name);
                            valid = true;
                            continue;
                        }
                        newReplacements.remove(replacement);
                        LOGGER.debug("Ignoring replacement {} -> {} in method {}", replacement.getFirst(), replacement.getSecond(), dirty.name);
                    }
                }
                if (valid) {
                    diff = new ParametersDiff(diff.originalCount(), diff.insertions(), newReplacements, swaps);
                } else {
                    return;
                }
            }
            String cleanQualifier = clean.name + clean.desc;
            String dirtyQualifier = dirty.name + dirty.desc;
            logHeader();
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
                .transform(ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.METHOD))
                .build();
            patches.add(patch);
        }
    }

    private List<String> findLambdasInMethod(ClassNode cls, MethodNode method) {
        List<String> list = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length >= 3) {
                for (Object bsmArg : indy.bsmArgs) {
                    if (bsmArg instanceof Handle handle && handle.getOwner().equals(cls.name)) {
                        String lambdaName = remapMethodName(cls, handle.getName(), handle.getDesc());
                        if (lambdaName.startsWith(LAMBDA_PREFIX)) {
                            list.add(handle.getName());
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

    @Nullable
    private static MethodNode findOverloadMethod(final String owner, final MethodNode method, final Collection<MethodNode> others) {
        List<MethodNode> found = new ArrayList<>();
        for (final MethodNode other : others) {
            if (!checkParameters(other, method, true)) {
                continue;
            }
            int labelCount = 0;
            for (final AbstractInsnNode insn : other.instructions) {
                if (insn instanceof LabelNode) {
                    labelCount++;
                }
                if (labelCount <= 1 && insn instanceof MethodInsnNode minsn && minsn.owner.equals(owner) && minsn.name.equals(method.name) && minsn.desc.equals(method.desc)) {
                    // Check return insn
                    boolean returnSeen = false;
                    for (AbstractInsnNode next = minsn.getNext(); next != null; next = next.getNext()) {
                        // Skip debug nodes
                        if (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode) {
                            continue;
                        }
                        // Find first (and single) return after the dirtyMethod call
                        if (RETURN_OPCODES.contains(next.getOpcode())) {
                            // Multiple returns found
                            if (returnSeen) {
                                returnSeen = false;
                                break;
                            }
                            returnSeen = true;
                        } else {
                            // Invalid insn found
                            returnSeen = false;
                            break;
                        }
                    }
                    if (returnSeen) {
                        found.add(other);
                    }
                }
            }
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    private static MethodNode findUniqueMethod(Multimap<String, MethodNode> methods, String name) {
        Collection<MethodNode> values = methods.get(name);
        if (values != null) {
            if (values.size() > 1) {
                throw new IllegalStateException("Found multiple candidates for method " + name);
            }
            return values.iterator().next();
        }
        throw new NullPointerException("Method " + name + " not found");
    }

    private static boolean checkParameters(MethodNode clean, MethodNode dirty, boolean strict) {
        return checkParameters(Type.getArgumentTypes(clean.desc), Type.getArgumentTypes(dirty.desc), strict);
    }

    // Check if dirtyMethod begins with cleanMethod's params
    private static boolean checkParameters(Type[] parameterTypes, Type[] dirtyParameterTypes, boolean strict) {
        if (parameterTypes.length > dirtyParameterTypes.length) {
            return false;
        }
        int i = 0;
        for (int j = 0; i < parameterTypes.length && j < dirtyParameterTypes.length; j++) {
            Type type = dirtyParameterTypes[j];
            if (!parameterTypes[i].equals(type)) {
                if (strict) {
                    return false;
                } else {
                    continue;
                }
            }
            i++;
        }
        return true;
    }

    private static Multimap<String, MethodNode> indexClassMethods(ClassNode classNode) {
        final Multimap<String, MethodNode> methods = HashMultimap.create();
        for (MethodNode method : classNode.methods) {
            methods.put(method.name, method);
        }
        return methods;
    }

    private static boolean isAnonymousClass(String name) {
        // Regex: second to last char in class name must be '$', and the class name must end with a number
        return name.matches("^.+\\$\\d+$");
    }
}
