package org.machinemc.foundry.field;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Collectors;

public class Field<S extends AnnotatedElement> {

    private final S source;
    private final Class<?> type;
    private final @Nullable SerializerFlag<?, ?> serializerFlag;
    private final Set<PredicateFlag<?>> predicateFlags;

    private Field(S source, Class<?> type, @Nullable SerializerFlag<?, ?> serializerFlag, Set<PredicateFlag<?>> predicateFlags) {
        this.source = source;
        this.type = type;
        this.serializerFlag = serializerFlag;
        this.predicateFlags = predicateFlags;
    }

    public S source() {
        return source;
    }

    public Class<?> type() {
        return type;
    }

    public SerializerFlag<?, ?> serializerFlag() {
        return serializerFlag;
    }

    public @UnmodifiableView Set<PredicateFlag<?>> predicateFlags() {
        return Collections.unmodifiableSet(predicateFlags);
    }

    public <F, T> T applyTo(F value) {
        //noinspection unchecked
        T serialized = serializerFlag != null ? ((SerializerFlag<F, T>) serializerFlag).serialize(value) : (T) value;
        for (PredicateFlag<?> predicateFlag : predicateFlags) {
            //noinspection unchecked
            PredicateFlag.Result result = ((PredicateFlag<T>) predicateFlag).test(serialized);
            if (!result.result())
                throw new IllegalArgumentException(result.message());
        }
        return serialized;
    }

    @Contract("_ -> new")
    public static Field<java.lang.reflect.Field> from(java.lang.reflect.Field field) throws IllegalArgumentException {
        return new Field<>(field, field.getType(), getSerializerFlag(field), getFieldFlags(field));
    }

    @Contract("_ -> new")
    public static Field<RecordComponent> from(RecordComponent component) {
        return new Field<>(component, component.getType(), getSerializerFlag(component), getFieldFlags(component));
    }

    private static @Nullable SerializerFlag<?, ?> getSerializerFlag(AnnotatedElement element) throws IllegalArgumentException {
        //noinspection unchecked
        return Arrays.stream(element.getDeclaredAnnotations())
                .map(Annotation::annotationType)
                .filter(annotationType -> annotationType.isAnnotationPresent(Handler.class))
                .map(annotationType -> annotationType.getAnnotation(Handler.class).value())
                .filter(SerializerFlag.class::isAssignableFrom)
                .map(serializerFlagClass -> (Class<? extends SerializerFlag<?, ?>>) serializerFlagClass)
                .reduce((a, b) -> {
                    throw new IllegalArgumentException("Multiple serializer flags found on " + element);
                })
                .map(Field::newInstance)
                .orElse(null);
    }

    private static Set<PredicateFlag<?>> getFieldFlags(AnnotatedElement element) throws IllegalArgumentException {
        //noinspection unchecked
        return Arrays.stream(element.getDeclaredAnnotations())
                .map(Annotation::annotationType)
                .filter(annotationType -> annotationType.isAnnotationPresent(Handler.class))
                .map(annotationType -> annotationType.getAnnotation(Handler.class).value())
                .filter(PredicateFlag.class::isAssignableFrom)
                .map(valueFlagClass -> (Class<? extends PredicateFlag<?>>) valueFlagClass)
                .map(Field::newInstance)
                .collect(Collectors.toSet());
    }

    private static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No default constructor found for the handler " + clazz);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
