package net.dempsy.serialization.java;

import net.dempsy.serialization.TestSerializerImplementation;

public class TestJavaSerializer extends TestSerializerImplementation {
    public TestJavaSerializer() {
        super(new JavaSerializer());
    }
}
