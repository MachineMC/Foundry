package org.machinemc.foundry.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Utility class for operations involving Java {@link Type}s.
 */
public final class TypeUtils {

    private static final BiMap<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = HashBiMap.create(9);

    static {
        PRIMITIVE_WRAPPERS.put(boolean.class, Boolean.class);
        PRIMITIVE_WRAPPERS.put(byte.class, Byte.class);
        PRIMITIVE_WRAPPERS.put(char.class, Character.class);
        PRIMITIVE_WRAPPERS.put(double.class, Double.class);
        PRIMITIVE_WRAPPERS.put(float.class, Float.class);
        PRIMITIVE_WRAPPERS.put(int.class, Integer.class);
        PRIMITIVE_WRAPPERS.put(long.class, Long.class);
        PRIMITIVE_WRAPPERS.put(short.class, Short.class);
        PRIMITIVE_WRAPPERS.put(void.class, Void.class);
    }

    private TypeUtils() {
    }

    /**
     * Checks if a variable of an {@code expected} type can be assigned a value of an
     * {@code actual} type, following Java's assignment and subtyping rules.
     *
     * @param expected the type of the variable (the target, or supertype)
     * @param actual the type of the value being assigned (the source, or subtype)
     * @return true if the types are assignment-compatible, else false
     */
    public static boolean isCompatible(Type expected, Type actual) {
        Preconditions.checkNotNull(expected, "Expected type cannot be null");
        Preconditions.checkNotNull(actual, "Actual type cannot be null");
        return isCompatible(expected, actual, new HashMap<>());
    }

    private static boolean isCompatible(Type expected, Type actual, Map<TypeVariable<?>, Type> resolvedVars) {
        if (expected.equals(actual)) {
            return true;
        }

        return switch (expected) {
            case Class<?> c -> isCompatibleWithClass(c, actual);
            case ParameterizedType pt -> isCompatibleWithParameterizedType(pt, actual, resolvedVars);
            case GenericArrayType gat -> isCompatibleWithGenericArrayType(gat, actual, resolvedVars);
            case WildcardType wt -> isCompatibleWithWildcardType(wt, actual, resolvedVars);
            case TypeVariable<?> tv -> isCompatibleWithTypeVariable(tv, actual, resolvedVars);
            default -> false; // should not be reached
        };
    }

    private static boolean isCompatibleWithClass(Class<?> expected, Type actual) {
        Class<?> actualClass = getRawType(actual);
        if (actualClass == null) return false;
        if (expected.isPrimitive()) expected = PRIMITIVE_WRAPPERS.get(expected);
        if (actualClass.isPrimitive()) actualClass = PRIMITIVE_WRAPPERS.get(actualClass);
        assert expected != null;
        assert actualClass != null;
        return expected.isAssignableFrom(actualClass);
    }

