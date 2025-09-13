package org.machinemc.foundry.visitor;

import org.machinemc.foundry.AllowNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;

/**
 * Marks a method as a visitor for a specific object type.
 * This annotation allows to create decoupled modules that can handle the visitation logic for
 * different types.
 * <p>
 * When an object is visited by {@link VisitorHandler}, it finds and invokes the corresponding
 * {@link Visit} annotated method that matches the object's type. This respects annotations
 * and generics, visitor for {@code List<String>} will not be invoked with {@code List<Integer>}
 * and visitor for {@code @Foo int} will not be invoked with {@code @Bar int}.
 * <p>
 * For the {@link Visit} annotated method to accept {@code null}, the data type of object
 * to visit needs to be annotated with {@link AllowNull}.
 *
 * <p><b>Method Signature</b></p>
 * Methods annotated with {@link Visit} must conform to the following signature:
 * <pre>{@code
 * @Visit
 * public O visit(Visitor<O> visitor, O input, T object, AnnotatedType type) {
 *     // implementation
 * }
 * }</pre>
 * Where:
 * <ul>
 *   <li><b>{@code Visitor<O> visitor}</b>: The visitor instance itself.
 *   This enables recursive visitation. If the visited {@code object} contains other
 *   elements that need to be processed, the implementation can invoke
 *   {@link Visitor#visit(Object, Object)} to continue the traversal.</li>
 *   <li><b>{@code O input}</b>: The input data from previous visit.
 *   <li><b>{@code T object}</b>: The actual object to be visited.
 *   <li><b>{@code AnnotatedType}</b> the {@link java.lang.reflect.AnnotatedType} of the object being visited,
 *   this should be used together with {@link Visitor#visit(Object, Object, AnnotatedType)} to preserve
 *   types during recursive visitation.
 *   <li><b>{@code returns}</b>: the updated result, this needs to match the output data type
 *   of {@link VisitorHandler} this module is used within.</li>
 * </ul>
 * The name of the visit method does not matter.
 *
 * @see Visitor
 * @see VisitorHandler
 */
// TODO instead of getting the data from previous visit as a parameter and returning the data for next visit,
//  add a structure for this (something like pair with modifiable right side?) and provide it as a parameter
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Visit {
}
