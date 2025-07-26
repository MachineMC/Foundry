package org.machinemc.foundry.visitor;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ThreadSafe;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.machinemc.foundry.DataHandler;
import org.machinemc.foundry.Template;
import org.machinemc.foundry.util.Sneaky;
import org.machinemc.foundry.util.TypeUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
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
 * This class is immutable and thread-safe after construction.
 *
 * @param <I> input data type to be visited.
 * @param <O> output data type which is accumulated during the visitation process
 * @see Visitor
 * @see Visit
 */
@Immutable
@ThreadSafe
public class VisitorHandler<I, O> implements DataHandler<I, O>, Visitor<O> {

    /**
     * Creates a new {@link VisitorHandler} from a collection of modules.
     *
     * @param inputType the type of the input object
     * @param outputType the type of the output object
     * @param emptyOutput a supplier for creating a new, empty output instance
     * @param modules a collection of objects containing methods annotated with {@link Visit}
     * @param <I> the input type
     * @param <O> the output type
     * @return a new instance of {@link VisitorHandler}
     */
    public static <I, O> VisitorHandler<I, O> fromModules(Class<I> inputType,
                                                          Class<O> outputType,
                                                          Supplier<O> emptyOutput,
                                                          Object... modules) {
        return new VisitorHandler<>(inputType, outputType, emptyOutput, modules);
    }

    private final Class<O> outputType;
    private final Supplier<O> emptyOutput;
    private final @Unmodifiable Map<AnnotatedType, VisitorFactorySource> factoryMap;
    private final Template<I> inputTemplate;

    protected VisitorHandler(Class<I> inputType, Class<O> outputType, Supplier<O> emptyOutput) {
        this(inputType, outputType, emptyOutput, Collections.emptyList());
    }

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
    protected VisitorHandler(Class<I> inputType, Class<O> outputType, Supplier<O> emptyOutput, Collection<Object> modules) {
        this.outputType = Preconditions.checkNotNull(outputType, "Output type can not be null");
        this.emptyOutput = Preconditions.checkNotNull(emptyOutput, "Empty output supplier can not be null");
        Preconditions.checkNotNull(modules, "Modules for the visitor handler can not be null");

        Map<AnnotatedType, VisitorFactorySource> resolved = new LinkedHashMap<>();
        for (Object module : modules) {
            Stream.concat(Arrays.stream(module.getClass().getMethods()),
                            Arrays.stream(module.getClass().getDeclaredMethods()))
                    .filter(method -> method.isAnnotationPresent(Visit.class))
                    .distinct()
                    .peek(this::assureCorrectVisitMethod)
                    .forEach(method -> resolved.computeIfAbsent(
                            method.getAnnotatedParameterTypes()[2],
                            Sneaky.function(key -> new VisitorFactorySource(
                                    module,
                                    (VisitorFactory<?, ?>) createVisitCallsite(method).getTarget().invokeExact()
                            ))
                    ));
        }
        this.factoryMap = Collections.unmodifiableMap(resolved);

        Preconditions.checkNotNull(inputType, "Input type can not be null");
        inputTemplate = Template.of(inputType);
        inputTemplate.setAccessible(true);
    }

    private void assureCorrectVisitMethod(Method method) {
        String formatError = "Method " + method + " does not follow the proper @Visit method format. ";
        Preconditions.checkState(method.getReturnType() == outputType, formatError
                + "The return type must be " + outputType.getName());
        Preconditions.checkState(method.getParameterCount() == 3, formatError
                + "The method must accept exactly 3 parameters.");
        Preconditions.checkState(method.getParameterTypes()[0] == Visitor.class, formatError
                + "The first parameter must be " + Visitor.class.getName());
        Preconditions.checkState(method.getParameterTypes()[1] == outputType, formatError
                + "The second parameter must be " + outputType.getName());
        Preconditions.checkState(TypeUtils.getRawType(method.getParameterTypes()[2]) != null,
                formatError + "The third parameter must be a raw type.");
    }

