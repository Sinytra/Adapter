package org.sinytra.adapter.patch.test.mixin;

import com.mojang.logging.LogUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.Assertions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.sinytra.adapter.patch.util.provider.ZipClassLookup;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;

public abstract class MinecraftMixinPatchTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    protected abstract LoadResult load(String className) throws Exception;

    @SafeVarargs
    protected final void assertSameCode(
        String className,
        String testName,
        BiConsumer<MethodNode, MethodNode>... assertions
    ) throws Exception {
        final LoadResult result = load(className);
        final MethodNode patched = result.patched.methods
            .stream().filter(m -> m.name.equals(testName))
            .findFirst().orElseThrow();
        final MethodNode expected = result.expected.methods
            .stream().filter(m -> m.name.equals(testName + "Expected"))
            .findFirst().orElseThrow();

        LOGGER.info("Patched node: \n{}", AdapterUtil.methodNodeToString(patched));

        Assertions.assertThat(patched.parameters)
            .as("Parameters")
            .usingElementComparator(Comparator.comparing(p -> p.name))
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

        Stream.of(assertions).forEach(c -> c.accept(patched, expected));
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

    public record LoadResult(ClassNode patched, ClassNode expected) {
    }

    protected ClassNode loadClass(String name) throws IOException {
        final ClassNode n = new ClassNode();
        try (final InputStream is = MinecraftMixinPatchTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            new ClassReader(is).accept(n, 0);
        }
        return n;
    }

    protected static ClassLookup createCleanLookup() {
        // Search for system property
        Path cleanPath = Optional.ofNullable(System.getProperty("adapter.clean.path"))
            .map(Path::of)
            .orElseThrow(() -> new RuntimeException("Could not determine clean minecraft artifact path"));
        try {
            ZipFile zipFile = new ZipFile(cleanPath.toFile());
            return new ZipClassLookup(zipFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static ClassLookup createDirtyLookup() {
        return name -> {
            try {
                final ClassNode node = new ClassNode();
                new ClassReader(name).accept(node, 0);
                return Optional.of(node);
            } catch (Exception exception) {
                return Optional.empty();
            }
        };
    }

    protected BiConsumer<MethodNode, MethodNode> assertTargetMethod() {
        Function<AnnotationNode, List<String>> targetMethodExtractor = node -> new AnnotationHandle(node).<List<String>>getValue("method").map(AnnotationValueHandle::get).orElseThrow();

        return (patched, expected) -> {
            AnnotationNode patchedMethodAnn = patched.visibleAnnotations.getFirst();
            AnnotationNode expectedMethodAnn = expected.visibleAnnotations.getFirst();

            Assertions.assertThat(targetMethodExtractor.apply(patchedMethodAnn))
                .as("Method Targets")
                .isEqualTo(targetMethodExtractor.apply(expectedMethodAnn));
        };
    }

    protected BiConsumer<MethodNode, MethodNode> assertInjectionPoint() {
        Function<AnnotationNode, Pair<String, String>> injectionPointExtractor = node -> new AnnotationHandle(node).getNested("at").map(h -> {
            String value = h.<String>getValue("value").orElseThrow().get();
            String target = h.<String>getValue("target").orElseThrow().get();
            return Pair.of(value, target);
        }).orElseThrow();

        return (patched, expected) -> {
            AnnotationNode patchedMethodAnn = patched.visibleAnnotations.getFirst();
            AnnotationNode expectedMethodAnn = expected.visibleAnnotations.getFirst();

            Assertions.assertThat(injectionPointExtractor.apply(patchedMethodAnn))
                .as("Injection Point Annotation")
                .isEqualTo(injectionPointExtractor.apply(expectedMethodAnn));
        };
    }
}
