package org.machinemc.foundry.util;

import com.google.common.base.Preconditions;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;

/**
 * A factory for creating {@link AnnotatedType} instances from standard {@link Type} instances,
 * correctly mapping the Type hierarchy to the AnnotatedType hierarchy.
 */
public final class AnnotatedTypeFactory {

    /**
     * Creates the {@link AnnotatedType} from the {@link Type} instance.
     *
     * @param type type
     * @return annotated type
     */
    public static AnnotatedType from(Type type) {
        return create(type, null);
    }

    private static AnnotatedType create(Type type, AnnotatedType owner) {
        Preconditions.checkNotNull(type, "Type can not be null");
        return switch (type) {
            case Class<?> c -> {
                if (c.isArray()) yield new AnnotatedArrayTypeImpl(c, owner);
                yield new AnnotatedTypeImpl(c, owner);
            }
            case ParameterizedType pt -> new AnnotatedParameterizedTypeImpl(pt, owner);
            case GenericArrayType gat -> new AnnotatedArrayTypeImpl(gat, owner);
            case TypeVariable<?> tv -> new AnnotatedTypeVariableImpl(tv, owner);
            case WildcardType wt -> new AnnotatedWildcardTypeImpl(wt, owner);
            default -> new AnnotatedTypeImpl(type, owner);
        };
    }

    private static abstract class AbstractAnnotatedType implements AnnotatedType {

        protected final Type type;
        protected final AnnotatedType ownerType;

        protected AbstractAnnotatedType(Type type, AnnotatedType owner) {
            this.type = type;
            this.ownerType = owner;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public AnnotatedType getAnnotatedOwnerType() {
            return ownerType;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }

    }

    private static final class AnnotatedTypeImpl extends AbstractAnnotatedType {

        private AnnotatedTypeImpl(Type type, AnnotatedType owner) {
            super(type, owner);
        }

    }

    private static final class AnnotatedParameterizedTypeImpl extends AbstractAnnotatedType implements AnnotatedParameterizedType {

        private final AnnotatedType[] typeArgs;

        private AnnotatedParameterizedTypeImpl(ParameterizedType type, AnnotatedType owner) {
            super(type, owner);
            this.typeArgs = Arrays.stream(type.getActualTypeArguments())
                    .map(t -> AnnotatedTypeFactory.create(t, this))
                    .toArray(AnnotatedType[]::new);
        }

        @Override
        public AnnotatedType[] getAnnotatedActualTypeArguments() {
            return typeArgs;
        }

    }

    private static final class AnnotatedArrayTypeImpl extends AbstractAnnotatedType implements AnnotatedArrayType {

        private final AnnotatedType componentType;

        private AnnotatedArrayTypeImpl(Type type, AnnotatedType owner) {
            super(type, owner);
            Type genericComponentType;
            if (type instanceof GenericArrayType gat) {
                genericComponentType = gat.getGenericComponentType();
            } else {
                genericComponentType = ((Class<?>) type).getComponentType();
            }
            this.componentType = AnnotatedTypeFactory.create(genericComponentType, this);
        }

        @Override
        public AnnotatedType getAnnotatedGenericComponentType() {
            return componentType;
        }

    }

    private static final class AnnotatedTypeVariableImpl extends AbstractAnnotatedType implements AnnotatedTypeVariable {

        private AnnotatedTypeVariableImpl(TypeVariable<?> type, AnnotatedType owner) {
            super(type, owner);
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return Arrays.stream(((TypeVariable<?>) type).getBounds())
                    .map(t -> AnnotatedTypeFactory.create(t, this))
                    .toArray(AnnotatedType[]::new);
        }

    }

    private static final class AnnotatedWildcardTypeImpl extends AbstractAnnotatedType implements AnnotatedWildcardType {

        private AnnotatedWildcardTypeImpl(WildcardType type, AnnotatedType owner) {
            super(type, owner);
        }

        @Override
        public AnnotatedType[] getAnnotatedLowerBounds() {
            return Arrays.stream(((WildcardType) type).getLowerBounds())
                    .map(t -> AnnotatedTypeFactory.create(t, this))
                    .toArray(AnnotatedType[]::new);
        }

        @Override
        public AnnotatedType[] getAnnotatedUpperBounds() {
            return Arrays.stream(((WildcardType) type).getUpperBounds())
                    .map(t -> AnnotatedTypeFactory.create(t, this))
                    .toArray(AnnotatedType[]::new);
        }

    }

}