    private CallSite createVisitCallsite(Method method) throws IllegalAccessException, LambdaConversionException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup());
        MethodHandle methodHandle = lookup.unreflect(method);
        return LambdaMetafactory.metafactory(lookup,
                "visit",
                MethodType.methodType(VisitorFactory.class),
                MethodType.methodType(Object.class, Object.class, Visitor.class, Object.class, Object.class),
                methodHandle,
                methodHandle.type()
        );
    }

    @Override
    public O transform(I instance) throws Exception {
        O output = emptyOutput.get();

        for (Field field : inputTemplate) {
            Object value = field.get(instance); // TODO possibly avoid reflection with asm
            // TODO implement handling for nullable values based on the @Visit#nullable=true
            output = visit(output, value, field.getAnnotatedType());
        }

        return output;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> O visit(O input, @Nullable T object, AnnotatedType type) {
        List<Map.Entry<AnnotatedType, VisitorFactorySource>> candidates = factoryMap.entrySet().stream()
                .filter(entry -> TypeUtils.isCompatible(entry.getKey().getType(), type.getType()))
                .toList();

        Preconditions.checkState(!candidates.isEmpty(), "Could not find any visitor for type "
                + type.getType().getTypeName());

        if (candidates.size() == 1) {
            VisitorFactorySource source = candidates.getFirst().getValue();
            VisitorFactory<O, T> factory = (VisitorFactory<O, T>) source.visitorFactory();
            return factory.visit(source.module(), this, input, object);
        }

        List<Map.Entry<AnnotatedType, VisitorFactorySource>> tiedCandidates = new ArrayList<>();
        int minDistance = Integer.MAX_VALUE;

        Class<?> rawInputType = TypeUtils.getRawType(type);
        Preconditions.checkState(rawInputType != null, "Could not determine raw type of "
                + type.getType().getTypeName());

        for (Map.Entry<AnnotatedType, VisitorFactorySource> candidate : candidates) {
            Class<?> rawCandidateType = TypeUtils.getRawType(candidate.getKey());

            int distance = TypeUtils.getDistance(rawInputType, rawCandidateType);
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

        if (tiedCandidates.size() == 1) {
            VisitorFactorySource source = tiedCandidates.getFirst().getValue();
            VisitorFactory<O, T> factory = (VisitorFactory<O, T>) source.visitorFactory();
            return factory.visit(source.module(), this, input, object);
        }

        Map.Entry<AnnotatedType, VisitorFactorySource> finalWinner = null;
        long maxAnnotationScore = -1;

        Set<Annotation> inputAnnotations = Set.of(type.getAnnotations());

        for (Map.Entry<AnnotatedType, VisitorFactorySource> candidate : tiedCandidates) {
            long score = Arrays.stream(candidate.getKey().getAnnotations())
                    .filter(inputAnnotations::contains)
                    .count();

            if (score > maxAnnotationScore) {
                maxAnnotationScore = score;
                finalWinner = candidate;
            }
        }

        VisitorFactorySource finalSource = finalWinner.getValue();
        VisitorFactory<O, T> factory = (VisitorFactory<O, T>) finalSource.visitorFactory();
        return factory.visit(finalSource.module(), this, input, object);
    }

    /**
     * Holder of a reference to the module instance and its associated visitor factory.
     *
     * @param module module (origin of the visitor factory)
     * @param visitorFactory visitor factory
     */
    private record VisitorFactorySource(Object module, VisitorFactory<?, ?> visitorFactory) {
    }

    /**
     * A functional interface representing the compiled lambda for a {@link Visit} method.
     *
     * @param <O> the output type
     * @param <T> the type of the object being visited
     */
    @FunctionalInterface
    private interface VisitorFactory<O, T> {
        O visit(Object module, Visitor<O> visitor, O input, T object);
    }

}
