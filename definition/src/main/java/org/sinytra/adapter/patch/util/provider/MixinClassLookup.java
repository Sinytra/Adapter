package org.sinytra.adapter.patch.util.provider;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.service.MixinService;

import java.util.Optional;

public class MixinClassLookup implements ClassLookup {
    public static MixinClassLookup INSTANCE = new MixinClassLookup();

    private static final Logger LOGGER = LogUtils.getLogger();

    private MixinClassLookup() {

    }

    @Override
    public Optional<ClassNode> getClass(String name) {
        try {
            return Optional.of(MixinService.getService().getBytecodeProvider().getClassNode(name));
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Target class not found: {}", name);
            return Optional.empty();
        } catch (Throwable t) {
            LOGGER.debug("Error getting class", t);
            return Optional.empty();
        }
    }
}
