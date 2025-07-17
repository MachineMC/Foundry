package org.machinemc.foundry.visitor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a visitor for a specific object type.
 * This annotation allows to create decoupled modules that can handle the visitation logic for
 * different types.
 * <p>
 * When an object is visited by {@link VisitorHandler}, it finds and invokes the corresponding
 * {@link Visit} annotated method that matches the object's type. This respects annotations
 * and generics, visitor for {@code List<String>} will not be invoked with {@code List<Integer>}
 * and visitor for {@code @Foo int} will not be invoked with {@code @Bar int}.
 *
 * <p><b>Method Signature</b></p>
 * Methods annotated with {@link Visit} must conform to the following signature:
 * <pre>{@code
 * @Visit
 * public O visit(Visitor<O> visitor, O input, T object) {
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
 *   <li><b>{@code T object}</b>: The actual object to be visited. The runtime
 *   type of this object determines which {@link Visit} annotated method is selected.</li>
 *   <li><b>{@code returns}</b>: the updated result, this needs to match the output data type
 *   of {@link VisitorHandler} this module is used within.</li>
 * </ul>
 * The name of the visit method does not matter.
 *
 * @see Visitor
 * @see VisitorHandler
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Visit {

    /**
     * Whether the visit method accepts null values in the place of {@code T object}.
     *
     * @return true if the visit method accepts null, else false
     */
    boolean nullable() default false;

}
