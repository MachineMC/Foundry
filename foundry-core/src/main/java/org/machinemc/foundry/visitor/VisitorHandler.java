package org.machinemc.foundry.visitor;

import org.jetbrains.annotations.Nullable;
import org.machinemc.foundry.DataHandler;

import java.lang.reflect.AnnotatedType;

// TODO
public class VisitorHandler<I, O> implements DataHandler<I, O>, Visitor<O> {

    @Override
    public O transform(I instance) {
        return null;
    }

    @Override
    public <T> O visit(O input, @Nullable T object, AnnotatedType type) {
        return null;
    }

}
