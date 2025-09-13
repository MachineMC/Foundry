package org.machinemc.foundry.visitor;

import org.junit.jupiter.api.Test;
import org.machinemc.foundry.Pipeline;

import java.lang.reflect.AnnotatedType;

import static org.junit.jupiter.api.Assertions.*;

public class VisitorHandlerTest {

    static class SimpleModule {

        @Visit
        public StringBuilder visitString(Visitor<StringBuilder> visitor, StringBuilder input,
                                         String object, AnnotatedType type) {
            return input.append("'").append(object).append("'").append(", ");
        }

        @Visit
        public StringBuilder visitInteger(Visitor<StringBuilder> visitor, StringBuilder input,
                                          Integer object, AnnotatedType type) {
            return input.append(object).append(", ");
        }

    }

    static class SimpleObject {

        public String name;
        public Integer value;

        public SimpleObject(String name, Integer value) {
            this.name = name;
            this.value = value;
        }

    }

    @Test
    void testSimpleObject() throws Exception {
        Pipeline<SimpleObject, String> pipeline = Pipeline.builder(
                VisitorHandler.builder(SimpleObject.class, StringBuilder.class, StringBuilder::new)
                        .addModules(new SimpleModule())
                        .build()
                )
                .next(StringBuilder::toString)
                .build();

        String result = pipeline.process(new SimpleObject("Hello", 10));
        assertEquals("'Hello', 10, ", result);
    }

}
