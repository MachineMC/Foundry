package org.machinemc.foundry.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.machinemc.foundry.Omit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeconstructedObject Tests")
public class DeconstructedObjectTest {

    public static class DirectFieldPojo {
        private String text;
        protected int number;
        public double decimal;

        private DirectFieldPojo() {
        }

        public DirectFieldPojo(String text, int number, double decimal) {
            this.text = text;
            this.number = number;
            this.decimal = decimal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DirectFieldPojo that)) return false;
            return number == that.number && Double.compare(that.decimal, decimal) == 0 && Objects.equals(text, that.text);
        }
    }

    @Test
    void testDirectFields() throws Exception {
        var deconstructor = DeconstructedObject.createDeconstructor(DirectFieldPojo.class);
        var constructor = DeconstructedObject.createConstructor(DirectFieldPojo.class);

        DirectFieldPojo original = new DirectFieldPojo("Direct Access", 42, 3.14);

        DeconstructedObject deconstructed = deconstructor.transform(original);

        assertEquals(3, deconstructed.size());

        DeconstructedObject.ObjectField textField = (DeconstructedObject.ObjectField)
                deconstructed.getField("text").orElseThrow();
        assertNotNull(textField);
        assertEquals("Direct Access", textField.value());

        DeconstructedObject.IntField numField = (DeconstructedObject.IntField)
                deconstructed.getField("number").orElseThrow();
        assertNotNull(numField);
        assertEquals(42, numField.value());

        DeconstructedObject.DoubleField decField = (DeconstructedObject.DoubleField)
                deconstructed.getField("decimal").orElseThrow();
        assertNotNull(decField);
        assertEquals(3.14, decField.value());

        DirectFieldPojo copy = constructor.transform(deconstructed);

        assertNotSame(original, copy);
        assertEquals(original, copy);
    }

    public static class AccessorPojo {
        private String name;
        private boolean active;

        public @Omit int getCalls = 0;
        public @Omit int setCalls = 0;

        public AccessorPojo() {}

        public AccessorPojo(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        public String getName() {
            getCalls++;
            return name;
        }

        public void setName(String name) {
            setCalls++;
            this.name = name;
        }

        public boolean isActive() {
            getCalls++;
            return active;
        }

        public void setActive(boolean active) {
            setCalls++;
            this.active = active;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AccessorPojo that)) return false;
            return active == that.active && Objects.equals(name, that.name);
        }
    }

    @Test
    void testAccessors() throws Exception {
        var deconstructor = DeconstructedObject.createDeconstructor(AccessorPojo.class);
        var constructor = DeconstructedObject.createConstructor(AccessorPojo.class);

        AccessorPojo original = new AccessorPojo("Encapsulated", true);

        DeconstructedObject deconstructed = deconstructor.transform(original);

        assertEquals(2, original.getCalls, "Should have called getters during deconstruction");

        DeconstructedObject.BoolField activeField = (DeconstructedObject.BoolField)
                deconstructed.getField("active").orElseThrow();
        assertNotNull(activeField);
        assertTrue(activeField.value());

        AccessorPojo copy = constructor.transform(deconstructed);

        assertNotSame(original, copy);
        assertEquals(original, copy);

        assertEquals(2, copy.setCalls, "Should have called setters during reconstruction");
    }

    public record SimpleRecord(String key, int value, float factor) {
    }

    @Test
    void testRecord() throws Exception {
        var deconstructor = DeconstructedObject.createDeconstructor(SimpleRecord.class);
        var constructor = DeconstructedObject.createConstructor(SimpleRecord.class);

        SimpleRecord original = new SimpleRecord("RecordKey", 100, 0.5f);

        DeconstructedObject deconstructed = deconstructor.transform(original);

        assertInstanceOf(DeconstructedObject.IntField.class, deconstructed.getField("value").orElseThrow());
        assertInstanceOf(DeconstructedObject.FloatField.class, deconstructed.getField("factor").orElseThrow());
        assertInstanceOf(DeconstructedObject.ObjectField.class, deconstructed.getField("key").orElseThrow());

        SimpleRecord copy = constructor.transform(deconstructed);

        assertEquals(original, copy);
    }

    public static class BadlyNamedAccessors {
        int foo = 10;
        @Omit boolean calledGet = false;
        @Omit boolean calledSet = false;

        @FieldAccess("foo")
        int thisMethodGetsFoo() {
            calledGet = true;
            return foo;
        }

        @FieldAccess("foo")
        void thisMethodSetsFoo(int foo) {
            this.foo = foo;
            calledSet = true;
        }
    }

    @Test
    void testFieldAccessAnnotation() throws Exception {
        var deconstructor = DeconstructedObject.createDeconstructor(BadlyNamedAccessors.class);
        var constructor = DeconstructedObject.createConstructor(BadlyNamedAccessors.class);

        BadlyNamedAccessors original = new BadlyNamedAccessors();
        original.foo = 99;

        DeconstructedObject deconstructed = deconstructor.transform(original);

        DeconstructedObject.IntField fooField = (DeconstructedObject.IntField)
                deconstructed.getField("foo").orElseThrow();
        assertNotNull(fooField);
        assertEquals(99, fooField.value());
        assertTrue(original.calledGet, "Custom getter should be called");

        BadlyNamedAccessors copy = constructor.transform(deconstructed);

        assertEquals(99, copy.foo);
        assertTrue(copy.calledSet, "Custom setter should be called");
    }

    @Test
    void testManualModification() throws Exception {
        var deconstructor = DeconstructedObject.createDeconstructor(SimpleRecord.class);
        var constructor = DeconstructedObject.createConstructor(SimpleRecord.class);

        SimpleRecord original = new SimpleRecord("Original", 1, 1.0f);
        DeconstructedObject deconstructed = deconstructor.transform(original);

        Map<String, DeconstructedObject.Field> map = new LinkedHashMap<>(deconstructed.asMap());
        DeconstructedObject.Field toReplace = map.get("value");
        map.replace("value", new DeconstructedObject.IntField("value", toReplace.annotatedType(), 999));

        DeconstructedObject modified = new DeconstructedObject(map);

        SimpleRecord copy = constructor.transform(modified);

        assertEquals("Original", copy.key());
        assertEquals(999, copy.value());
    }

    @Test
    void testMultipleConstructions() throws Exception {
        var deconstructor = DeconstructedObject.createDeconstructor(DirectFieldPojo.class);
        var constructor = DeconstructedObject.createConstructor(DirectFieldPojo.class);

        DirectFieldPojo original = new DirectFieldPojo("Direct Access", 42, 3.14);

        for (int i = 0; i < 10; i++) {
            DeconstructedObject deconstructed = deconstructor.transform(original);
            DirectFieldPojo copy = constructor.transform(deconstructed);
            assertNotSame(original, copy);
            assertEquals(original, copy);
        }
    }

}
