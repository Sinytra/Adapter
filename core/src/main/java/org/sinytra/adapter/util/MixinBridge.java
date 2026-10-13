package org.sinytra.adapter.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.RefmapHolder;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.gen.AccessorInfo;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.modify.InvalidImplicitDiscriminatorException;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.points.BeforeConstant;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.injection.selectors.InvalidSelectorException;
import org.spongepowered.asm.mixin.injection.struct.*;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.struct.AnnotatedMethodInfo;
import org.spongepowered.asm.mixin.struct.SpecialMethodInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Locals;
import org.spongepowered.asm.util.asm.IAnnotationHandle;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MixinBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodHandles.Lookup TRUSTED_LOOKUP;
    private static final Unsafe UNSAFE;
    private static final MethodHandle TARGET_CTR;
    private static final Class<?> LOCAL_VARIABLE_INJECTION_POINT_CLASS;
    private static final VarHandle ANNOTATED_METHOD_INFO_CONTEXT;
    private static final MethodHandle LOCAL_VARIABLE_INJECTION_POINT_FIND;
    private static final VarHandle INJECTION_POINT_TYPES;
    private static final String CLS_REGEX = "^([A-Za-z_][A-Za-z0-9_]*[\\.\\$])+[A-Za-z_][A-Za-z0-9_]*$";
    private static final Object LOCALS_LOCK = new Object();

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
            Field hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            TRUSTED_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(hackfield), UNSAFE.staticFieldOffset(hackfield));

            TARGET_CTR = MethodHandles.privateLookupIn(Target.class, MethodHandles.lookup()).findConstructor(Target.class, MethodType.methodType(void.class, ClassInfo.class, ClassNode.class, MethodNode.class));

            LOCAL_VARIABLE_INJECTION_POINT_CLASS = Class.forName("org.spongepowered.asm.mixin.injection.modify.ModifyVariableInjector$LocalVariableInjectionPoint");
            ANNOTATED_METHOD_INFO_CONTEXT = TRUSTED_LOOKUP.findVarHandle(AnnotatedMethodInfo.class, "context", IMixinContext.class);
            INJECTION_POINT_TYPES = MethodHandles.privateLookupIn(InjectionPoint.class, MethodHandles.lookup()).findStaticVarHandle(InjectionPoint.class, "types", Map.class);
            LOCAL_VARIABLE_INJECTION_POINT_FIND = MethodHandles.privateLookupIn(LOCAL_VARIABLE_INJECTION_POINT_CLASS, MethodHandles.lookup()).findVirtual(LOCAL_VARIABLE_INJECTION_POINT_CLASS, "find", MethodType.methodType(boolean.class, InjectionInfo.class, InsnList.class, Collection.class, Target.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Session session(String mixinClass, MethodNode mixinMethod, ClassNode targetClass, MethodNode targetMethod, PatchEnvironment environment) {
        return session(mixinClass, mixinMethod, targetClass, targetMethod, environment.refmapHolder(), environment.fabricLVTCompatibility());
    }

    public static Session session(String mixinClass, MethodNode mixinMethod, ClassNode targetClass, MethodNode targetMethod, RefmapHolder refmapHolder, int lvtCompatibility) {
        return new Session(new ClassMixinContext(mixinClass, targetClass.name, refmapHolder, lvtCompatibility), mixinMethod, targetClass, targetMethod, lvtCompatibility);
    }

    public static LocalVariableNode[] getLocalsAt(ClassNode targetClass, MethodNode targetMethod, AbstractInsnNode insn, int lvtCompatLevel) {
        // Synchronize to avoid issues in mixin. This is necessary.
        synchronized (LOCALS_LOCK) {
            return Locals.getLocalsAt(targetClass, targetMethod, insn, lvtCompatLevel);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean isKnownInjectionPointType(String value) {
        Map<String, Class<? extends InjectionPoint>> types = (Map<String, Class<? extends InjectionPoint>>) INJECTION_POINT_TYPES.get();
        return types.containsKey(value.toUpperCase(Locale.ROOT)) || value.matches(CLS_REGEX);
    }

    @Nullable
    public static String getAccessorFieldName(String methodName) {
        AccessorInfo.AccessorName name = AccessorInfo.AccessorName.of(methodName);
        return name != null ? name.name : null;
    }

    @Nullable
    public static String getGroupName(MethodNode method) {
        AnnotationNode node = Annotations.getInvisible(method, Group.class);
        if (node == null) return null;

        String name = Annotations.getValue(node, "name");
        return name == null || name.isEmpty() ? null : name;
    }

    public static List<InjectorGroup> findInjectorGroups(List<MethodNode> methods) {
        InjectorGroupInfo.Map infos = new InjectorGroupInfo.Map();
        Multimap<String, MethodNode> members = HashMultimap.create();

        for (MethodNode method : methods) {
            InjectorGroupInfo info = infos.parseGroup(method, "default");
            if (info.isDefault() || info.getName().equals("default")) continue;

            members.put(info.getName(), method);
        }

        List<InjectorGroup> groups = new ArrayList<>();
        members.asMap().forEach((name, mems) ->
            groups.add(new InjectorGroup(name, List.copyOf(mems), infos.forName(name).getMinRequired())));
        return groups;
    }

    public record InjectorGroup(String name, List<MethodNode> members, int minRequired) {
    }

    public record MemberSelector(@Nullable String owner, @Nullable String name, @Nullable String desc, int maxMatches, boolean field) {
    }

    @Nullable
    public static MemberSelector parseMemberSelector(String input) {
        String selector = input.strip();
        if (selector.startsWith("/") || selector.startsWith("@")) {
            return null;
        }
        try {
            MemberInfo info = MemberInfo.parse(selector, null).validate();
            return new MemberSelector(info.getOwner(), info.getName(), info.getDesc(), info.getMaxMatchCount(), info.isField());
        } catch (InvalidSelectorException e) {
            return null;
        }
    }

    public record LocalCandidate(int ordinal, int index) {
    }

    public static final class Session {
        private final IMixinContext mixinContext;
        private final int lvtCompatibility;
        private final MethodNode mixinMethod;
        private final ClassNode targetClass;
        private final MethodNode targetMethod;
        @Nullable
        private Target target;

        private Session(IMixinContext mixinContext, MethodNode mixinMethod, ClassNode targetClass, MethodNode targetMethod, int lvtCompatibility) {
            this.mixinContext = mixinContext;
            this.lvtCompatibility = lvtCompatibility;
            this.mixinMethod = mixinMethod;
            this.targetClass = targetClass;
            this.targetMethod = targetMethod;
        }

        public List<AbstractInsnNode> findInjectionPoint(AnnotationNode parent, AnnotationNode at, @Nullable AnnotationNode slice) {
            try {
                InjectionPoint point = InjectionPoint.parse(this.mixinContext, this.mixinMethod, parent, at);
                return find(point, slice);
            } catch (Throwable e) {
                LOGGER.error("Error finding injection insns", e);
                return List.of();
            }
        }

        public List<AbstractInsnNode> findConstant(AnnotationNode constant, String returnDesc, @Nullable AnnotationNode slice) {
            try {
                return find(new BeforeConstant(this.mixinContext, constant, returnDesc), slice);
            } catch (Throwable e) {
                LOGGER.error("Error finding injection insns", e);
                return List.of();
            }
        }

        public InsnList slice(AnnotationNode slice) {
            ISliceContext sliceContext = new MethodSliceContext(this.mixinContext, this.mixinMethod);
            return MethodSlice.parse(sliceContext, slice).getSlice(target());
        }

        public int findLocal(AnnotationNode selector, Type type, AbstractInsnNode insn) {
            LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(selector);
            try {
                return discriminator.findLocal(localsContext(type, discriminator.isArgsOnly(), insn));
            } catch (InvalidImplicitDiscriminatorException e) {
                return -1;
            }
        }

        public List<LocalCandidate> findLocalCandidates(Type type, AbstractInsnNode insn, boolean argsOnly) {
            LocalVariableDiscriminator.Context context = localsContext(type, argsOnly, insn);
            List<LocalCandidate> candidates = new ArrayList<>();
            // Ordinals start at slot 0, so the first ordinal of a type may belong to `this` and select nothing
            for (int ordinal = 0; ordinal < this.targetMethod.maxLocals; ordinal++) {
                int index = new LocalVariableDiscriminator(argsOnly, ordinal, -1, Set.of(), false).findLocal(context);
                if (index != -1) {
                    candidates.add(new LocalCandidate(ordinal, index));
                }
            }
            return candidates;
        }

        private LocalVariableDiscriminator.Context localsContext(Type type, boolean argsOnly, AbstractInsnNode insn) {
            InjectionInfo injectionInfo = createInjectionInfo(CallbackInjectionInfo.class, this.mixinContext, this.lvtCompatibility);
            // The context reads the locals through Locals.getLocalsAt, which isn't thread safe
            synchronized (LOCALS_LOCK) {
                return new LocalVariableDiscriminator.Context(injectionInfo, type, argsOnly, target(), insn);
            }
        }

        private List<AbstractInsnNode> find(InjectionPoint point, @Nullable AnnotationNode slice) throws Throwable {
            List<AbstractInsnNode> targetInsns = new ArrayList<>();
            if (LOCAL_VARIABLE_INJECTION_POINT_CLASS.isInstance(point)) {
                // Local variable injection points need a ModifyVariable injector, and always search the whole method
                InjectionInfo info = createInjectionInfo(ModifyVariableInjectionInfo.class, this.mixinContext, this.lvtCompatibility);
                LOCAL_VARIABLE_INJECTION_POINT_FIND.invoke(point, info, this.targetMethod.instructions, targetInsns, target());
            } else {
                InsnList instructions = slice != null ? slice(slice) : this.targetMethod.instructions;
                point.find(this.targetMethod.desc, instructions, targetInsns);
            }
            return targetInsns;
        }

        private Target target() {
            if (this.target == null) {
                ClassInfo info = ClassInfo.forName(this.targetClass.name);
                try {
                    this.target = (Target) TARGET_CTR.invoke(info, this.targetClass, this.targetMethod);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            return this.target;
        }
    }

    private static InjectionInfo createInjectionInfo(Class<? extends InjectionInfo> type, IMixinContext mixinContext, int lvtCompatibility) {
        try {
            InjectionInfo info = (InjectionInfo) UNSAFE.allocateInstance(type);
            ANNOTATED_METHOD_INFO_CONTEXT.set(info, mixinContext);
            CompatibilityContexts.attach(info, lvtCompatibility);
            return info;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class CompatibilityContexts {
        @Nullable
        private static final VarHandle[] HANDLES;
        @Nullable
        private static final Class<?>[] CLASSES;
        private static final Map<Integer, Object> CONTEXTS = new ConcurrentHashMap<>();

        static {
            VarHandle[] handles = null;
            Class<?>[] classes = null;
            try {
                Class<?> targetContext = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTargetContext");
                Class<?> mixinInfo = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
                Class<?> mixinConfig = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
                handles = new VarHandle[]{
                    TRUSTED_LOOKUP.findVarHandle(SpecialMethodInfo.class, "mixin", targetContext),
                    TRUSTED_LOOKUP.findVarHandle(targetContext, "mixin", mixinInfo),
                    TRUSTED_LOOKUP.findVarHandle(mixinInfo, "parent", mixinConfig),
                    TRUSTED_LOOKUP.findVarHandle(mixinConfig, "decorations", Map.class)
                };
                classes = new Class<?>[]{targetContext, mixinInfo, mixinConfig};
            } catch (Throwable t) {
                LOGGER.debug("Mixin target contexts are unavailable, local variable compatibility uses the selector context", t);
            }
            HANDLES = handles;
            CLASSES = classes;
        }

        public static void attach(InjectionInfo info, int lvtCompatibility) {
            if (HANDLES != null) {
                HANDLES[0].set(info, CONTEXTS.computeIfAbsent(lvtCompatibility, CompatibilityContexts::create));
            }
        }

        private static Object create(int lvtCompatibility) {
            try {
                Object config = UNSAFE.allocateInstance(CLASSES[2]);
                HANDLES[3].set(config, new HashMap<>(Map.of(FabricUtil.KEY_COMPATIBILITY, lvtCompatibility)));
                Object mixinInfo = UNSAFE.allocateInstance(CLASSES[1]);
                HANDLES[2].set(mixinInfo, config);
                Object context = UNSAFE.allocateInstance(CLASSES[0]);
                HANDLES[1].set(context, mixinInfo);
                return context;
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private record MethodSliceContext(IMixinContext context, MethodNode methodNode) implements ISliceContext {
        @Override
        public IMixinContext getMixin() {
            return this.context;
        }

        @Override
        public String remap(String reference) {
            return this.context.getReferenceMapper().remap(this.context.getClassName(), reference);
        }

        //@formatter:off
        @Override public String getElementDescription() {return "dummy";}
        @Override public MethodSlice getSlice(String id) {throw new UnsupportedOperationException();}
        @Override public MethodNode getMethod() {return this.methodNode;}
        @Override public AnnotationNode getAnnotationNode() {throw new UnsupportedOperationException();}
        @Override public ISelectorContext getParent() {throw new UnsupportedOperationException();}
        @Override public IAnnotationHandle getAnnotation() {throw new UnsupportedOperationException();}
        @Override public IAnnotationHandle getSelectorAnnotation() {throw new UnsupportedOperationException();}
        @Override public String getSelectorCoordinate(boolean leaf) {throw new UnsupportedOperationException();}
        @Override public void addMessage(String format, Object... args) {}
        //@formatter:on
    }

    private record ReferenceRemapper(RefmapHolder refmapHolder) implements IReferenceMapper {
        @Override
        public String remapWithContext(String context, String className, String reference) {
            return this.refmapHolder.remap(className, reference);
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

    private record DummyMixinConfig(int lvtCompatibility) implements IMixinConfig {
        @Override
        public MixinEnvironment getEnvironment() {
            return MixinEnvironment.getCurrentEnvironment();
        }

        @Override
        public boolean hasDecoration(String key) {
            return FabricUtil.KEY_COMPATIBILITY.equals(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> V getDecoration(String key) {
            return FabricUtil.KEY_COMPATIBILITY.equals(key) ? (V) (Integer) this.lvtCompatibility : null;
        }

        //@formatter:off
        @Override public IMixinConfigSource getSource() {throw new UnsupportedOperationException();}
        @Override public String getCleanSourceId() {return "dummy";}
        @Override public String getName() {throw new UnsupportedOperationException();}
        @Override public String getMixinPackage() {throw new UnsupportedOperationException();}
        @Override public int getPriority() {return 0;}
        @Override public IMixinConfigPlugin getPlugin() {return null;}
        @Override public boolean isRequired() {throw new UnsupportedOperationException();}
        @Override public Set<String> getTargets() {throw new UnsupportedOperationException();}
        @Override public <V> void decorate(String key, V value) {}
        //@formatter:on
    }

    private record DummyMixinInfo(IMixinConfig config) implements IMixinInfo {
        @Override
        public IMixinConfig getConfig() {
            return config;
        }

        //@formatter:off
        @Override public String getName() {throw new UnsupportedOperationException();}
        @Override public String getClassName() {throw new UnsupportedOperationException();}
        @Override public String getClassRef() {throw new UnsupportedOperationException();}
        @Override public byte[] getClassBytes() {throw new UnsupportedOperationException();}
        @Override public boolean isDetachedSuper() {throw new UnsupportedOperationException();}
        @Override public ClassNode getClassNode(int flags) {throw new UnsupportedOperationException();}
        @Override public List<String> getTargetClasses() {throw new UnsupportedOperationException();}
        @Override public int getPriority() {throw new UnsupportedOperationException();}
        @Override public MixinEnvironment.Phase getPhase() {throw new UnsupportedOperationException();}
        //@formatter:on
    }

    private static final class ClassMixinContext implements IMixinContext {
        private final String className;
        private final String targetClass;
        private final ReferenceRemapper referenceRemapper;
        private final IMixinInfo mixinInfo;

        public ClassMixinContext(String className, String targetClass, RefmapHolder refmapHolder, int lvtCompatibility) {
            this.className = className;
            this.targetClass = targetClass;
            this.referenceRemapper = new ReferenceRemapper(refmapHolder);
            this.mixinInfo = new DummyMixinInfo(new DummyMixinConfig(lvtCompatibility));
        }

        @Override
        public IMixinInfo getMixin() {
            return this.mixinInfo;
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
        public String getTargetClassName() {
            return this.targetClass.replace('/', '.');
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
