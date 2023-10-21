/*
 * Mini - an ASM-based class transformer reminiscent of MalisisCore and Mixin
 * 
 * The MIT License
 *
 * Copyright (c) 2017-2021 Una Thompson (unascribed) and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.su5ed.sinytra.adapter.patch.util;

import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// Source: https://git.sleeping.town/Nil/NilLoader/src/commit/d66d783a5f7ac72a3688594335b3285fcb975b07/src/main/java/nilloader/api/lib/mini/PatchContext.java
public class InsnComparator {

    public static boolean instructionsEqual(AbstractInsnNode a, AbstractInsnNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        if (a.getOpcode() != b.getOpcode()) return false;

        if (a instanceof FieldInsnNode) {
            FieldInsnNode fa = (FieldInsnNode) a;
            FieldInsnNode fb = (FieldInsnNode) b;
            return Objects.equals(fa.owner, fb.owner) &&
                Objects.equals(fa.name, fb.name) &&
                Objects.equals(fa.desc, fb.desc);
        } else if (a instanceof IincInsnNode) {
            IincInsnNode ia = (IincInsnNode) a;
            IincInsnNode ib = (IincInsnNode) b;
            return ia.var == ib.var && ia.incr == ib.incr;
        } else if (a instanceof InsnNode) {
            return true;
        } else if (a instanceof IntInsnNode) {
            IntInsnNode ia = (IntInsnNode) a;
            IntInsnNode ib = (IntInsnNode) b;
            return ia.operand == ib.operand;
        } else if (a instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode ia = (InvokeDynamicInsnNode) a;
            InvokeDynamicInsnNode ib = (InvokeDynamicInsnNode) b;
            return Objects.equals(ia.bsm, ib.bsm) &&
                Arrays.equals(ia.bsmArgs, ib.bsmArgs) &&
                Objects.equals(ia.name, ib.name) &&
                Objects.equals(ia.desc, ib.desc);
        } else if (a instanceof JumpInsnNode || a instanceof LabelNode || a instanceof FrameNode) {
            // no good way to compare label equality
            return true;
        } else if (a instanceof LdcInsnNode) {
            LdcInsnNode la = (LdcInsnNode) a;
            LdcInsnNode lb = (LdcInsnNode) b;
            return Objects.equals(la.cst, lb.cst);
        } else if (a instanceof LineNumberNode) {
            LineNumberNode la = (LineNumberNode) a;
            LineNumberNode lb = (LineNumberNode) b;
            return la.line == lb.line && instructionsEqual(la.start, lb.start);
        } else if (a instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode la = (LookupSwitchInsnNode) a;
            LookupSwitchInsnNode lb = (LookupSwitchInsnNode) b;
            return instructionsEqual(la.dflt, lb.dflt) &&
                Objects.equals(la.keys, lb.keys) &&
                instructionListsEqual(la.labels, lb.labels);
        } else if (a instanceof MethodInsnNode) {
            MethodInsnNode ma = (MethodInsnNode) a;
            MethodInsnNode mb = (MethodInsnNode) b;
            return Objects.equals(ma.owner, mb.owner) &&
                Objects.equals(ma.name, mb.name) &&
                Objects.equals(ma.desc, mb.desc) &&
                ma.itf == mb.itf;
        } else if (a instanceof MultiANewArrayInsnNode) {
            MultiANewArrayInsnNode ma = (MultiANewArrayInsnNode) a;
            MultiANewArrayInsnNode mb = (MultiANewArrayInsnNode) b;
            return Objects.equals(ma.desc, mb.desc) && ma.dims == mb.dims;
        } else if (a instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode ta = (TableSwitchInsnNode) a;
            TableSwitchInsnNode tb = (TableSwitchInsnNode) b;
            return ta.min == tb.min &&
                ta.max == tb.max &&
                instructionsEqual(ta.dflt, tb.dflt) &&
                instructionListsEqual(ta.labels, tb.labels);
        } else if (a instanceof TypeInsnNode) {
            TypeInsnNode ta = (TypeInsnNode) a;
            TypeInsnNode tb = (TypeInsnNode) b;
            return Objects.equals(ta.desc, tb.desc);
        } else if (a instanceof VarInsnNode) {
            VarInsnNode va = (VarInsnNode) a;
            VarInsnNode vb = (VarInsnNode) b;
            return va.var == vb.var;
        }
        throw new IllegalArgumentException("Unknown insn type " + a.getClass().getName());
    }

    private static boolean instructionListsEqual(List<? extends AbstractInsnNode> a, List<? extends AbstractInsnNode> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!instructionsEqual(a.get(i), b.get(i))) return false;
        }
        return true;
    }
}
