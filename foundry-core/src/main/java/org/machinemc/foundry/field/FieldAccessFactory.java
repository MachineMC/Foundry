package org.machinemc.foundry.field;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class FieldAccessFactory {

    public static <S, T> FieldAccess<S, T> access(FieldData<S, T> data) {
        return new FieldAccess<>(getter(data), setter(data));
    }

    public static <S, T> FieldAccess.Getter<S, T> getter(FieldData<S, T> data) {
        return null;
    }

    public static <S, T> FieldAccess.Setter<S, T> setter(FieldData<S, T> data) {
        return null;
    }

    private static @Unmodifiable List<String> getGetterNames(FieldData<?, ?> data) {
        boolean stateful = data.typeClass() == boolean.class || data.typeClass() == Boolean.class;
        return List.of(
                data.name(),
                (stateful ? "is" : "get")
                        + data.name().substring(0, 1).toUpperCase()
                        + data.name().substring(1)
        );
    }

    private static @Unmodifiable List<String> getSetterNames(FieldData<?, ?> data) {
        return List.of(
                data.name(),
                "set" + data.name().substring(0, 1).toUpperCase() + data.name().substring(1)
        );
    }

}
