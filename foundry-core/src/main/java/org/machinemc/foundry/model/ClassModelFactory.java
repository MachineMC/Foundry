package org.machinemc.foundry.model;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;
import org.machinemc.foundry.Omit;

import java.lang.reflect.*;
import java.util.*;

/**
 * Class that automatically generated class models for types.
 *
 * @see ClassModel
 */
final class ClassModelFactory {

    private ClassModelFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Automatically creates a class model from given type.
     * <p>
     * This works for both regular classes and records.
     * <p>
     * Getters and setters (both non-chaining and chaining) respecting the JavaBeans naming or
     * fluent record naming schemes are prioritized over the direct field access.
     * <p>
     * Class member are allowed to have {@code private} access modifier.
     *
     * @param type type to generate the model for
     * @param customConstructor custom constructor for the class, if {@code null}, no args constructor is used
     * @return model for given type
     */
    static ClassModel mapAuto(Class<?> type, @Nullable ClassModel.CustomConstructor<?> customConstructor) {
        if (type.isEnum() || type.isInterface() || type.isAnnotation() || Modifier.isAbstract(type.getModifiers()))
            throw new UnsupportedOperationException("Can not automatically resolve class model for '"
                    + type.getName() + "'");

        if (type.isRecord()) {
            Preconditions.checkState(customConstructor == null, "Record class models can not have "
                    + "custom constructors");
            return ofRecord(type);
        }

        ModelAttribute[] attributes = getAllFields(type).stream()
                .filter(ClassModelFactory::keepField)
                .map(ClassModelFactory::asAttribute)
                .toArray(ModelAttribute[]::new);
        ModelAttribute missingSetter = Arrays.stream(attributes)
                .filter(attribute -> attribute.access().setter() == null)
                .findFirst()
                .orElse(null);
        if (missingSetter != null)
            throw new IllegalStateException("Attribute '" + missingSetter.name() + "' of class '" + type.getName()
                    + "' is missing setter");
        // We must use no arguments constructor, as fields in classes are in no guaranteed order. We can not
        // determine the correct parameters order in all args constructor. This does not apply to records.
        Constructor<?> noArgs = noArgsConstructor(type);
        Preconditions.checkState(noArgs != null, "Class '" + type.getName() + "' is missing "
                + "no arguments constructor");
        return new ClassModel(attributes,
                customConstructor != null ? customConstructor : ClassModel.NoArgsConstructor.INSTANCE);
    }

    private static ClassModel ofRecord(Class<?> type) {
        ModelAttribute[] attributes = Arrays.stream(type.getRecordComponents())
                .map(ClassModelFactory::asAttribute)
                .toArray(ModelAttribute[]::new);
        Constructor<?> constructor = allArgsConstructor(type, attributes);
        Preconditions.checkNotNull(constructor); // is always present on records
        return new ClassModel(attributes, ClassModel.RecordConstructor.INSTANCE);
    }

    private static SequencedSet<Field> getAllFields(Class<?> type) {
        SequencedSet<Field> collected = new LinkedHashSet<>();
        List<Class<?>> classes = new LinkedList<>();
        while (type.getSuperclass() != null) {
            classes.addFirst(type); // fields of the parent classes are included first
            type = type.getSuperclass();
        }
        classes.stream()
                .map(Class::getDeclaredFields)
                .map(List::of)
                .forEach(collected::addAll);
        return collected;
    }

    private static boolean keepField(Field field) {
        return !Modifier.isTransient(field.getModifiers())
                && !Modifier.isStatic(field.getModifiers())
                && !field.isAnnotationPresent(Omit.class);
    }

    private static ModelAttribute asAttribute(Field field) {
        return new ModelAttribute(field.getDeclaringClass(), field.getName(), field.getType(),
                field.getAnnotatedType(), createAccess(field));
    }

    private static ModelAttribute asAttribute(RecordComponent component) {
        return new ModelAttribute(component.getDeclaringRecord(), component.getName(), component.getType(),
                component.getAnnotatedType(), new AttributeAccess(
                new AttributeAccess.Method(component.getType(), component.getName(), Collections.emptyList()),
                null));
    }

    private static AttributeAccess createAccess(Field field) {
        Class<?> declaring = field.getDeclaringClass();
        Method getter = findFieldAccessMethod(field, declaring, false);
        Method setter = findFieldAccessMethod(field, declaring, true);
        if (getter == null) {
            var getterNames = getGetterNames(field);
            getter = Arrays.stream(declaring.getDeclaredMethods())
                    .filter(method -> getterNames.contains(method.getName()))
                    .filter(method -> isGetterMethod(field, method))
                    .findFirst().orElse(null);
        }
        if (setter == null) {
            var setterNames = getSetterNames(field);
            setter = Arrays.stream(declaring.getDeclaredMethods())
                    .filter(method -> setterNames.contains(method.getName()))
                    .filter(method -> isSetterMethod(field, method))
                    .findFirst().orElse(null);
        }

        AttributeAccess.Get get = getter != null
                ? new AttributeAccess.Method(getter.getReturnType(), getter.getName(),
                List.of(getter.getParameterTypes()))
                : new AttributeAccess.Direct(field.getName());
        AttributeAccess.Set set;
        if (setter != null) {
            set = new AttributeAccess.Method(setter.getReturnType(), setter.getName(),
                    List.of(setter.getParameterTypes()));
        } else if (!Modifier.isFinal(field.getModifiers())) {
            set = new AttributeAccess.Direct(field.getName());
        } else {
            set = null;
        }

        return new AttributeAccess(get, set);
    }

    private static @Nullable Method findFieldAccessMethod(Field field, Class<?> type, boolean setter) {
        var accessMethods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(FieldAccess.class))
                .filter(method -> method.getAnnotation(FieldAccess.class).value().equals(field.getName()))
                .filter(method -> setter ? isSetterMethod(field, method) : isGetterMethod(field, method))
                .toList();
        Preconditions.checkState(accessMethods.size() <= 1, "Found multiple valid field " +
                "access methods for field '" + field.getName() + "' of class '" + type.getName() + "'.");
        return accessMethods.isEmpty() ? null : accessMethods.getFirst();
    }

    private static boolean isGetterMethod(Field field, Method method) {
        return !Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                && field.getType().equals(method.getReturnType());
    }

    private static boolean isSetterMethod(Field field, Method method) {
        return !Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1
                && method.getParameterTypes()[0].equals(field.getType());
    }

    private static List<String> getGetterNames(Field field) {
        boolean stateful = field.getType() == boolean.class; // boolean primitives use 'is' prefix
        String name = field.getName();
        return List.of(name,
                (stateful ? "is" : "get") + name.substring(0, 1).toUpperCase() + name.substring(1));
    }

    private static List<String> getSetterNames(Field field) {
        String name = field.getName();
        return List.of(name, "set" + name.substring(0, 1).toUpperCase() + name.substring(1));
    }

    private static @Nullable Constructor<?> noArgsConstructor(Class<?> type) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException _) {
            return null;
        }
    }

    private static @Nullable Constructor<?> allArgsConstructor(Class<?> type, ModelAttribute[] attributes) {
        try {
            return type.getDeclaredConstructor(Arrays.stream(attributes).map(ModelAttribute::type).toArray(Class[]::new));
        } catch (NoSuchMethodException _) {
            return null;
        }
    }

}
