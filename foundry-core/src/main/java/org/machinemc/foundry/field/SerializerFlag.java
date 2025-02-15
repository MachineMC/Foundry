package org.machinemc.foundry.field;

public interface SerializerFlag<F, T> extends FieldFlag {

    T serialize(F value);

    F deserialize(T value);

}
