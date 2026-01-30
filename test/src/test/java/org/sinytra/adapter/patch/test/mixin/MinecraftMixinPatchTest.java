package org.sinytra.adapter.patch.test.mixin;

import com.mojang.logging.LogUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.env.ctx.MixinClassGenerator;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.adapter.util.provider.ZipClassLookup;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public abstract class MinecraftMixinPatchTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    public interface AssertCallback {
        void accept(MethodNode patched, MethodNode expected, PatchEnvironment env);
    }

    protected abstract LoadResult load(String className, List<String> allowedMethods) throws Exception;

    protected final void assertSameCode(
        String className,
        String testName,
        AssertCallback... assertions
    ) throws Exception {
        final LoadResult result = load(className, List.of(testName));
        final MethodNode patched = result.patched.methods
            .stream().filter(m -> m.name.equals(testName))
            .findFirst().orElse(null);
        final MethodNode expected = result.expected.methods
            .stream().filter(m -> m.name.equals(testName + "Expected"))
            .findFirst().orElse(null);
        if (patched == null && expected == null) {
            return;
        }

        LOGGER.info("Patched node: \n{}", AdapterUtil.methodNodeToString(patched));

        Assertions.assertThat(patched.parameters)
            .as("Parameters")
            .usingElementComparator(Comparator.comparing(p -> p.name))
            .withRepresentation(object -> ((List<ParameterNode>) object)
                .stream().map(par -> par.name)
                .collect(Collectors.joining("\n")))
            .isEqualTo(expected.parameters);

        final Predicate<AbstractInsnNode> dontTest = i -> i instanceof LineNumberNode || i instanceof FrameNode;
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

        Assertions.assertThat(Objects.requireNonNullElseGet(patched.invisibleParameterAnnotations, () -> new List[0]))
            .as("Invisible parameter annotations")
            .usingElementComparator(Comparator.comparing(l -> l == null ? "null" : ((List<AnnotationNode>) l).stream().map(a -> a.desc).toList().toString()))
            .withRepresentation(object -> {
                if (object instanceof List<?> list) {
                    object = list.toArray(List[]::new);
                }
                return Stream.of(((List<AnnotationNode>[]) object))
                    .<String>map(n -> n == null ? "null" : n.stream().map(o -> o.desc).toList().toString())
                    .collect(Collectors.joining("\n  "));
            })
            .containsExactlyInAnyOrder(Objects.requireNonNullElseGet(expected.invisibleParameterAnnotations, () -> new List[0]));

        Stream.of(assertions).forEach(c -> c.accept(patched, expected, result.env()));
    }

    protected final void assertSameField(
        String className,
        String testName
    ) throws Exception {
        final LoadResult result = load(className, List.of(testName));
        final FieldNode patched = result.patched.fields
            .stream().filter(m -> m.name.equals(testName))
            .findFirst().orElseThrow();
        final FieldNode expected = result.expected.fields
            .stream().filter(m -> m.name.equals(testName + "Expected"))
            .findFirst().orElseThrow();

        LOGGER.info("Patched field node: {} {}", patched.name, patched.desc);

        assertEquals(patched.desc, expected.desc, "Field types differ");
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

    public record LoadResult(PatchEnvironment env, ClassNode patched, ClassNode expected) {
    }

    public static ClassNode loadClass(String name) throws IOException {
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

    protected AssertCallback assertUnique() {
        return (patched, expected, env) -> {
            Assertions.assertThat(patched.visibleAnnotations.stream().anyMatch(ann -> ann.desc.equals(MixinAnnotations.UNIQUE)))
                .as("Unique method")
                .isEqualTo(expected.visibleAnnotations.stream().anyMatch(ann -> ann.desc.equals(MixinAnnotations.UNIQUE)));
        };
    }

    protected AssertCallback assertHasGeneratedMethod(String targetClass) {
        return (patched, expected, env) -> {
            MixinClassGenerator.GeneratedClass generatedClass = env.classGenerator().getGeneratedMixinClasses().get(targetClass);
            assertNotNull(generatedClass, "Missing generated class for " + targetClass);
        };
    }

    protected AssertCallback assertType() {
        return (patched, expected, env) -> {
            AnnotationNode patchedMethodAnn = patched.visibleAnnotations.getFirst();
            AnnotationNode expectedMethodAnn = expected.visibleAnnotations.getFirst();

            Assertions.assertThat(patchedMethodAnn.desc)
                .as("Method Mixin Type")
                .isEqualTo(expectedMethodAnn.desc);
        };
    }

    protected AssertCallback assertTargetMethod() {
        Function<AnnotationNode, List<String>> targetMethodExtractor = node ->
            new AnnotationHandle(node).
                <List<String>>getValue("method")
                .map(AnnotationValueHandle::get)
                .orElseThrow();

        return (patched, expected, env) -> {
            AnnotationNode patchedMethodAnn = patched.visibleAnnotations.getFirst();
            AnnotationNode expectedMethodAnn = expected.visibleAnnotations.getFirst();

            Assertions.assertThat(targetMethodExtractor.apply(patchedMethodAnn))
                .as("Method Targets")
                .isEqualTo(targetMethodExtractor.apply(expectedMethodAnn));
        };
    }

    protected AssertCallback assertInjectionPoint() {
        Function<AnnotationNode, Pair<String, @Nullable String>> injectionPointExtractor = node -> new AnnotationHandle(node).getNested("at").map(h -> {
            String value = h.<String>getValue("value").orElseThrow().get();
            String target = h.<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
            return Pair.of(value, target);
        }).orElseThrow();

        return (patched, expected, env) -> {
            AnnotationNode patchedMethodAnn = patched.visibleAnnotations.getFirst();
            AnnotationNode expectedMethodAnn = expected.visibleAnnotations.getFirst();

            Assertions.assertThat(injectionPointExtractor.apply(patchedMethodAnn))
                .as("Injection Point Annotation")
                .isEqualTo(injectionPointExtractor.apply(expectedMethodAnn));
        };
    }

    protected AssertCallback assertSliceRange() {
        BiFunction<String, AnnotationNode, Pair<String, String>> sliceExtractor = (name, node) -> new AnnotationHandle(node).getNested("slice")
            .flatMap(ann -> ann.getNested(name))
            .map(h -> {
                String value = h.<String>getValue("value").orElseThrow().get();
                String target = h.<String>getValue("target").orElseThrow().get();
                return Pair.of(value, target);
            })
            .orElse(null);

        return (patched, expected, env) -> {
            AnnotationNode patchedMethodAnn = patched.visibleAnnotations.getFirst();
            AnnotationNode expectedMethodAnn = expected.visibleAnnotations.getFirst();

            Assertions.assertThat(sliceExtractor.apply("from", patchedMethodAnn))
                .as("Slice From")
                .isEqualTo(sliceExtractor.apply("from", expectedMethodAnn));

            Assertions.assertThat(sliceExtractor.apply("to", patchedMethodAnn))
                .as("Slice To")
                .isEqualTo(sliceExtractor.apply("to", expectedMethodAnn));
        };
    }

    protected AssertCallback assertTargetsConstant() {
        return (patched, expected, env) -> {
            AnnotationHandle patchedMethodAnn = new AnnotationHandle(patched.visibleAnnotations.getFirst());
            AnnotationHandle expectedMethodAnn = new AnnotationHandle(expected.visibleAnnotations.getFirst());

            assertTrue(patchedMethodAnn.getNested("at").isEmpty());
            assertTrue(patchedMethodAnn.getNested("constant").isPresent());

            Assertions.assertThat(patchedMethodAnn.getNested("constant").get().unwrap().values)
                .as("Values")
                .isEqualTo(expectedMethodAnn.getNested("constant").get().unwrap().values);
        };
    }
}
