package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;

import java.io.IOException;

import org.junit.Test;

import net.dempsy.serialization.MockClass;
import net.dempsy.serialization.TestSerializerImplementation;

public class TestKryoWithRegistration extends TestSerializerImplementation {
    public TestKryoWithRegistration() {
        super(new KryoSerializer(defaultMock3Optimizer, new Registration(MockClass.class.getName()),
                new Registration(Mock3.class.getName())));
    }

    @Test(expected = IOException.class)
    public void testKryoDeserializeWithRegisterFail() throws Throwable {
        final KryoSerializer ser1 = new KryoSerializer();
        final KryoSerializer ser2 = new KryoSerializer();
        ser2.setKryoRegistrationRequired(true);
        final byte[] data = ser1.serialize(new MockClass());
        ser2.deserialize(data, MockClass.class);
    }

}
