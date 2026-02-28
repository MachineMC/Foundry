package org.machinemc.foundry.model;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * Class that automatically generates class models for types.
 *
 * @see ClassModel
 */
final class ClassModelFactory {

    private ClassModelFactory() {
        throw new UnsupportedOperationException();
    }

    static <T> ClassModel<T> mapClass(Class<T> type) {
        return mapClass(type, ClassModel.ModellingStrategy.STRUCTURE, null);
    }

    static <T> ClassModel<T> mapClass(Class<T> type, ClassModel.ModellingStrategy strategy,
                                      @Nullable ClassModel.CustomConstructor<T> customConstructor) {
        Preconditions.checkArgument(!type.isEnum() && !type.isInterface() && !type.isAnnotation()
                        && !type.isRecord(), "Type '%s' cannot be mapped as a standard class",
                type.getName());
        Preconditions.checkState(!Modifier.isAbstract(type.getModifiers()) || customConstructor != null,
                "Type '%s' is abstract and can not be mapped without custom constructor");

        ModelAttribute[] attributes = extractAttributes(type, strategy);
        validateSetters(type, attributes);

        Constructor<?> noArgs = noArgsConstructor(type);
        Preconditions.checkState(customConstructor != null || noArgs != null,
                "Class '%s' is missing a no-arguments constructor and no custom constructor " +
                        "was provided", type.getName());

        return new ClassModel<>(type, attributes,
                customConstructor != null ? customConstructor : ClassModel.NoArgsConstructor.INSTANCE);
    }

    static <T> ClassModel<T> mapInterface(Class<T> type, ClassModel.CustomConstructor<T> customConstructor) {
        Preconditions.checkArgument(type.isInterface(), "Type '%s' must be an interface",
                type.getName());
        Preconditions.checkNotNull(customConstructor, "Interface mapping requires a custom constructor");

        ModelAttribute[] attributes = extractAttributes(type, ClassModel.ModellingStrategy.EXPOSED);
        validateSetters(type, attributes);

        return new ClassModel<>(type, attributes, customConstructor);
    }

    static <T extends Record> ClassModel<T> mapRecord(Class<T> type) {
        Preconditions.checkArgument(type.isRecord(), "Type '%s' must be a record", type.getName());

        ModelAttribute[] attributes = Arrays.stream(type.getRecordComponents())
                .map(ClassModelFactory::asAttribute)
                .toArray(ModelAttribute[]::new);

        Constructor<?> constructor = allArgsConstructor(type, attributes);
        Preconditions.checkNotNull(constructor); // always present

        return new ClassModel<>(type, attributes, ClassModel.RecordConstructor.INSTANCE);
    }

    static <T extends Enum<T>> ClassModel<T> mapEnum(Class<T> type) {
        return mapEnum(type, ClassModel.ModellingStrategy.STRUCTURE, null);
    }

    static <T extends Enum<T>> ClassModel<T> mapEnum(Class<T> type, ClassModel.ModellingStrategy strategy,
                                                     @Nullable ClassModel.EnumConstructor<T> customConstructor) {
        Preconditions.checkArgument(type.isEnum(), "Type '%s' must be an enum", type.getName());

        List<ModelAttribute> attributes = new ArrayList<>(List.of(extractAttributes(type, strategy)));

        for (int i = 0; i < attributes.size(); i++) {
            ModelAttribute a = attributes.get(i);
            // we remove the setter as enums are constants, we resolve them by name
            ModelAttribute withoutSetter = new ModelAttribute(a.source(), a.name(), a.type(), a.annotatedType(),
                    new AttributeAccess(a.access().getter(), null));
            attributes.set(i, withoutSetter);
        }

        // move the built in attributes to the front
        ModelAttribute[] builtIn = new ModelAttribute[2];
        List<ModelAttribute> javaInternals = new ArrayList<>();
        for (ModelAttribute a : attributes) {
            if (a.source() != Enum.class)
                continue;
            switch (a.name()) {
                case "name" -> builtIn[0] = a;
                case "ordinal" -> builtIn[1] = a;
                default -> javaInternals.add(a);
            }
        }
        Arrays.stream(builtIn).forEach(attributes::remove);
        attributes.removeAll(javaInternals);

        ModelAttribute[] sortedAttributes = new ModelAttribute[builtIn.length + attributes.size()];
        System.arraycopy(builtIn, 0, sortedAttributes, 0, builtIn.length);
        for (int i = 0; i < attributes.size(); i++) {
            sortedAttributes[i + builtIn.length] = attributes.get(i);
        }

        return new ClassModel<>(type, sortedAttributes,
                customConstructor != null ? customConstructor : ClassModel.EnumConstructor.valueOf(type));
    }

