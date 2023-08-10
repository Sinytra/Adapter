package dev.su5ed.sinytra.adapter.gradle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchImpl;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ClassAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClassAnalyzer");
    private static final Collection<Integer> RETURN_OPCODES = Set.of(Opcodes.RETURN, Opcodes.ARETURN, Opcodes.DRETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN);

    private final ClassNode cleanNode;
    private final ClassNode dirtyNode;

    private final Multimap<String, MethodNode> cleanMethods;
    private final Multimap<String, MethodNode> dirtyMethods;
    // Methods that exist in both, uses patched MethodNodes from the dirty class
    private final Multimap<String, MethodNode> dirtyCommonMethods;

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
        Collection<MethodNode> allClean = this.cleanMethods.values();
        this.dirtyMethods.forEach((name, method) -> {
            final String dirtyQualifier = method.name + method.desc;
            if (allClean.stream().anyMatch(cleanMethod -> {
                final String cleanQualifier = cleanMethod.name + cleanMethod.desc;
                return dirtyQualifier.equals(cleanQualifier);
            })) {
                this.dirtyCommonMethods.put(name, method);
            }
        });
    }

    public record AnalysisResults(List<PatchImpl> patches) {}

    public record MethodOverload(MethodNode overloader, MethodNode overloadee) {}

    public AnalysisResults analyze() {
        // Try to find added method patches
        List<PatchImpl> overloads = new ArrayList<>();
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
                        LOGGER.info("Class {}", this.cleanNode.name);
                        LOGGER.info("   " + overloader.name + overloader.desc);
                        LOGGER.info("=> " + dirtyQualifier);
                        LOGGER.info("");

                        // TODO Unify interface and impl?
                        PatchImpl patch = (PatchImpl) Patch.builder()
                            .targetClass(this.dirtyNode.name)
                            .targetMethod(overloader.name + overloader.desc)
                            .modifyTarget(method.name + method.desc)
                            .build();
                        overloads.add(patch);
                    }
                }
            }
        });
        return new AnalysisResults(overloads);
    }

    @Nullable
    private MethodNode findOverloadMethod(final String owner, final MethodNode method, final Collection<MethodNode> others) {
        List<MethodNode> found = new ArrayList<>();
        for (final MethodNode other : others) {
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
                        }
                        else {
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
}
