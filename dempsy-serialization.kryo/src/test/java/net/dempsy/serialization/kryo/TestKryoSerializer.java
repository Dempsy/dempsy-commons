package net.dempsy.serialization.kryo;

import net.dempsy.serialization.TestSerializerImplementation;

public class TestKryoSerializer extends TestSerializerImplementation {
    public TestKryoSerializer() {
        super(new KryoSerializer(), true);
    }
}
