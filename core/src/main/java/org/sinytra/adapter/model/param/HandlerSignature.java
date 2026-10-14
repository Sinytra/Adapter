package org.sinytra.adapter.model.param;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;

import java.util.List;

public record HandlerSignature(List<Slot> slots, ValueSource returns) {
    public enum ParamSource {
        /// All arguments of the target method
        TARGET_ARGS,
        /// CI/CIR
        CALLBACK_INFO,
        /// The receiver and arguments of the call the @At selects
        CALL_OPERANDS,
        /// MixinExtras Operation
        OPERATION,
        /// A single value the injector modifies
        VALUE,
        /// A subset of the target method's arguments
        CAPTURED_TARGET_ARGS,
        /// Kept unchanged, such as captured locals and sugar
        KEEP
    }

    public enum ValueSource {
        VOID,
        /// The return type of the call the `@At` selects
        CALL_RETURN,
        /// The return type of the target method
        TARGET_RETURN,
        /// An argument of the call the `@At` selects, chosen by the injector
        CALL_ARG,
        /// A local variable of the target method, chosen by the injector
        VARIABLE,
        /// Whether the wrapped instruction runs
        CONDITION,
        /// The constant the `@Constant` selects
        CONSTANT
    }

    public record Slot(ParamGroup group, ParamSource source) {
    }

    public static HandlerSignature of(ValueSource returns, Slot... slots) {
        return new HandlerSignature(List.of(slots), returns);
    }

    public static Slot slot(ParamGroup group, ParamSource source) {
        return new Slot(group, source);
    }

    public List<ParamGroup> groups() {
        return this.slots.stream().map(Slot::group).toList();
    }

    public MethodParameters parse(MethodNode handler) {
        return MethodParameters.create(handler, groups());
    }
}
