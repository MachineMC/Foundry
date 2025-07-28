package org.machinemc.foundry.field;

import com.google.common.base.Preconditions;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;

// for non-static fields
public record FieldData(Class<?> source, String name, AnnotatedType type) {

    public static FieldData of(Field field) {
        return new FieldData(field.getDeclaringClass(), field.getName(), field.getAnnotatedType());
    }

    public static FieldData of(RecordComponent recordComponent) {
        return new FieldData(recordComponent.getDeclaringRecord(),
                recordComponent.getName(), recordComponent.getAnnotatedType());
    }

    public FieldData {
        Preconditions.checkNotNull(source, "Source class can not be null");
        Preconditions.checkNotNull(name, "Name can not be null");
        Preconditions.checkNotNull(type, "Type can not be null");
    }

}
