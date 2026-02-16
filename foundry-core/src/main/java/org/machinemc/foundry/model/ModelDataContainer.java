package org.machinemc.foundry.model;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * Container of extracted data from an object using a {@link ClassModel}.
 * <p>
 * This class is intentionally unsafe to keep it optimized for fast
 * writes and reads, and is for internal use only.
 */
@ApiStatus.Internal
public final class ModelDataContainer {

    /**
     * Factory for producing empty model data containers for given class model.
     */
    public static final class Factory implements Supplier<ModelDataContainer> {

        /**
         * Creates new model data container factory for given class model.
         *
         * @param model class model
         * @return model data container factory
         */
        public static Factory of(ClassModel model) {
            return new Factory(model);
        }

        private final int booleans, chars, bytes, shorts, ints, longs, floats, doubles, objects;

        private Factory(ClassModel model) {
            ModelAttribute[] attributes = model.getAttributes();
            int booleans = 0, chars = 0, bytes = 0, shorts = 0, ints = 0, longs = 0, floats = 0, doubles = 0, objects = 0;
            for (ModelAttribute attribute : attributes) {
                if (!attribute.primitive()) {
                    objects++;
                    continue;
                }
                Class<?> type = attribute.type();
                if (type == boolean.class) booleans++;
                else if (type == char.class) chars++;
                else if (type == byte.class) bytes++;
                else if (type == short.class) shorts++;
                else if (type == int.class) ints++;
                else if (type == long.class) longs++;
                else if (type == float.class) floats++;
                else if (type == double.class) doubles++;
                else throw new RuntimeException("Unexpected type: '" + type.getName() + "'");
            }
            this.booleans = booleans;
            this.chars = chars;
            this.bytes = bytes;
            this.shorts = shorts;
            this.ints = ints;
            this.longs = longs;
            this.floats = floats;
            this.doubles = doubles;
            this.objects = objects;
        }

        @Override
        public ModelDataContainer get() {
            return new ModelDataContainer(booleans, chars, bytes, shorts, ints, longs, floats, doubles, objects);
        }

    }

    private final boolean[] booleans;
    private final char[] chars;
    private final byte[] bytes;
    private final short[] shorts;
    private final int[] ints;
    private final long[] longs;
    private final float[] floats;
    private final double[] doubles;
    private final Object[] objects;

    private int booleansRead = 0, booleansWrite = 0;
    private int charsRead = 0, charsWrite = 0;
    private int bytesRead = 0, bytesWrite = 0;
    private int shortsRead = 0, shortsWrite = 0;
    private int intsRead = 0, intsWrite = 0;
    private int longsRead = 0, longsWrite = 0;
    private int floatsRead = 0, floatsWrite = 0;
    private int doublesRead = 0, doublesWrite = 0;
    private int objectsRead = 0, objectsWrite = 0;

    private ModelDataContainer(int booleans, int chars, int bytes, int shorts, int ints, int longs,
                               int floats, int doubles, int objects) {
        this.booleans = new boolean[booleans];
        this.chars = new char[chars];
        this.bytes = new byte[bytes];
        this.shorts = new short[shorts];
        this.ints = new int[ints];
        this.longs = new long[longs];
        this.floats = new float[floats];
        this.doubles = new double[doubles];
        this.objects = new Object[objects];
    }

    public boolean readBool() {
        return booleans[booleansRead++];
    }

    public char readChar() {
        return chars[charsRead++];
    }

    public byte readByte() {
        return bytes[bytesRead++];
    }

    public short readShort() {
        return shorts[shortsRead++];
    }

    public int readInt() {
        return ints[intsRead++];
    }

    public long readLong() {
        return longs[longsRead++];
    }

    public float readFloat() {
        return floats[floatsRead++];
    }

    public double readDouble() {
        return doubles[doublesRead++];
    }

    public Object readObject() {
        return objects[objectsRead++];
    }

    public void writeBool(boolean value) {
        booleans[booleansWrite++] = value;
    }

    public void writeChar(char value) {
        chars[charsWrite++] = value;
    }

    public void writeByte(byte value) {
        bytes[bytesWrite++] = value;
    }

    public void writeShort(short value) {
        shorts[shortsWrite++] = value;
    }

    public void writeInt(int value) {
        ints[intsWrite++] = value;
    }

    public void writeLong(long value) {
        longs[longsWrite++] = value;
    }

    public void writeFloat(float value) {
        floats[floatsWrite++] = value;
    }

    public void writeDouble(double value) {
        doubles[doublesWrite++] = value;
    }

    public void writeObject(Object value) {
        objects[objectsWrite++] = value;
    }

    /**
     * Resets all the reader indices.
     */
    public void resetReader() {
        booleansRead = 0;
        charsRead = 0;
        bytesRead = 0;
        shortsRead = 0;
        intsRead = 0;
        longsRead = 0;
        floatsRead = 0;
        doublesRead = 0;
    }

    /**
     * Resets all the writer indices.
     */
    public void resetWriter() {
        booleansWrite = 0;
        charsWrite = 0;
        bytesWrite = 0;
        shortsWrite = 0;
        intsWrite = 0;
        longsWrite = 0;
        floatsWrite = 0;
        doublesWrite = 0;
    }

}
