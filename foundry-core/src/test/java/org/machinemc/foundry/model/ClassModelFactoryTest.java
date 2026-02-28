package org.machinemc.foundry.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClassModelFactory Tests")
public class ClassModelFactoryTest {


    public static class StandardClass {

        private String hiddenInternal;
        private int value;

        public StandardClass() {}

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Omit
        public String getHiddenInternal() {
            return hiddenInternal;
        }

    }

    @Test
    void testMapClassStructure() {
        ClassModel<?> model = ClassModelFactory.mapClass(StandardClass.class);

        assertEquals(2, model.getAttributes().length, "STRUCTURE should find both fields");
        assertTrue(Arrays.stream(model.getAttributes()).anyMatch(a -> a.name().equals("hiddenInternal")));
        assertTrue(Arrays.stream(model.getAttributes()).anyMatch(a -> a.name().equals("value")));
        assertInstanceOf(ClassModel.NoArgsConstructor.class, model.getConstructionMethod());
    }

    @Test
    void testMapClassExposed() {
        ClassModel<?> model = ClassModelFactory.mapClass(StandardClass.class, ClassModel.ModellingStrategy.EXPOSED,
                null);

        assertEquals(1, model.getAttributes().length,
                "EXPOSED should only find 'value' via getter/setter");
        assertEquals("value", model.getAttributes()[0].name());
    }

    public interface ConfigInterface {
        String getHost();
        void setHost(String host);

        int getPort();
        void setPort(int port);
    }

    @Test
    void testMapInterface() {
        ConfigInterface impl = new ConfigInterface() {
            String host; int port;
            @Override public String getHost() { return host; }
            @Override public void setHost(String host) { this.host = host; }
            @Override public int getPort() { return port; }
            @Override public void setPort(int port) { this.port = port; }
        };

        ClassModel.CustomConstructor<ConfigInterface> constructor = () -> impl;

        ClassModel<?> model = ClassModelFactory.mapInterface(ConfigInterface.class, constructor);

        assertEquals(2, model.getAttributes().length);
        assertTrue(Arrays.stream(model.getAttributes()).anyMatch(a -> a.name().equals("host")));
        assertTrue(Arrays.stream(model.getAttributes()).anyMatch(a -> a.name().equals("port")));
        assertEquals(constructor, model.getConstructionMethod());
    }

    public record UserRecord(UUID id, String username) {}

    @Test
    void testMapRecord() {
        ClassModel<?> model = ClassModelFactory.mapRecord(UserRecord.class);

        assertEquals(2, model.getAttributes().length);
        assertEquals("id", model.getAttributes()[0].name());
        assertEquals("username", model.getAttributes()[1].name());
        assertInstanceOf(ClassModel.RecordConstructor.class, model.getConstructionMethod());

        assertNull(model.getAttributes()[0].access().setter());
    }

    public enum StatusEnum {
        ACTIVE(1), INACTIVE(0);

        private final int code;

        StatusEnum(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    @Test
    void testMapEnum() {
        ClassModel<?> model = ClassModelFactory.mapEnum(StatusEnum.class);

        ModelAttribute[] attributes = model.getAttributes();

        assertEquals(3, attributes.length);

        assertEquals("name", attributes[0].name());
        assertEquals(String.class, attributes[0].type());

        assertEquals("ordinal", attributes[1].name());
        assertEquals(int.class, attributes[1].type());

        for (ModelAttribute attr : attributes) {
            assertNull(attr.access().setter(), "Enum attribute '" + attr.name()
                    + "' should have a null setter");
        }
    }

    public static class MissingSetterClass {
        private int readOnly;

        public int getReadOnly() {
            return readOnly;
        }
    }

    @Test
    void testValidations() {
        assertThrows(IllegalArgumentException.class, () ->
                ClassModelFactory.mapClass(ConfigInterface.class)
        );

        assertThrows(IllegalArgumentException.class, () ->
                ClassModelFactory.mapClass(StatusEnum.class)
        );

        assertThrows(IllegalArgumentException.class, () ->
                ClassModelFactory.mapClass(UserRecord.class)
        );

        assertThrows(NullPointerException.class, () ->
                ClassModelFactory.mapInterface(ConfigInterface.class, null)
        );

        assertThrows(IllegalStateException.class, () ->
                ClassModelFactory.mapClass(MissingSetterClass.class, ClassModel.ModellingStrategy.EXPOSED,
                        null)
        );
    }

}
