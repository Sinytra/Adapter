package dev.su5ed.sinytra.adapter.gradle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchImpl;
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

public class ClassAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClassAnalyzer");
    private static final Collection<Integer> RETURN_OPCODES = Set.of(Opcodes.RETURN, Opcodes.ARETURN, Opcodes.DRETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN);
    private static final String LAMBDA_PREFIX = "lambda$";

    private final ClassNode cleanNode;
    private final ClassNode dirtyNode;
    private final IMappingFile mappings;

    private final Multimap<String, MethodNode> cleanMethods;
    private final Multimap<String, MethodNode> dirtyMethods;
    // Methods that exist exclusively in one class and not the other
    private final Multimap<String, MethodNode> cleanOnlyMethods;
    private final Multimap<String, MethodNode> dirtyOnlyMethods;
    // Methods that exist in both, uses patched MethodNodes from the dirty class
    private final Multimap<String, MethodNode> dirtyCommonMethods;

    private final Map<String, FieldNode> cleanFields;
    private final Map<String, FieldNode> dirtyFields;

    public static ClassAnalyzer create(byte[] cleanData, byte[] dirtyData, IMappingFile mappings) {
        return new ClassAnalyzer(readClassNode(cleanData), readClassNode(dirtyData), mappings);
    }

    private static ClassNode readClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    public ClassAnalyzer(ClassNode cleanNode, ClassNode dirtyNode, IMappingFile mappings) {
        this.cleanNode = cleanNode;
        this.dirtyNode = dirtyNode;
        this.mappings = mappings;

        this.cleanMethods = indexClassMethods(cleanNode);
        this.dirtyMethods = indexClassMethods(dirtyNode);
        this.dirtyCommonMethods = HashMultimap.create();
        this.cleanOnlyMethods = HashMultimap.create();
        this.dirtyOnlyMethods = HashMultimap.create();
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
            if (allClean.stream().anyMatch(cleanMethod -> {
                final String cleanQualifier = cleanMethod.name + cleanMethod.desc;
                return dirtyQualifier.equals(cleanQualifier);
            })) {
                this.dirtyCommonMethods.put(name, method);
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

    private boolean loggedHeader = false;

    private void logHeader() {
        if (!this.loggedHeader) {
            LOGGER.info("Class {}", this.cleanNode.name);
            this.loggedHeader = true;
        }
    }

    public record AnalysisResults(List<PatchImpl> patches, List<String> modifiedFieldWarnings) {
    }

    public AnalysisResults analyze() {
        // Try to find added dirtyMethod patches
        List<PatchImpl> patches = new ArrayList<>();
        this.dirtyMethods.asMap().forEach((name, methods) -> {
            for (MethodNode method : methods) {
                final String dirtyQualifier = method.name + method.desc;
                final Collection<MethodNode> cleanMethods = this.cleanMethods.get(name);

                if (cleanMethods.stream().noneMatch(cleanMethod -> {
                    final String cleanQualifier = cleanMethod.name + cleanMethod.desc;
                    return dirtyQualifier.equals(cleanQualifier);
                })) {
                    MethodNode overloader = findOverloadMethod(this.dirtyNode.name, method, this.dirtyCommonMethods.values());
                    if (overloader != null) {
                        logHeader();
                        LOGGER.info("OVERLOAD");
                        LOGGER.info("   " + overloader.name + overloader.desc);
                        LOGGER.info("=> " + dirtyQualifier);
                        LOGGER.info("===");

                        // TODO Unify interface and impl?
                        PatchImpl patch = (PatchImpl) Patch.builder()
                            .targetClass(this.dirtyNode.name)
                            .targetMethod(overloader.name + overloader.desc)
                            .modifyTarget(method.name + method.desc)
                            .build();
                        patches.add(patch);
                    }
                }
            }
        });
        if (!isAnonymousInnerClass(this.cleanNode.name)) {
            Set<String> replacedMethods = new HashSet<>();
            findExpandedMethods(patches, replacedMethods);
            findExpandedLambdas(patches, replacedMethods);
        }
        if (this.loggedHeader) {
            LOGGER.info("");
        }

        List<String> modifiedFieldWarnings = new ArrayList<>();
        this.dirtyFields.forEach((name, field) -> {
            FieldNode cleanField = this.cleanFields.get(name);
            if (cleanField != null && !field.desc.equals(cleanField.desc)) {
                modifiedFieldWarnings.add("Field %s.%s changed its type from %s to %s".formatted(this.dirtyNode.name, name, cleanField.desc, field.desc));
            }
        });

        return new AnalysisResults(patches, modifiedFieldWarnings);
    }

    private void findExpandedMethods(List<PatchImpl> patches, Set<String> replacedMethods) {
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
                    tryFindExpandedMethod(patches, replacedMethods, method, dirtyMethod, true);
                }
            });
        });
    }

    private void findExpandedLambdas(List<PatchImpl> patches, Set<String> replacedMethods) {
        this.dirtyCommonMethods.forEach((dirtyName, dirtyMethod) -> {
            String qualifier = dirtyMethod.name + dirtyMethod.desc;

            this.cleanMethods.forEach((cleanName, cleanMethod) -> {
                String cleanQualifier = cleanMethod.name + cleanMethod.desc;

                if (qualifier.equals(cleanQualifier)) {
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
                                    tryFindExpandedMethod(patches, replacedMethods, cleanLambdaMethod, dirtyLambdaMethod, false);
                                }
                            }
                            else {
                                cleanIdx++;
                                dirtyIdx++;
                            }
                        }
                    }
                }
            });
        });
    }
    
    private void tryFindExpandedMethod(List<PatchImpl> patches, Set<String> replacedMethods, MethodNode clean, MethodNode dirty, boolean strictParams) {
        // Skip methods with different return types
        if (!Type.getReturnType(clean.desc).equals(Type.getReturnType(dirty.desc))
            // Make an educated guess and assume all dirtyMethod replacements keep the same name.
            || !dirty.name.equals(remapMethodName(this.cleanNode, clean.name, clean.desc))
        ) {
            return;
        }

        Type[] parameterTypes = Type.getArgumentTypes(clean.desc);
        Type[] dirtyParameterTypes = Type.getArgumentTypes(dirty.desc);
        if (parameterTypes.length > 0 && parameterTypes.length < dirtyParameterTypes.length && checkParameters(parameterTypes, dirtyParameterTypes, strictParams)) {
            String methodQualifier = clean.name + clean.desc;
            logHeader();
            LOGGER.info("REPLACE");
            LOGGER.info("   " + methodQualifier);
            LOGGER.info("\\> " + dirty.name + dirty.desc);
            LOGGER.info("===");

            if (!replacedMethods.add(methodQualifier)) {
                throw new IllegalStateException("Duplicate replacement for %s.%s".formatted(this.cleanNode.name, methodQualifier));
            }

            PatchImpl patch = (PatchImpl) Patch.builder()
                .targetClass(this.dirtyNode.name)
                .targetMethod(methodQualifier)
                .modifyTarget(dirty.name + dirty.desc)
                .setParams(Arrays.asList(dirtyParameterTypes))
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

    private MethodNode findUniqueMethod(Multimap<String, MethodNode> methods, String name) {
        Collection<MethodNode> values = methods.get(name);
        if (values != null) {
            if (values.size() > 1) {
                throw new IllegalStateException("Found multiple candidates for method " + name);
            }
            return values.iterator().next();
        }
        throw new NullPointerException("Method " + name + " not found");
    }

    private boolean checkParameters(MethodNode clean, MethodNode dirty, boolean strict) {
        return checkParameters(Type.getArgumentTypes(clean.desc), Type.getArgumentTypes(dirty.desc), strict);
    }

    // Check if dirtyMethod begins with cleanMethod's params
    private boolean checkParameters(Type[] parameterTypes, Type[] dirtyParameterTypes, boolean strict) {
        if (parameterTypes.length > dirtyParameterTypes.length) {
            return false;
        }
        int i = 0;
        for (int j = 0; i < parameterTypes.length && j < dirtyParameterTypes.length; j++) {
            Type type = dirtyParameterTypes[j];
            if (!parameterTypes[i].equals(type)) {
                if (strict) {
                    return false;
                }
                else {
                    continue;
                }
            }
            i++;
        }
        return true;
    }

    @Nullable
    private MethodNode findOverloadMethod(final String owner, final MethodNode method, final Collection<MethodNode> others) {
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

    private Multimap<String, MethodNode> indexClassMethods(ClassNode classNode) {
        final Multimap<String, MethodNode> methods = HashMultimap.create();
        for (MethodNode method : classNode.methods) {
            methods.put(method.name, method);
        }
        return methods;
    }

    private String remapMethodName(ClassNode cls, String name, String desc) {
        return Optional.ofNullable(this.mappings.getClass(cls.name))
            .map(c -> c.getMethod(name, desc))
            .map(IMappingFile.INode::getMapped)
            .orElse(name);
    }
    
    private static boolean isAnonymousInnerClass(String name) {
        String[] array = name.split("\\$");
        return array.length > 1 && array[array.length - 1].matches("[0-9]+");
    }
}
