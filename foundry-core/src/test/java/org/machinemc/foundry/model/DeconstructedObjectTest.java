package org.machinemc.foundry.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                getField(deconstructed, "text").orElseThrow();
        assertNotNull(textField);
        assertEquals("Direct Access", textField.value());

        DeconstructedObject.IntField numField = (DeconstructedObject.IntField)
                getField(deconstructed, "number").orElseThrow();
        assertNotNull(numField);
        assertEquals(42, numField.value());

        DeconstructedObject.DoubleField decField = (DeconstructedObject.DoubleField)
                getField(deconstructed, "decimal").orElseThrow();
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
                getField(deconstructed, "active").orElseThrow();
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

        assertInstanceOf(DeconstructedObject.IntField.class, getField(deconstructed, "value").orElseThrow());
        assertInstanceOf(DeconstructedObject.FloatField.class, getField(deconstructed, "factor").orElseThrow());
        assertInstanceOf(DeconstructedObject.ObjectField.class, getField(deconstructed, "key").orElseThrow());

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
                getField(deconstructed, "foo").orElseThrow();
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

        var modifiedFields = deconstructed.asList().stream().map(f -> {
            if (f.name().equals("value"))
                return new DeconstructedObject.IntField("value", f.annotatedType(), 999);
            else
                return f;
        }).toList();

        DeconstructedObject modified = new DeconstructedObject(modifiedFields);

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

    Optional<DeconstructedObject.Field> getField(DeconstructedObject obj, String name) {
        for (var field : obj) {
            if (field.name().equals(name))
                return Optional.of(field);
        }
        return Optional.empty();
    }


    public static class BaseEntity {
        private int baseId;
        private BaseEntity() {}
        public BaseEntity(int baseId) { this.baseId = baseId; }
        public int fetchBaseId() { return baseId; } // non bean name to force direct field access
    }

    public static class SubEntity1 extends BaseEntity {
        private String sub1Name;
        private SubEntity1() {}
        public SubEntity1(int baseId, String sub1Name) {
            super(baseId);
            this.sub1Name = sub1Name;
        }
        public String fetchSub1Name() { return sub1Name; }
    }

    public static class SubEntity2 extends SubEntity1 {
        private boolean sub2Flag;
        private SubEntity2() {}
        public SubEntity2(int baseId, String sub1Name, boolean sub2Flag) {
            super(baseId, sub1Name);
            this.sub2Flag = sub2Flag;
        }
        public boolean fetchSub2Flag() { return sub2Flag; }
    }

    public static class SubEntity3 extends SubEntity2 {
        private double sub3Value;
        private SubEntity3() {}
        public SubEntity3(int baseId, String sub1Name, boolean sub2Flag, double sub3Value) {
            super(baseId, sub1Name, sub2Flag);
            this.sub3Value = sub3Value;
        }
        public double fetchSub3Value() { return sub3Value; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SubEntity3 that)) return false;
            return fetchBaseId() == that.fetchBaseId() &&
                    fetchSub2Flag() == that.fetchSub2Flag() &&
                    Double.compare(that.fetchSub3Value(), fetchSub3Value()) == 0 &&
                    Objects.equals(fetchSub1Name(), that.fetchSub1Name());
        }
    }

    @Test
    void testDeepInheritanceWithPrivateFields() throws Exception {
        var codec = DeconstructedObject.codec(SubEntity3.class);

        SubEntity3 original = new SubEntity3(1024, "Deep Hierarchy", true, 99.99);

        DeconstructedObject deconstructedObject = codec.encode(original);
        SubEntity3 copy = codec.decode(deconstructedObject);

        assertNotSame(original, copy);
        assertEquals(original, copy);

        assertEquals(1024, copy.fetchBaseId());
        assertEquals("Deep Hierarchy", copy.fetchSub1Name());
        assertTrue(copy.fetchSub2Flag());
        assertEquals(99.99, copy.fetchSub3Value());
    }

    public interface ConfigNode {
        String getHost();
        void setHost(String host);

        int getPort();
        void setPort(int port);
    }

    public static class DefaultConfigNode implements ConfigNode {
        private String host;
        private int port;
        @Override public String getHost() { return host; }
        @Override public void setHost(String host) { this.host = host; }
        @Override public int getPort() { return port; }
        @Override public void setPort(int port) { this.port = port; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConfigNode that)) return false;
            return port == that.getPort() && Objects.equals(host, that.getHost());
        }
    }

    @Test
    void testInterfaceMapping() throws Exception {
        ClassModel.CustomConstructor<ConfigNode> constructor = DefaultConfigNode::new;
        ClassModel<ConfigNode> classModel = ClassModel.ofInterface(ConfigNode.class, constructor);

        var codec = DeconstructedObject.codec(ConfigNode.class, classModel);

        ConfigNode original = new DefaultConfigNode();
        original.setHost("localhost");
        original.setPort(8080);

        DeconstructedObject deconstructed = codec.encode(original);

        assertEquals(2, deconstructed.size());
        assertEquals("localhost", ((DeconstructedObject.ObjectField) getField(deconstructed, "host")
                .orElseThrow()).value());
        assertEquals(8080, ((DeconstructedObject.IntField) getField(deconstructed, "port")
                .orElseThrow()).value());

        ConfigNode copy = codec.decode(deconstructed);

        assertNotSame(original, copy);
        assertEquals(original, copy);
    }

    public static abstract class AbstractTask {
        private String id;

        protected AbstractTask() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public abstract boolean isCompleted();
    }

    public static class ConcreteTask extends AbstractTask {
        private boolean completed;

        @Override
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AbstractTask that)) return false;
            return isCompleted() == that.isCompleted() && Objects.equals(getId(), that.getId());
        }
    }

    @Test
    void testAbstractClassMapping() throws Exception {
        ClassModel.CustomConstructor<AbstractTask> customConstructor = ConcreteTask::new;
        ClassModel<AbstractTask> classModel = ClassModel.ofClass(
                AbstractTask.class,
                ClassModel.ModellingStrategy.STRUCTURE,
                customConstructor
        );

        var codec = DeconstructedObject.codec(AbstractTask.class, classModel);

        ConcreteTask original = new ConcreteTask();
        original.setId("TASK-123");
        original.setCompleted(true);

        DeconstructedObject deconstructed = codec.encode(original);

        assertEquals(1, deconstructed.size());
        assertEquals("TASK-123", ((DeconstructedObject.ObjectField) getField(deconstructed, "id")
                .orElseThrow()).value());

        AbstractTask copy = codec.decode(deconstructed);

        assertNotSame(original, copy);
        assertEquals("TASK-123", copy.getId());
        assertFalse(copy.isCompleted()); // state lost because 'completed' is not in AbstractTask
    }

    public enum Priority {
        LOW(10), MEDIUM(50), HIGH(100);

        private final int level;

        Priority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    @Test
    void testEnumMapping() throws Exception {
        ClassModel<Priority> classModel = ClassModel.ofEnum(
                Priority.class,
                ClassModel.ModellingStrategy.STRUCTURE,
                ClassModel.EnumConstructor.valueOf(Priority.class)
        );
        var codec = DeconstructedObject.codec(Priority.class, classModel);

        Priority original = Priority.HIGH;

        DeconstructedObject deconstructed = codec.encode(original);

        assertTrue(deconstructed.size() >= 3);

        DeconstructedObject.ObjectField nameField = (DeconstructedObject.ObjectField)
                getField(deconstructed, "name").orElseThrow();
        assertEquals("HIGH", nameField.value());

        DeconstructedObject.IntField ordinalField = (DeconstructedObject.IntField)
                getField(deconstructed, "ordinal").orElseThrow();
        assertEquals(2, ordinalField.value());

        DeconstructedObject.IntField levelField = (DeconstructedObject.IntField)
                getField(deconstructed, "level").orElseThrow();
        assertEquals(100, levelField.value());

        Priority copy = codec.decode(deconstructed);
        assertSame(original, copy, "Enum instances must maintain JVM identity");
    }

}
