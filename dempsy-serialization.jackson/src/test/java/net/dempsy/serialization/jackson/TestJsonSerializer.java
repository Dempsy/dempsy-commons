package net.dempsy.serialization.jackson;

import net.dempsy.serialization.TestSerializerImplementation;

public class TestJsonSerializer extends TestSerializerImplementation {
    public TestJsonSerializer() {
        super(new JsonSerializer());
    }
}
