package org.machinemc.foundry.field;

import com.google.common.base.Preconditions;
import org.machinemc.foundry.util.TypeUtils;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;

public record FieldData<S, T>(Class<S> source, String name, AnnotatedType type) {

    @SuppressWarnings("unchecked")
    public static <S, T> FieldData<S, T> of(Field field) {
        return (FieldData<S, T>) new FieldData<>(field.getDeclaringClass(), field.getName(), field.getAnnotatedType());
    }

    @SuppressWarnings("unchecked")
    public static <S, T> FieldData<S, T> of(RecordComponent recordComponent) {
        return (FieldData<S, T>) new FieldData<>(recordComponent.getDeclaringRecord(),
                recordComponent.getName(), recordComponent.getAnnotatedType());
    }

    public FieldData(Class<S> source, String name, Class<T> type) {
        this(source, name, TypeUtils.getAnnotatedType(type));
    }

    public FieldData {
        Preconditions.checkNotNull(source, "Source class can not be null");
        Preconditions.checkNotNull(name, "Name can not be null");
        Preconditions.checkNotNull(type, "Type can not be null");
    }

    @SuppressWarnings("unchecked")
    public Class<T> typeClass() {
        return (Class<T>) TypeUtils.getRawType(type);
    }

}
