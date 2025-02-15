package org.machinemc.foundry.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Handler {

    Class<? extends FieldFlag> value();

    Class<?>[] targetTypes() default {};

}
