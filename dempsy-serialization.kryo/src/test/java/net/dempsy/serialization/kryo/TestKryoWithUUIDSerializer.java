package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;

import net.dempsy.serialization.TestSerializerImplementation;

public class TestKryoWithUUIDSerializer extends TestSerializerImplementation {
    public TestKryoWithUUIDSerializer() {
        super(new KryoSerializer(defaultMock3Optimizer));
    }
}
