package org.machinemc.foundry;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.machinemc.foundry.util.Token;
import org.machinemc.foundry.util.TypeUtils;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TypeUtilsTest {

    @Test
    void testIsCompatible_SameClass() {
        assertTrue(TypeUtils.isCompatible(String.class, String.class));
    }

    @Test
    void testIsCompatible_Superclass() {
        assertTrue(TypeUtils.isCompatible(Object.class, String.class));
    }

    @Test
    void testIsCompatible_Interface() {
        assertTrue(TypeUtils.isCompatible(Serializable.class, String.class));
    }

    @Test
    void testIsCompatible_PrimitiveToWrapper() {
        assertTrue(TypeUtils.isCompatible(Integer.class, int.class));
    }

    @Test
    void testIsCompatible_WrapperToPrimitive() {
        assertTrue(TypeUtils.isCompatible(int.class, Integer.class));
    }

    @Test
    void testIsCompatible_SamePrimitive() {
        assertTrue(TypeUtils.isCompatible(int.class, int.class));
    }

    @Test
    void testIsCompatible_IncompatiblePrimitives() {
        assertFalse(TypeUtils.isCompatible(int.class, boolean.class));
        assertFalse(TypeUtils.isCompatible(int.class, long.class));
    }

    @Test
    void testIsCompatible_ParameterizedType_Same() {
        Type listOfStrings = new Token<List<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(listOfStrings, listOfStrings));
    }

    @Test
    void testIsCompatible_ParameterizedType_Subclass() {
        Type listOfStrings = new Token<List<String>>() {}.get().getType();
        Type arrayListOfStrings = new Token<ArrayList<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(listOfStrings, arrayListOfStrings));
    }

    @Test
    void testIsCompatible_ParameterizedType_Wildcard() {
        Type listOfWildcard = new Token<List<?>>() {}.get().getType();
        Type listOfStrings = new Token<List<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(listOfWildcard, listOfStrings));
    }

    @Test
    void testIsCompatible_ParameterizedType_Incompatible() {
        Type listOfIntegers = new Token<List<Integer>>() {}.get().getType();
        Type listOfStrings = new Token<List<String>>() {}.get().getType();
        assertFalse(TypeUtils.isCompatible(listOfIntegers, listOfStrings));
    }

    @Test
    void testIsCompatible_RawTypeWithParameterizedType() {
        Type arrayListOfStrings = new Token<ArrayList<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(List.class, arrayListOfStrings));
    }

    @Test
    void testIsCompatible_ParameterizedTypeWithRawType() {
        Type listOfStrings = new Token<List<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(listOfStrings, ArrayList.class));
    }

    @Test
    void testIsCompatible_GenericArrayType_Same() {
        Type arrayOfListOfStrings = new Token<List<String>[]>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(arrayOfListOfStrings, arrayOfListOfStrings));
    }

    @Test
    void testIsCompatible_GenericArrayType_Subclass() {
        Type arrayOfListOfStrings = new Token<List<String>[]>() {}.get().getType();
        Type arrayOfArrayListOfStrings = new Token<ArrayList<String>[]>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(arrayOfListOfStrings, arrayOfArrayListOfStrings));
    }

    @Test
    void testIsCompatible_GenericArrayType_Wildcard() {
        Type arrayOfListOfWildcard = new Token<List<?>[]>() {}.get().getType();
        Type arrayOfArrayListOfStrings = new Token<ArrayList<String>[]>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(arrayOfListOfWildcard, arrayOfArrayListOfStrings));
    }

    @Test
    void testIsCompatible_GenericArrayType_Incompatible() {
        Type arrayOfListOfInteger = new Token<List<Integer>[]>() {}.get().getType();
        Type arrayOfArrayListOfStrings = new Token<ArrayList<String>[]>() {}.get().getType();
        assertFalse(TypeUtils.isCompatible(arrayOfListOfInteger, arrayOfArrayListOfStrings));
    }

    @Test
    void testIsCompatible_Wildcard_UpperBound() {
        Type wildcardExtendsNumber = new Token<List<? extends Number>>() {}.get().getType();
        Type listOfInteger = new Token<List<Integer>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(wildcardExtendsNumber, listOfInteger));
    }

    @Test
    void testIsCompatible_Wildcard_LowerBound() {
        Type wildcardSuperInteger = new Token<List<? super Integer>>() {}.get().getType();
        Type listOfNumber = new Token<List<Number>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(wildcardSuperInteger, listOfNumber));
    }

    @Test
    void testIsCompatible_Wildcard_Unbounded() {
        Type unboundedWildcard = new Token<List<?>>() {}.get().getType();
        Type listOfString = new Token<List<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(unboundedWildcard, listOfString));
    }

    @Test
    void testIsCompatible_Wildcard_Incompatible() {
        Type wildcardExtendsNumber = new Token<List<? extends Number>>() {}.get().getType();
        Type listOfString = new Token<List<String>>() {}.get().getType();
        assertFalse(TypeUtils.isCompatible(wildcardExtendsNumber, listOfString));
    }

    @Test
    <T_GENERIC extends Number> void testIsCompatible_TypeVariable_Simple() {
        Type typeVariable = new Token<T_GENERIC>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(typeVariable, Integer.class));
    }

    @Test
    <T_GENERIC extends List<String>> void testIsCompatible_TypeVariable_WithBound() {
        Type typeVariable = new Token<T_GENERIC>() {}.get().getType();
        Type arrayListOfStrings = new Token<ArrayList<String>>() {}.get().getType();
        assertTrue(TypeUtils.isCompatible(typeVariable, arrayListOfStrings));
    }

    @Test
    <T_GENERIC extends Number> void testIsCompatible_TypeVariable_Incompatible() {
        Type typeVariable = new Token<T_GENERIC>() {}.get().getType();
        assertFalse(TypeUtils.isCompatible(typeVariable, String.class));
    }

    @Test
    void testIsCompatible_NullExpected() {
        assertThrows(NullPointerException.class, () -> TypeUtils.isCompatible(null, String.class));
    }

    @Test
    void testIsCompatible_NullActual() {
        assertThrows(NullPointerException.class, () -> TypeUtils.isCompatible(String.class, null));
    }

    @Test
    void testGetRawType_Class() {
        assertEquals(String.class, TypeUtils.getRawType(String.class));
    }

    @Test
    void testGetRawType_ParameterizedType() {
        Type listOfStrings = new Token<List<String>>() {}.get().getType();
        assertEquals(List.class, TypeUtils.getRawType(listOfStrings));
    }

    @Test
    void testGetRawType_GenericArrayType() {
        Type arrayOfListOfStrings = new Token<List<String>[]>() {}.get().getType();
        assertEquals(List[].class, TypeUtils.getRawType(arrayOfListOfStrings));
    }

    @Test
    <T_GENERIC extends Number> void testGetRawType_TypeVariable() {
        Type typeVariable = new Token<T_GENERIC>() {}.get().getType();
        assertEquals(Number.class, TypeUtils.getRawType(typeVariable));
    }

    @Test
    void testGetRawType_WildcardType() {
        Type listOfWildcard = new Token<List<? extends Number>>() {}.get().getType();
        Type wildcard = ((ParameterizedType) listOfWildcard).getActualTypeArguments()[0];
        assertEquals(Number.class, TypeUtils.getRawType(wildcard));
    }

    @Test
    void testGetRawType_NestedParameterizedType() {
        Type nested = new Token<Map<String, List<Integer>>>() {}.get().getType();
        assertEquals(Map.class, TypeUtils.getRawType(nested));
    }

    @Test
    void testGetRawType_FromAnnotatedType() {
        var token = new Token<List<String>>() {};
        assertEquals(List.class, TypeUtils.getRawType(token.get()));
    }

    @Test
    @SuppressWarnings("all")
    void testGetRawType_NullType() {
        assertThrows(NullPointerException.class, () -> TypeUtils.getRawType((Type) null));
    }

    @Test
    void testGetAnnotation_Token() {
        var token = new Token<@Nullable /* we use jspecify just here for the test */ String>() {};
        assertTrue(token.get().isAnnotationPresent(Nullable.class));
    }

}
