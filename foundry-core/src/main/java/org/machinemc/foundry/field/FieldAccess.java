package org.machinemc.foundry.field;

import com.google.common.base.Preconditions;

public record FieldAccess<S, T>(Getter<S, T> getter, Setter<S, T> setter) {

    public FieldAccess {
        Preconditions.checkNotNull(getter, "Getter can not be null");
        Preconditions.checkNotNull(setter, "Setter can not be null");
    }

    public interface Getter<S, T> {
        T get(S instance);
    }

    public interface Setter<S, T> {
        void set(S instance, T value);
    }

}
