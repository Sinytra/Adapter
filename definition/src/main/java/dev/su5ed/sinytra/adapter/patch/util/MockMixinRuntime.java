package dev.su5ed.sinytra.adapter.patch.util;

import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.injection.struct.CallbackInjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.util.asm.IAnnotationHandle;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class MockMixinRuntime {
    private static final MethodHandles.Lookup TRUSTED_LOOKUP;
    private static final Unsafe UNSAFE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
            Field hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            TRUSTED_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(hackfield), UNSAFE.staticFieldOffset(hackfield));
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
            VarHandle handle = TRUSTED_LOOKUP.findVarHandle(InjectionInfo.class, "context", IMixinContext.class);
            IMixinContext context = forClass(className, targetClass, environment);
            handle.set(injectionInfo, context);
            return injectionInfo;
        } catch (Throwable t) {
            throw new RuntimeException(t);
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
            return this.env.getRefmapHolder().remap(className, reference);
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

    private static class DummyMixinConfig implements IMixinConfig {
        @Override
        public MixinEnvironment getEnvironment() {
            return MixinEnvironment.getCurrentEnvironment();
        }

        //@formatter:off
        @Override public String getName() {throw new UnsupportedOperationException();}
        @Override public String getMixinPackage() {throw new UnsupportedOperationException();}
        @Override public int getPriority() {return 0;}
        @Override public IMixinConfigPlugin getPlugin() {return null;}
        @Override public boolean isRequired() {throw new UnsupportedOperationException();}
        @Override public Set<String> getTargets() {throw new UnsupportedOperationException();}
        @Override public <V> void decorate(String key, V value) {}
        @Override public boolean hasDecoration(String key) {return false;}
        @Override public <V> V getDecoration(String key) {return null;}
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
            this.mixinInfo = new DummyMixinInfo(new DummyMixinConfig());
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
