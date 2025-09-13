package org.machinemc.foundry.visitor;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.machinemc.foundry.AllowNull;
import org.machinemc.foundry.DataHandler;
import org.machinemc.foundry.Template;
import org.machinemc.foundry.util.TypeUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A powerful implementation of the {@link DataHandler} that
 * processes an input object by visiting each of its fields using provided Visitor modules.
 * <p>
 * This handler uses a modular approach, where the logic for handling different data types
 * is defined in methods annotated with {@link Visit} within separate module objects.
 * <p>
 * When {@link #transform(Object)} is called, it iterates over the fields of the input object,
 * and for each field, it dynamically dispatches the call to the most suitable {@code @Visit}
 * method based on the field's runtime type, including generics and annotations.
 * <p>
 * This class is immutable after construction.
 * <p>
 * For the handler to be thread-safe, the modules also need to be thread safe.
 *
 * @param <I> input data type to be visited.
 * @param <O> output data type which is accumulated during the visitation process
 * @see Visitor
 * @see Visit
 */
@Immutable
public class VisitorHandler<I, O> implements DataHandler<I, O>, Visitor<O> {

    /**
     * Creates a new {@link Builder} for a {@link VisitorHandler}.
     *
     * @param inputType the type of the input object
     * @param outputType the type of the output object
     * @param emptyOutput a supplier for creating a new, empty output instance
     * @param <I> the input type
     * @param <O> the output type
     * @return a new instance of {@link Builder}
     */
    public static <I, O> Builder<I, O> builder(Class<I> inputType, Class<O> outputType, Supplier<O> emptyOutput) {
        return new Builder<>(inputType, outputType, emptyOutput);
    }

    private final Class<I> inputType;
    private final Class<O> outputType;
    private final Supplier<O> emptyOutput;
    private final @Unmodifiable Map<AnnotatedType, VisitorFactorySource> factoryMap;

    private final transient Map<Class<? extends I>, Template<? extends I>> resolvedTemplateCache
            = new ConcurrentHashMap<>();
    private final transient Map<AnnotatedType, VisitorFactorySource> resolvedVisitorCache = new ConcurrentHashMap<>();

    protected VisitorHandler(Class<I> inputType, Class<O> outputType, Supplier<O> emptyOutput, Object... modules) {
        this(inputType, outputType, emptyOutput, List.of(modules));
    }

    /**
     * Constructs a {@link VisitorHandler}, discovering and preparing visitor methods from the provided modules.
     *
     * @param inputType the type of the input object
     * @param outputType the type of the output object
     * @param emptyOutput a supplier for creating a new, empty output instance
     * @param modules a collection of objects containing methods annotated with {@link Visit}
     */
    protected VisitorHandler(Class<I> inputType, Class<O> outputType,
                             Supplier<O> emptyOutput, Collection<Object> modules) {
        this.inputType = Preconditions.checkNotNull(inputType, "Input type can not be null");
        this.outputType = Preconditions.checkNotNull(outputType, "Output type can not be null");
        this.emptyOutput = Preconditions.checkNotNull(emptyOutput, "Empty output supplier can not be null");
        Preconditions.checkNotNull(modules, "Modules for the visitor handler can not be null");

        Map<AnnotatedType, VisitorFactorySource> resolved = new LinkedHashMap<>();
        for (Object module : modules) {
            Preconditions.checkNotNull(modules, "Module can not be null");
            Stream.concat(Arrays.stream(module.getClass().getMethods()),
                            Arrays.stream(module.getClass().getDeclaredMethods()))
                    .filter(method -> method.isAnnotationPresent(Visit.class))
                    .distinct()
                    .peek(this::assureCorrectVisitMethod)
                    .forEach(method -> resolved.computeIfAbsent(
                            method.getAnnotatedParameterTypes()[2],
                            key -> {
                                try {
                                    boolean allowNull = method.getAnnotatedParameterTypes()[2]
                                            .isAnnotationPresent(AllowNull.class);
                                    //noinspection unchecked
                                    return new VisitorFactorySource(
                                            module,
                                            (VisitorFactory<Object, Object>) createVisitCallsite(method)
                                                    .getTarget().invokeExact(),
                                            allowNull
                                    );
                                } catch (Throwable throwable) {
                                    // should not happen and be caught by assureCorrectVisitMethod
                                    throw new RuntimeException(throwable);
                                }
                            }
                    ));
        }
        this.factoryMap = Collections.unmodifiableMap(resolved);
    }

    private void assureCorrectVisitMethod(Method method) {
        String formatError = "Method " + method + " does not follow the proper @Visit method format. ";
        Preconditions.checkState(method.getReturnType() == outputType, formatError
                + "The return type must be " + outputType.getName());
        Preconditions.checkState(method.getParameterCount() == 4, formatError
                + "The method must accept exactly 4 parameters.");
        Preconditions.checkState(method.getParameterTypes()[0] == Visitor.class, formatError
                + "The first parameter must be " + Visitor.class.getName());
        Preconditions.checkState(method.getParameterTypes()[1] == outputType, formatError
                + "The second parameter must be " + outputType.getName());
        Preconditions.checkState(method.getParameterTypes()[3] == AnnotatedType.class, formatError
                + "The fourth parameter must be " + AnnotatedType.class.getName());
        Preconditions.checkState(TypeUtils.getRawType(method.getParameterTypes()[2]) != null,
                formatError + "The third parameter must be a raw type.");
    }

    private CallSite createVisitCallsite(Method method) throws IllegalAccessException, LambdaConversionException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup());
        MethodHandle methodHandle = lookup.unreflect(method);
        return LambdaMetafactory.metafactory(lookup,
                "visit",
                MethodType.methodType(VisitorFactory.class),
                MethodType.methodType(Object.class, Object.class, Visitor.class,
                        Object.class, Object.class, AnnotatedType.class),
                methodHandle,
                methodHandle.type()
        );
    }

    @Override
    public O transform(I instance) throws Exception {
        Preconditions.checkNotNull(instance, "Instance to transform can not be null");
        Preconditions.checkArgument(inputType.isInstance(instance), "Can not transform instance of type "
                + instance.getClass().getName());

        O output = emptyOutput.get();

        //noinspection unchecked
        Template<? extends I> template = resolvedTemplateCache
                .computeIfAbsent((Class<? extends I>) instance.getClass(), Template::of);
        for (Field field : template) {
            Object value = field.get(instance);
            output = visit(output, value, field.getAnnotatedType());
        }

        return output;
    }

    @Override
    public <T> O visit(O input, @Nullable T object, AnnotatedType type) {
        Preconditions.checkNotNull(input, "Input data can not be null");
        Preconditions.checkNotNull(type, "Type of the object can not be null");
        Class<?> rawType = TypeUtils.getRawType(type);
        if (rawType != null && object != null)
            Preconditions.checkArgument(rawType.isInstance(object), "Provided type " + rawType.getName()
                    + " for object of type " + object.getClass().getName());

        VisitorFactorySource source = resolvedVisitorCache.computeIfAbsent(type, this::findBestVisitorForType);
        return source.visit(source.module(), this, input, object, type);
    }

    /**
     * Finds the most specific visitor for a given annotated type.
     *
     * @param type the annotated type to find a visitor for
     * @return the best {@link VisitorFactorySource} for the given type
     */
    private VisitorFactorySource findBestVisitorForType(AnnotatedType type) {
        List<Map.Entry<AnnotatedType, VisitorFactorySource>> candidates = factoryMap.entrySet().stream()
                .filter(entry -> TypeUtils.isCompatible(entry.getKey().getType(), type.getType()))
                .toList();

        Preconditions.checkState(!candidates.isEmpty(), "Could not find any visitor for type "
                + type.getType().getTypeName());

        if (candidates.size() == 1)
            return candidates.getFirst().getValue();

        List<Map.Entry<AnnotatedType, VisitorFactorySource>> tiedCandidates = new ArrayList<>();
        int minDistance = Integer.MAX_VALUE;

        Class<?> rawType = TypeUtils.getRawType(type);
        Preconditions.checkState(rawType != null, "Could not determine raw type of "
                + type.getType().getTypeName());

        for (Map.Entry<AnnotatedType, VisitorFactorySource> candidate : candidates) {
            Class<?> rawCandidateType = TypeUtils.getRawType(candidate.getKey());

            int distance = TypeUtils.getDistance(rawType, rawCandidateType);
            if (distance < 0) continue;

            if (distance < minDistance) {
                minDistance = distance;
                tiedCandidates.clear();
                tiedCandidates.add(candidate);
            } else if (distance == minDistance) {
                tiedCandidates.add(candidate);
            }
        }

        Preconditions.checkState(!tiedCandidates.isEmpty(), "Could not find a specific visitor for type "
                + type.getType().getTypeName());

        if (tiedCandidates.size() == 1)
            return tiedCandidates.getFirst().getValue();

        Map.Entry<AnnotatedType, VisitorFactorySource> finalWinner = null;
        long maxAnnotationScore = -1;

        Set<Annotation> annotations = Set.of(type.getAnnotations());

        for (Map.Entry<AnnotatedType, VisitorFactorySource> candidate : tiedCandidates) {
            long score = Arrays.stream(candidate.getKey().getAnnotations())
                    .filter(annotations::contains)
                    .count();

            if (score > maxAnnotationScore) {
                maxAnnotationScore = score;
                finalWinner = candidate;
            }
        }

        return finalWinner.getValue();
    }

    /**
     * Holder of a reference to the module instance and its associated visitor factory.
     *
     * @param module module (origin of the visitor factory)
     * @param visitorFactory visitor factory
     * @param allowNull whether the visitor method accepts null values
     */
    private record VisitorFactorySource(Object module, VisitorFactory<Object, Object> visitorFactory,
                                        boolean allowNull) {

        private <O, T> O visit(Object module, Visitor<O> visitor, O input, T object, AnnotatedType type) {
            Preconditions.checkArgument(object != null || allowNull, "Failed to pass null value "
                    + "for type " + TypeUtils.getRawType(type) + " for module " + module.getClass().getName());
            //noinspection unchecked
            return (O) visitorFactory.visit(module, (Visitor<Object>) visitor, input, object, type);
        }

    }

    /**
     * A functional interface representing the compiled lambda for a {@link Visit} method.
     *
     * @param <O> the output type
     * @param <T> the type of the object being visited
     */
    @FunctionalInterface
    private interface VisitorFactory<O, T> {

        O visit(Object module, Visitor<O> visitor, O input, T object, AnnotatedType type);

    }

    /**
     * Builder for the {@link VisitorHandler}.
     *
     * @param <I> input data type to be visited
     * @param <O> output data type which is accumulated during the visitation process
     */
    public static final class Builder<I, O> {

        private final Class<I> inputType;
        private final Class<O> outputType;
        private final Supplier<O> emptyOutput;
        private final List<Object> modules = new ArrayList<>();

        private Builder(Class<I> inputType, Class<O> outputType, Supplier<O> emptyOutput) {
            this.inputType = Preconditions.checkNotNull(inputType, "Input type can not be null");
            this.outputType = Preconditions.checkNotNull(outputType, "Output type can not be null");
            this.emptyOutput = Preconditions.checkNotNull(emptyOutput, "Empty output supplier can not be null");
        }

        /**
         * Adds modules to the builder.
         *
         * @param modules modules to add
         * @return this builder
         */
        public Builder<I, O> addModules(Object... modules) {
            Preconditions.checkNotNull(modules, "Modules can not be null");
            this.modules.addAll(List.of(modules));
            return this;
        }

        /**
         * Builds the {@link VisitorHandler}.
         *
         * @return a new instance of {@link VisitorHandler}
         */
        public VisitorHandler<I, O> build() {
            return new VisitorHandler<>(inputType, outputType, emptyOutput, modules);
        }

    }

}
