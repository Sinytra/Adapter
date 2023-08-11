package dev.su5ed.sinytra.adapter.gradle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchImpl;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
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

    private final Multimap<String, MethodNode> cleanMethods;
    private final Multimap<String, MethodNode> dirtyMethods;
    // Methods that exist exclusively in one class and not the other
    private final Multimap<String, MethodNode> cleanOnlyMethods;
    private final Multimap<String, MethodNode> dirtyOnlyMethods;
    // Methods that exist in both, uses patched MethodNodes from the dirty class
    private final Multimap<String, MethodNode> dirtyCommonMethods;

    private final Map<String, FieldNode> cleanFields;
    private final Map<String, FieldNode> dirtyFields;

    public static ClassAnalyzer create(byte[] cleanData, byte[] dirtyData) {
        return new ClassAnalyzer(readClassNode(cleanData), readClassNode(dirtyData));
    }

    private static ClassNode readClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    public ClassAnalyzer(ClassNode cleanNode, ClassNode dirtyNode) {
        this.cleanNode = cleanNode;
        this.dirtyNode = dirtyNode;

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

    public AnalysisResults analyze(IMappingFile mappings) {
        // Try to find added method patches
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
            findExpandedMethods(patches, mappings);
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

    private void findExpandedMethods(List<PatchImpl> patches, IMappingFile mappings) {
        Set<String> replacedMethods = new HashSet<>();
        // Find "expanded" methods where forge replaces a method with one that takes in additional parameters
        this.cleanOnlyMethods.forEach((name, method) -> {
            // Skip lambdas for now
            String mappedClean = Optional.ofNullable(mappings.getClass(this.cleanNode.name))
                .map(cls -> cls.getMethod(method.name, method.desc))
                .map(IMappingFile.INode::getMapped)
                .orElse(method.name);
            if (mappedClean.startsWith(LAMBDA_PREFIX)) {
                return;
            }

            this.dirtyOnlyMethods.forEach((dirtyName, dirtyMethod) -> {
                // Skip methods with different return types
                // Skip lambdas for now
                if (!Type.getReturnType(method.desc).equals(Type.getReturnType(dirtyMethod.desc)) || dirtyName.startsWith(LAMBDA_PREFIX)
                    // Make an educated guess and assume all method replacements keep the same name.
                    || !dirtyName.equals(mappedClean)
                ) {
                    return;
                }

                Type[] parameterTypes = Type.getArgumentTypes(method.desc);
                Type[] dirtyParameterTypes = Type.getArgumentTypes(dirtyMethod.desc);
                if (parameterTypes.length > 0 && parameterTypes.length < dirtyParameterTypes.length && checkParameters(parameterTypes, dirtyParameterTypes)) {
                    String methodQualifier = method.name + method.desc;
                    logHeader();
                    LOGGER.info("REPLACE");
                    LOGGER.info("   " + methodQualifier);
                    LOGGER.info("\\> " + dirtyMethod.name + dirtyMethod.desc);
                    LOGGER.info("===");

                    if (!replacedMethods.add(methodQualifier)) {
                        throw new IllegalStateException("Duplicate replacement for %s.%s".formatted(this.cleanNode.name, methodQualifier));
                    }

                    PatchImpl patch = (PatchImpl) Patch.builder()
                        .targetClass(this.dirtyNode.name)
                        .targetMethod(methodQualifier)
                        .modifyTarget(dirtyMethod.name + dirtyMethod.desc)
                        .setParams(Arrays.asList(dirtyParameterTypes))
                        .build();
                    patches.add(patch);
                }
            });
        });
    }

    private boolean checkParameters(MethodNode clean, MethodNode dirty) {
        return checkParameters(Type.getArgumentTypes(clean.desc), Type.getArgumentTypes(dirty.desc));
    }

    // Check if dirty method begins with clean method's params
    private boolean checkParameters(Type[] parameterTypes, Type[] dirtyParameterTypes) {
        if (parameterTypes.length > dirtyParameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            Type type = dirtyParameterTypes[i];
            if (!parameterTypes[i].equals(type)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private MethodNode findOverloadMethod(final String owner, final MethodNode method, final Collection<MethodNode> others) {
        List<MethodNode> found = new ArrayList<>();
        for (final MethodNode other : others) {
            if (!checkParameters(other, method)) {
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
                        // Find first (and single) return after the method call
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

    private static boolean isAnonymousInnerClass(String name) {
        String[] array = name.split("\\$");
        return array.length > 1 && array[array.length - 1].matches("[0-9]+");
    }
}
