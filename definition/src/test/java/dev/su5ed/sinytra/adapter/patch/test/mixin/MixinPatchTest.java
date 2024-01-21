package dev.su5ed.sinytra.adapter.patch.test.mixin;

import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.api.RefmapHolder;
import org.assertj.core.api.Assertions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class MixinPatchTest {
    protected LoadResult load(String className, Patch patch) throws Exception {
        final ClassNode patched = load(className);
        final PatchEnvironment env = PatchEnvironment.create(
                new RefmapHolder() {
                    @Override
                    public String remap(String cls, String reference) {
                        return reference;
                    }

                    @Override
                    public void copyEntries(String from, String to) {

                    }
                },
                name -> {
                    try {
                        final ClassNode node = new ClassNode();
                        new ClassReader(name).accept(node, 0);
                        return Optional.of(node);
                    } catch (Exception exception) {
                        return Optional.empty();
                    }
                },
                null
        );
        patch.apply(patched, env);
        return new LoadResult(patched, load(className));
    }

    protected void assertSameCode(
            String className,
            String testName,
            Patch.ClassPatchBuilder patch
    ) throws Exception {
        final LoadResult result = load(className, patch.build());
        final MethodNode patched = result.patched.methods
                .stream().filter(m -> m.name.equals(testName))
                .findFirst().orElseThrow();
        final MethodNode expected = result.expected.methods
                .stream().filter(m -> m.name.equals(testName + "Expected"))
                .findFirst().orElseThrow();

        Assertions.assertThat(patched.parameters)
                .as("Parameters")
                .usingRecursiveFieldByFieldElementComparator()
                .withRepresentation(object -> ((List<ParameterNode>) object)
                        .stream().map(par -> par.name)
                        .collect(Collectors.joining("\n")))
                .isEqualTo(expected.parameters);

        final Predicate<AbstractInsnNode> dontTest = i -> i instanceof LineNumberNode;
        Assertions.assertThat(patched.instructions.iterator())
                .toIterable()
                .as("Instructions")
                .filteredOn(dontTest.negate())
                .usingElementComparator(new InsnComparator())
                .isEqualTo(StreamSupport.stream(expected.instructions.spliterator(), false)
                        .filter(dontTest.negate()).toList());

        Assertions.assertThat(patched.localVariables)
                .as("LVT")
                .usingElementComparator(Comparator.<LocalVariableNode>comparingInt(n -> n.index)
                        .thenComparing(n -> n.name)
                        .thenComparing(n -> n.desc))
                .withRepresentation(object -> {
                    if (object instanceof LocalVariableNode[] lvn) {
                        object = List.of(lvn);
                    }
                    return ((List<LocalVariableNode>) object).stream()
                            .map(n -> n.index + ": " + n.name + " (" + n.desc + ")")
                            .collect(Collectors.joining("\n"));
                })
                .containsExactlyInAnyOrder(expected.localVariables.toArray(LocalVariableNode[]::new));
    }

    public static class InsnComparator implements Comparator<AbstractInsnNode> {
        @Override
        public int compare(AbstractInsnNode o1, AbstractInsnNode o2) {
            if (o1.getClass() != o2.getClass() || o1.getType() != o2.getType()) {
                return -1;
            }

            for (final Field field : o1.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (!field.getType().isPrimitive() && field.getType() != String.class) {
                    continue;
                }
                try {
                    if (((Comparable) field.get(o1)).compareTo(field.get(o2)) != 0) {
                        return -1;
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }

            return 0;
        }
    }

    public record LoadResult(ClassNode patched, ClassNode expected) {}

    protected ClassNode load(String name) throws IOException {
        final ClassNode n = new ClassNode();
        try (final InputStream is = MixinPatchTest.class.getResourceAsStream("/" + name + ".class")) {
            new ClassReader(is).accept(n, 0);
        }
        return n;
    }
}
