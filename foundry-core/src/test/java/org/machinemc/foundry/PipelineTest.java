package org.machinemc.foundry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pipeline Tests")
class PipelineTest {

    @Test
    void testProcess_Identity() throws Exception {
        Pipeline<String, String> pipeline = Pipeline.<String>builder().build();
        assertEquals("test", pipeline.process("test"));
    }

    @Test
    void testProcess_SimpleTransform() throws Exception {
        Pipeline<String, Integer> pipeline = Pipeline.<String>builder()
                .next(String::length)
                .build();

        assertEquals(4, pipeline.process("test"));
    }

    @Test
    void testProcess_ChainedHandlers() throws Exception {
        Pipeline<String, String> pipeline = Pipeline.<String>builder()
                .next(String::trim)
                .next(String::toUpperCase)
                .next(s -> s + "!")
                .build();

        assertEquals("HELLO!", pipeline.process("  hello "));
    }

    @Test
    void testProcess_ChainedPipeline() throws Exception {
        Pipeline<String, Integer> subPipeline = Pipeline.<String>builder()
                .next(String::length)
                .build();

        Pipeline<String, Integer> mainPipeline = Pipeline.<String>builder()
                .next(String::trim)
                .next(subPipeline)
                .build();

        assertEquals(5, mainPipeline.process(" hello "));
    }

    @Test
    void testCompose_TwoPipelines() throws Exception {
        Pipeline<String, Integer> p1 = Pipeline.builder(String::length).build();
        Pipeline<Integer, String> p2 = Pipeline.<Integer, String>builder(Object::toString).build();

        Pipeline<String, String> composed = Pipeline.compose(p1, p2);

        assertEquals("4", composed.process("test"));
    }

    @Test
    void testCompose_ThreePipelines() throws Exception {
        Pipeline<String, String> p1 = Pipeline.builder(String::trim).build();
        Pipeline<String, Integer> p2 = Pipeline.builder(String::length).build();
        Pipeline<Integer, Integer> p3 = Pipeline.builder((Integer i) -> i * 2).build();

        Pipeline<String, Integer> composed = Pipeline.compose(p1, p2, p3);

        assertEquals(8, composed.process(" test "));
    }

    @Test
    void testProtected_BasicMap() throws Exception {
        Pipeline<String, String> pipeline = Pipeline.<String>builder()
                .protect()
                .map(String::toUpperCase)
                .build();

        assertEquals("TEST", pipeline.process("test"));
    }

    @Test
    void testProtected_Filter_Pass() throws Exception {
        Pipeline<String, String> pipeline = Pipeline.<String>builder()
                .protect()
                .filter(s -> s.length() > 3)
                .build();

        assertEquals("test", pipeline.process("test"));
    }

    @Test
    void testProtected_Filter_Fail_ReturnsNull() throws Exception {
        Pipeline<String, String> pipeline = Pipeline.<String>builder()
                .protect()
                .filter(s -> s.length() > 10)
                .build();

        assertNull(pipeline.process("test"));
    }

    @Test
    void testProtected_BuildOpt_Success() throws Exception {
        Pipeline<String, Optional<String>> pipeline = Pipeline.<String>builder()
                .protect()
                .map(String::toUpperCase)
                .buildOpt();

        Optional<String> result = pipeline.process("test");
        assertTrue(result.isPresent());
        assertEquals("TEST", result.get());
    }

    @Test
    void testProtected_BuildOpt_EmptyOnFilterFail() throws Exception {
        Pipeline<String, Optional<String>> pipeline = Pipeline.<String>builder()
                .protect()
                .filter(s -> s.startsWith("A"))
                .buildOpt();

        Optional<String> result = pipeline.process("B");
        assertTrue(result.isEmpty());
    }

    @Test
    void testProtected_NextHandler_Integration() throws Exception {
        Pipeline<String, Integer> pipeline = Pipeline.<String>builder()
                .protect()
                .filter(s -> !s.isEmpty())
                .next(String::length)
                .build();

        assertEquals(4, pipeline.process("test"));
    }

    @Test
    void testProtected_NextHandler_SkippedOnFailure() throws Exception {
        Pipeline<String, Object> pipeline = Pipeline.<String>builder()
                .protect()
                .filter(_ -> false)
                .next(_ -> { throw new RuntimeException("Should not be executed"); })
                .build();

        assertNull(pipeline.process("test"));
    }

    @Test
    void testProtected_NextPipeline_Integration() throws Exception {
        Pipeline<String, Integer> subPipeline = Pipeline.builder(String::length).build();

        Pipeline<String, Integer> pipeline = Pipeline.<String>builder()
                .protect()
                .next(subPipeline)
                .build();

        assertEquals(4, pipeline.process("test"));
    }

    @Test
    void testProtected_ComplexChain() throws Exception {
        Pipeline<String, Integer> pipeline = Pipeline.<String>builder()
                .next(String::trim)
                .protect()
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .filter(s -> s.startsWith("H"))
                .map(String::length)
                .build();

        assertEquals(5, pipeline.process("  hello "));
        assertNull(pipeline.process("  bye "));
        assertNull(pipeline.process("   "));
    }

    @Test
    void testProtected_Or_Filter() throws Exception {
        Pipeline<Integer, Integer> pipeline = Pipeline.<Integer>builder()
                .protect()
                .filter(i -> i > 5)
                .or(() -> 100)
                .build();

        assertEquals(7, pipeline.process(7));
        assertEquals(100, pipeline.process(4));
    }

}
