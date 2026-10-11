package org.sinytra.adapter.env;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.injection.struct.CallbackInjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.ModifyVariableInjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.struct.AnnotatedMethodInfo;
import org.spongepowered.asm.mixin.struct.SpecialMethodInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.util.asm.IAnnotationHandle;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MockMixinRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodHandles.Lookup TRUSTED_LOOKUP;
    private static final Unsafe UNSAFE;
    private static final MethodHandle TARGET_CTR;
    private static final Class<?> LOCAL_VARIABLE_INJECTION_POINT_CLASS;
    private static final VarHandle ANNOTATED_METHOD_INFO_CONTEXT;
    private static final MethodHandle LOCAL_VARIABLE_INJECTION_POINT_FIND;

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
            LOCAL_VARIABLE_INJECTION_POINT_FIND = MethodHandles.privateLookupIn(LOCAL_VARIABLE_INJECTION_POINT_CLASS, MethodHandles.lookup()).findVirtual(LOCAL_VARIABLE_INJECTION_POINT_CLASS, "find", MethodType.methodType(boolean.class, InjectionInfo.class, InsnList.class, Collection.class, Target.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Target createMixinTarget(TargetPair pair) {
        ClassInfo info = ClassInfo.forName(pair.classNode().name);
        try {
            return (Target) TARGET_CTR.invoke(info, pair.classNode(), pair.methodNode());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static IMixinContext forClass(String className, String targetClass, PatchEnvironment environment) {
        return new ClassMixinContext(className, targetClass, environment);
    }

    public static ISliceContext forSlice(IMixinContext context, MethodNode methodNode) {
        return new MethodSliceContext(context, methodNode);
    }

    public static InjectionInfo forInjectionInfo(String className, String targetClass, PatchEnvironment environment) {
        try {
            InjectionInfo injectionInfo = (InjectionInfo) UNSAFE.allocateInstance(CallbackInjectionInfo.class);
            IMixinContext context = forClass(className, targetClass, environment);
            ANNOTATED_METHOD_INFO_CONTEXT.set(injectionInfo, context);
            CompatibilityContexts.attach(injectionInfo, environment.fabricLVTCompatibility());
            return injectionInfo;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean findModifyVariableInjectionInsns(InjectionPoint injectionPoint, IMixinContext mixinContext, InsnList insns, List<AbstractInsnNode> list, Target target) throws Throwable {
        InjectionInfo info = MockMixinRuntime.createModifyVariableInjectionInfo(mixinContext);
        return (boolean) LOCAL_VARIABLE_INJECTION_POINT_FIND.invoke(injectionPoint, info, insns, list, target);
    }

    public static boolean injectionPointNeedsSpecialCare(InjectionPoint injectionPoint) {
        return LOCAL_VARIABLE_INJECTION_POINT_CLASS.isInstance(injectionPoint);
    }

    public static InjectionInfo createModifyVariableInjectionInfo(IMixinContext mixinContext) throws Exception {
        ModifyVariableInjectionInfo info = (ModifyVariableInjectionInfo) UNSAFE.allocateInstance(ModifyVariableInjectionInfo.class);
        ANNOTATED_METHOD_INFO_CONTEXT.set(info, mixinContext);
        CompatibilityContexts.attach(info, lvtCompatibility(mixinContext));
        return info;
    }

    private static int lvtCompatibility(IMixinContext mixinContext) {
        IMixinConfig config = mixinContext.getMixin().getConfig();
        return config.hasDecoration(FabricUtil.KEY_COMPATIBILITY) ? config.<Integer>getDecoration(FabricUtil.KEY_COMPATIBILITY) : FabricUtil.COMPATIBILITY_LATEST;
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

    private record ReferenceRemapper(PatchEnvironment env) implements IReferenceMapper {
        @Override
        public String remapWithContext(String context, String className, String reference) {
            return this.env.refmapHolder().remap(className, reference);
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

        public ClassMixinContext(String className, String targetClass, PatchEnvironment env) {
            this.className = className;
            this.targetClass = targetClass;
            this.referenceRemapper = new ReferenceRemapper(env);
            this.mixinInfo = new DummyMixinInfo(new DummyMixinConfig(env.fabricLVTCompatibility()));
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
