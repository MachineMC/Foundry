package org.machinemc.foundry.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.machinemc.foundry.Omit;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ObjectFactory Tests")
public class ObjectFactoryTest {

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
    void testDirectFields() {
        ObjectFactory<DirectFieldPojo> model = ObjectFactory.create(DirectFieldPojo.class);
        DirectFieldPojo original = new DirectFieldPojo("Direct Access", 42, 3.14);

        ModelDataContainer container = model.write(original);
        DirectFieldPojo copy = model.read(container);

        assertNotSame(original, copy);
        assertEquals(original, copy);
    }

    public static class AccessorPojo {

        private String name;
        private boolean active;

        private @Omit int getCalls = 0, setCalls = 0;

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
    void testAccessors() {
        ObjectFactory<AccessorPojo> model = ObjectFactory.create(AccessorPojo.class);
        AccessorPojo original = new AccessorPojo("Encapsulated", true);

        ModelDataContainer container = model.write(original);
        AccessorPojo copy = model.read(container);

        assertNotSame(original, copy);
        assertEquals(original, copy);

        assertEquals(2, original.getCalls);
        assertEquals(2, copy.setCalls);
    }

    public record SimpleRecord(String key, int value, float factor) {
    }

    @Test
    void testRecord() {
        ObjectFactory<SimpleRecord> model = ObjectFactory.create(SimpleRecord.class);
        SimpleRecord original = new SimpleRecord("RecordKey", 100, 0.5f);

        ModelDataContainer container = model.write(original);
        SimpleRecord copy = model.read(container);

        assertEquals(original, copy);
    }

    public record IntsRecord(int first, int middle, int last) {
    }

    @Test
    void testContainerReadOrder() {
        ObjectFactory<IntsRecord> model = ObjectFactory.create(IntsRecord.class);
        IntsRecord original = new IntsRecord(1, 2, 3);

        ModelDataContainer container = model.write(original);

        assertEquals(1, container.readInt());
        assertEquals(2, container.readInt());
        assertEquals(3, container.readInt());
    }

    public static class BadlyNamedAccessors {

        int foo = 10;
        @Omit boolean calledGet = false, calledSet = false;

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
    void testFieldAccessAnnotation() {
        ObjectFactory<BadlyNamedAccessors> model = ObjectFactory.create(BadlyNamedAccessors.class);
        BadlyNamedAccessors original = new BadlyNamedAccessors();
        original.foo = 20;

        ModelDataContainer container = model.write(original);
        BadlyNamedAccessors copy = model.read(container);

        assertEquals(20, copy.foo);

        assertTrue(original.calledGet);
        assertTrue(copy.calledSet);
    }

}
