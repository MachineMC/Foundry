package org.machinemc.foundry;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.machinemc.foundry.model.Omit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Representation of an object's structure.
 *
 * @param <T> the source class this template is created from
 */
// TODO does not work for records,
//  replace Field with its own Field representation
public final class Template<T> implements Iterable<Field> {

    private final Class<T> sourceClass;
    private final @Unmodifiable List<Field> fields;

    /**
     * Creates a {@link Template} by reflecting on the given class, containing all
     * its fields and fields of its super classes (ordered).
     *
     * @param type the class to create a template from
     * @return template for the given class
     */
    public static <S> Template<S> of(Class<S> type) {
        Preconditions.checkNotNull(type, "Source class type cannot be null");
        SequencedSet<Field> fieldSet = new LinkedHashSet<>();
        Class<?> current = type;
        while (current != null) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (skipField(field)) continue;
                fieldSet.add(field);
            }
            current = current.getSuperclass();
        }
        return new Template<>(type, fieldSet);
    }

    /**
     * Helper method for checking if the field should be omitted in the template.
     *
     * @param field field to check
     * @return true if the field should be omitted, else false
     */
    private static boolean skipField(Field field) {
        if (Modifier.isStatic(field.getModifiers())) return true;
        if (Modifier.isTransient(field.getModifiers())) return true;
        return field.isAnnotationPresent(Omit.class);
    }

    private Template(Class<T> sourceClass, SequencedCollection<Field> fields) {
        this.sourceClass = sourceClass;
        this.fields = ImmutableList.copyOf(fields);
    }

    /**
     * Returns the original class that this template was created from.
     *
     * @return the source class
     */
    public Class<T> getSourceClass() {
        return sourceClass;
    }

    /**
     * Returns the immutable, ordered list of field entries that make up this template.
     *
     * @return field entries
     */
    public @Unmodifiable List<Field> getFields() {
        return fields;
    }

    /**
     * Searches for a field entry by its name.
     *
     * @param name the name of the field to find
     * @return field entry if found, else empty
     */
    public Optional<Field> getField(String name) {
        return fields.stream().filter(f -> f.getName().equals(name)).findAny();
    }

    /**
     * Returns the number of fields in this template.
     *
     * @return the total field count
     */
    public int size() {
        return fields.size();
    }

    /**
     * Checks if this template contains any fields.
     *
     * @return {@code true} if this template has no fields, {@code false} otherwise
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * Sets the accessibility of the fields of this template.
     *
     * @param accessible accessibility
     */
    public void setAccessible(boolean accessible) {
        for (Field field : fields) {
            field.setAccessible(accessible);
        }
    }

    /**
     * Returns an iterator over the field entries in this template.
     *
     * @return a list iterator for the field entries
     */
    @Override
    public @NotNull ListIterator<Field> iterator() {
        return fields.listIterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Template<?> template)) return false;
        return Objects.equals(sourceClass, template.sourceClass) && Objects.equals(fields, template.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceClass, fields);
    }

    @Override
    public String toString() {
        return "Template{" + "sourceClass=" + sourceClass + ", fields=" + fields + "}";
    }

}
