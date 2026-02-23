package org.machinemc.foundry.model;

import org.objectweb.asm.Type;

import java.util.Arrays;

/**
 * Groups data for byte code generation of {@link ObjectFactory} together.
 */
enum ContainerTypeMapping {

    /**
     * Primitive boolean mapping.
     */
    BOOLEAN(boolean.class, Type.BOOLEAN_TYPE, "readBool", "writeBool",
            AttributeAccess.CustomGetter.Bool.class, AttributeAccess.CustomSetter.Bool.class),

    /**
     * Primitive char mapping.
     */
    CHAR(char.class, Type.CHAR_TYPE, "readChar", "writeChar",
            AttributeAccess.CustomGetter.Char.class, AttributeAccess.CustomSetter.Char.class),

    /**
     * Primitive byte mapping.
     */
    BYTE(byte.class, Type.BYTE_TYPE, "readByte", "writeByte",
            AttributeAccess.CustomGetter.Byte.class, AttributeAccess.CustomSetter.Byte.class),

    /**
     * Primitive short mapping.
     */
    SHORT(short.class, Type.SHORT_TYPE, "readShort", "writeShort",
            AttributeAccess.CustomGetter.Short.class, AttributeAccess.CustomSetter.Short.class),

    /**
     * Primitive int mapping.
     */
    INT(int.class, Type.INT_TYPE, "readInt", "writeInt",
            AttributeAccess.CustomGetter.Int.class, AttributeAccess.CustomSetter.Int.class),

    /**
     * Primitive long mapping.
     */
    LONG(long.class, Type.LONG_TYPE, "readLong", "writeLong",
            AttributeAccess.CustomGetter.Long.class, AttributeAccess.CustomSetter.Long.class),

    /**
     * Primitive float mapping.
     */
    FLOAT(float.class, Type.FLOAT_TYPE, "readFloat", "writeFloat",
            AttributeAccess.CustomGetter.Float.class, AttributeAccess.CustomSetter.Float.class),

    /**
     * Primitive double mapping.
     */
    DOUBLE(double.class, Type.DOUBLE_TYPE, "readDouble", "writeDouble",
            AttributeAccess.CustomGetter.Double.class, AttributeAccess.CustomSetter.Double.class),

    /**
     * Object mapping.
     */
    OBJECT(Object.class, Type.getType(Object.class), "readObject", "writeObject",
            AttributeAccess.CustomGetter.Object.class, AttributeAccess.CustomSetter.Object.class);

    final Class<?> javaType;
    final Type asmType;
    final String readMethod;
    final String writeMethod;
    final Class<?> customGetter;
    final Class<?> customSetter;

    ContainerTypeMapping(Class<?> javaType, Type asmType, String readMethod, String writeMethod,
                         Class<?> customGetter, Class<?> customSetter) {
        this.javaType = javaType;
        this.asmType = asmType;
        this.readMethod = readMethod;
        this.writeMethod = writeMethod;
        this.customGetter = customGetter;
        this.customSetter = customSetter;
    }

    /**
     * Returns the mapping for provided type.
     *
     * @param type type
     * @return mapping for given type
     */
    static ContainerTypeMapping of(Class<?> type) {
        if (type.isPrimitive()) {
            return Arrays.stream(values())
                    .filter(m -> m.javaType == type)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown primitive type: " + type));
        }
        return OBJECT;
    }

}
