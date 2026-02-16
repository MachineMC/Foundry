package org.machinemc.foundry.model;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;
import org.machinemc.foundry.Omit;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a schema of a Java class, defining how to construct instances
 * and access its attributes.
 * <p>
 * This model enforces two strategies based on the class type:
 * <ul>
 * <li><b>Records:</b> Constructed using the canonical all args constructor. Attributes
 * are accessed with record component accessors.</li>
 *
 * <li><b>Regular Classes:</b> Constructed using the no args constructor and field setters.
 * All fields that are not transient, static, or annotated with {@link Omit} must be mutable.
 * Foundry automatically finds getter and setter methods for individual fields if they exist
 * and follow regular naming format. If they do not, the methods to use can be
 * specified with {@link FieldAccess}.
 * </ul>
 */
public class ClassModel {

    private final ModelAttribute[] attributes;
    private final ConstructionMethod constructionMethod;

    /**
     * Creates a class model for the specified type.
     *
     * @param type type
     * @return class model of given type
     * @throws IllegalStateException if the class does not satisfy the requirements
     */
    public static ClassModel of(Class<?> type) {
        return of0(type);
    }

    protected ClassModel(ModelAttribute[] attributes, ConstructionMethod constructionMethod) {
        this.attributes = attributes;
        this.constructionMethod = constructionMethod;
    }

    /**
     * Returns the attributes of this class model.
     * <p>
     * The order is guaranteed only if the class represented by this model is a record.
     * The attributes are then in returned in the order of record component declaration.
     *
     * @return attributes of this model
     */
    public ModelAttribute[] getAttributes() {
        return Arrays.copyOf(attributes, attributes.length);
    }

    /**
     * @return which constructor is used in this model
     */
    public ConstructionMethod getConstructionMethod() {
        return constructionMethod;
    }

    /**
     * Which construction method is used when creating new instance of the class.
     */
    public sealed interface ConstructionMethod {
    }

    /**
     * No args constructor is called during the creation and the fields are
     * populated via setters.
     * <p>
     * This method is used by regular classes.
     */
    public record NoArgsConstructor() implements ConstructionMethod {
        public static final NoArgsConstructor INSTANCE = new NoArgsConstructor();
    }

    /**
     * The object is created just by the all args constructor.
     * <p>
     * This method is used by records.
     */
    public record AllArgsConstructor() implements ConstructionMethod {
        public static final AllArgsConstructor INSTANCE = new AllArgsConstructor();
    }

    private static ClassModel of0(Class<?> type) {
        if (type.isEnum() || type.isInterface() || type.isAnnotation() || Modifier.isAbstract(type.getModifiers()))
            throw new UnsupportedOperationException("Can not automatically resolve class model for '"
                    + type.getName() + "'");

        if (type.isRecord())
            return ofRecord(type);

        ModelAttribute[] attributes = Arrays.stream(type.getDeclaredFields())
                .filter(ClassModel::keepField)
                .map(ClassModel::asAttribute)
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
        return new ClassModel(attributes, NoArgsConstructor.INSTANCE);
    }

    private static ClassModel ofRecord(Class<?> type) {
        ModelAttribute[] attributes = Arrays.stream(type.getRecordComponents())
                .map(ClassModel::asAttribute)
                .toArray(ModelAttribute[]::new);
        Constructor<?> constructor = allArgsConstructor(type, attributes);
        Preconditions.checkNotNull(constructor); // is always present on records
        return new ClassModel(attributes, AllArgsConstructor.INSTANCE);
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
