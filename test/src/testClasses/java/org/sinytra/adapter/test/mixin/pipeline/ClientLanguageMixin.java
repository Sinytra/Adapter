package org.sinytra.adapter.test.mixin.pipeline;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.InputStream;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
    @Redirect(
        method = "appendFrom(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"
        )
    )
    private static void saveSeparately(InputStream inputStream, BiConsumer entryConsumer, String langCode) {
        // TODO Patch code as well
//        Map<String, Map<Object, Object>> map = Maps.newHashMap();
//
//        Language.loadFromJson(inputStream, entryConsumer.andThen((key, value) ->
//            map.computeIfAbsent(langCode, k -> Maps.newHashMap())
//                .put(key, value)));
//
//        Language.loadFromJson(inputStream, entryConsumer);
    }

    @Redirect(
        method = "appendFrom(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;)V"
        )
    )
    private static void saveSeparatelyExpected(InputStream inputStream, BiConsumer entryConsumer, BiConsumer adapter_injected_2, String langCode) {
//        Map<String, Map<Object, Object>> map = Maps.newHashMap();
//
//        Language.loadFromJson(inputStream, entryConsumer.andThen((key, value) ->
//            map.computeIfAbsent(langCode, k -> Maps.newHashMap())
//                .put(key, value)), consumer); // Added consumer arg
//
//        // Added consumer arg
//        Language.loadFromJson(inputStream, entryConsumer, consumer);
    }
}
