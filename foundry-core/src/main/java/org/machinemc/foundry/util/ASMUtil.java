package org.machinemc.foundry.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Utilities for ASM.
 */
public final class ASMUtil {

    private ASMUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Pushes value on the top of the stack.
     *
     * @param mv method visitor
     * @param value value to push
     */
    public static void push(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

}