    private static boolean isCompatibleWithParameterizedType(ParameterizedType expected, Type actual, Map<TypeVariable<?>, Type> resolvedVars) {
        Class<?> expectedRaw = getRawType(expected);
        Class<?> actualRaw = getRawType(actual);

        if (expectedRaw == null || actualRaw == null || !expectedRaw.isAssignableFrom(actualRaw))
            return false;

        if (actual instanceof Class<?>)
            return true;

        if (!(actual instanceof ParameterizedType actualParam))
            return false;

        Type[] expectedArgs = expected.getActualTypeArguments();
        Type[] actualArgs = actualParam.getActualTypeArguments();

        if (expectedArgs.length != actualArgs.length)
            return false;

        for (int i = 0; i < expectedArgs.length; i++) {
            if (!isCompatible(expectedArgs[i], actualArgs[i], resolvedVars)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCompatibleWithGenericArrayType(GenericArrayType expected, Type actual, Map<TypeVariable<?>, Type> resolvedVars) {
        Type expectedComponent = expected.getGenericComponentType();
        Type actualComponent = getArrayComponentType(actual);
        return actualComponent != null && isCompatible(expectedComponent, actualComponent, resolvedVars);
    }

    private static boolean isCompatibleWithWildcardType(WildcardType expected, Type actual, Map<TypeVariable<?>, Type> resolvedVars) {
        for (Type upperBound : expected.getUpperBounds()) {
            if (isCompatible(upperBound, actual, resolvedVars)) continue;
            return false;
        }

        for (Type lowerBound : expected.getLowerBounds()) {
            if (isCompatible(actual, lowerBound, resolvedVars)) continue;
            return false;
        }

        return true;
    }

    private static boolean isCompatibleWithTypeVariable(TypeVariable<?> expected, Type actual, Map<TypeVariable<?>, Type> resolvedVars) {
        if (resolvedVars.containsKey(expected))
            return isCompatible(resolvedVars.get(expected), actual, resolvedVars);

        for (Type bound : expected.getBounds()) {
            if (isCompatible(bound, actual, resolvedVars)) continue;
            return false;
        }

        resolvedVars.put(expected, actual);
        return true;
    }

    /**
     * Gets the raw {@link Class} representation of a {@link Type}.
     *
     * @param annotatedType the annotated type to inspect
     * @return the raw class or {@code null} if it cannot be determined
     */
    public static @Nullable Class<?> getRawType(AnnotatedType annotatedType) {
        Preconditions.checkNotNull(annotatedType, "Type can not be null");
        return getRawType(annotatedType.getType());
    }

    /**
     * Gets the raw {@link Class} representation of a {@link Type}.
     *
     * @param type the type to inspect
     * @return the raw class, or {@code null} if it cannot be determined
     */
    public static @Nullable Class<?> getRawType(Type type) {
        Preconditions.checkNotNull(type, "Type can not be null");
        return switch (type) {
            case Class<?> c -> c;
            case ParameterizedType pt -> getRawType(pt.getRawType());
            case GenericArrayType gat -> {
                Class<?> componentType = getRawType(gat.getGenericComponentType());
                yield componentType != null ? Array.newInstance(componentType, 0).getClass() : null;
            }
            case TypeVariable<?> tv -> {
                if (tv.getBounds().length != 1) yield null;
                yield getRawType(tv.getBounds()[0]);
            }
            case WildcardType wt -> {
                if (wt.getUpperBounds().length != 1) yield null;
                yield getRawType(wt.getUpperBounds()[0]);
            }
            default -> null;
        };
    }

    /**
     * Creates {@link AnnotatedType} from the given {@link Type}.
     * <p>
     * This AnnotatedType has no annotations present.
     *
     * @param type type
     * @return annotated type wrapping given type with no annotations
     */
    public static AnnotatedType getAnnotatedType(Type type) {
        return AnnotatedTypeFactory.from(type);
    }

    private static @Nullable Type getArrayComponentType(Type type) {
        Preconditions.checkNotNull(type, "Type can not be null");
        if (type instanceof Class<?> clazz)
            return clazz.isArray() ? clazz.getComponentType() : null;
        if (type instanceof GenericArrayType gat)
            return gat.getGenericComponentType();
        return null;
    }

    /**
     * Calculates the shortest distance in the inheritance hierarchy from a subtype to a supertype.
     * Distance to self is 0, to direct superclass/interface is 1, and so on.
     *
     * @param subType the starting class (subtype)
     * @param superType the target class (supertype)
     * @return the inheritance distance, or -1 if not related
     */
    public static int getDistance(Class<?> subType, Class<?> superType) {
        if (subType == null || superType == null) return -1;

        // BFS to find the shortest path in the unweighted inheritance graph
        Queue<Class<?>> queue = new ArrayDeque<>();
        Map<Class<?>, Integer> distances = new HashMap<>();

        queue.add(subType);
        distances.put(subType, 0);

        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            int distance = distances.get(current);

            if (current.equals(superType))
                return distance;

            Class<?> parent = current.getSuperclass();
            if (parent != null && !distances.containsKey(parent)) {
                distances.put(parent, distance + 1);
                queue.add(parent);
            }

            for (Class<?> interfaceType : current.getInterfaces()) {
                if (distances.containsKey(interfaceType)) continue;
                distances.put(interfaceType, distance + 1);
                queue.add(interfaceType);
            }
        }

        return -1;
    }

}
