package org.machinemc.foundry.util;

import com.google.common.base.Preconditions;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

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
            mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    /**
     * Swaps the top value on the stack with the value below it,
     * accounting for Category 2 types (long/double).
     *
     * @param mv method visitor
     * @param topSize size of the top element (1 or 2)
     * @param bottomSize size of the element below the top (1 or 2)
     */
    public static void swap(MethodVisitor mv, int topSize, int bottomSize) {
        Preconditions.checkArgument(1 <= topSize && topSize <= 2);
        Preconditions.checkArgument(1 <= bottomSize && bottomSize <= 2);
        if (topSize == 1) {
            if (bottomSize == 1) {
                mv.visitInsn(SWAP);
            } else {
                mv.visitInsn(DUP_X2);
                mv.visitInsn(POP);
            }
        } else { // topSize == 2
            if (bottomSize == 1) {
                mv.visitInsn(DUP2_X1);
                mv.visitInsn(POP2);
            } else {
                mv.visitInsn(DUP2_X2);
                mv.visitInsn(POP2);
            }
        }
    }

    /**
     * Pops the value on the top of the stack accounting for Category 2 types (long/double).
     *
     * @param mv method visitor
     * @param size size of the element
     */
    public static void pop(MethodVisitor mv, int size) {
        Preconditions.checkArgument(1 <= size && size <= 2);
        if (size == 1)
            mv.visitInsn(POP);
        else
            mv.visitInsn(POP2);
    }

}