    private static ModelAttribute[] extractAttributes(Class<?> type, ClassModel.ModellingStrategy strategy) {
        if (strategy == ClassModel.ModellingStrategy.STRUCTURE) {
            return getAllFields(type).stream()
                    .filter(ClassModelFactory::keepField)
                    .map(ClassModelFactory::asAttribute)
                    .toArray(ModelAttribute[]::new);
        } else {
            return extractExposedAttributes(type);
        }
    }

    private static ModelAttribute[] extractExposedAttributes(Class<?> type) {
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();

        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class) continue;
            if (method.isAnnotationPresent(Omit.class)) continue;
            if (method.isBridge() || method.isSynthetic()) continue;

            if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                getters.putIfAbsent(getPropertyName(method.getName(), true), method);
            } else if (method.getParameterCount() == 1) {
                setters.putIfAbsent(getPropertyName(method.getName(), false), method);
            }
        }

        List<ModelAttribute> attributes = new ArrayList<>();
        for (var entry : getters.entrySet()) {
            String name = entry.getKey();
            Method getter = entry.getValue();
            Method setter = setters.get(name);

            // verify setter parameter type matches getter return type
            if (setter != null && !setter.getParameterTypes()[0].equals(getter.getReturnType())) {
                setter = null;
            }

            AttributeAccess access = new AttributeAccess(
                    new AttributeAccess.Method(getter.getReturnType(), getter.getName(), Collections.emptyList()),
                    setter != null
                            ? new AttributeAccess.Method(setter.getReturnType(), setter.getName(),
                            List.of(setter.getParameterTypes()))
                            : null
            );

            attributes.add(new ModelAttribute(getter.getDeclaringClass(), name, getter.getReturnType(),
                    getter.getAnnotatedReturnType(), access));
        }

        return attributes.toArray(new ModelAttribute[0]);
    }

    private static String getPropertyName(String methodName, boolean isGetter) {
        if (isGetter) {
            String getter = extractFromPrefix("get", methodName);
            if (getter != null) return getter;
            getter = extractFromPrefix("is", methodName);
            if (getter != null) return getter;
        } else {
            String setter = extractFromPrefix("set", methodName);
            if (setter != null) return setter;
        }
        return methodName; // fallback to fluent naming
    }

    private static @Nullable String extractFromPrefix(String prefix, String methodName) {
        if (!methodName.startsWith(prefix) || methodName.length() <= prefix.length()
                || Character.isLowerCase(methodName.charAt(prefix.length())))
            return null;
        return Character.toLowerCase(methodName.charAt(prefix.length()))
                + methodName.substring(prefix.length() + 1);
    }

    private static void validateSetters(Class<?> type, ModelAttribute[] attributes) {
        ModelAttribute missingSetter = Arrays.stream(attributes)
                .filter(attribute -> attribute.access().setter() == null)
                .findFirst()
                .orElse(null);
        if (missingSetter != null) {
            throw new IllegalStateException("Attribute '" + missingSetter.name() + "' of class '" + type.getName()
                    + "' is missing setter");
        }
    }

    private static SequencedSet<Field> getAllFields(Class<?> type) {
        SequencedSet<Field> collected = new LinkedHashSet<>();
        List<Class<?>> classes = new LinkedList<>();
        while (type != null && type != Object.class) {
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
