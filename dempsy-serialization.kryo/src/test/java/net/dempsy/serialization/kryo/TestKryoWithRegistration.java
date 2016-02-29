package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.serialization.MockClass;
import net.dempsy.serialization.TestSerializerImplementation;

@RunWith(Parameterized.class)
public class TestKryoWithRegistration extends TestSerializerImplementation {
    private final boolean manageExactClass;

    public TestKryoWithRegistration(final Boolean manageExactClass) {
        super(new KryoSerializer(manageExactClass.booleanValue(), defaultMock3Optimizer, new Registration(MockClass.class.getName()),
                new Registration(Mock3.class.getName())), false, manageExactClass.booleanValue());
        this.manageExactClass = manageExactClass.booleanValue();
    }

    @Test(expected = IOException.class)
    public void testKryoDeserializeWithRegisterFail() throws Throwable {
        final KryoSerializer ser1 = new KryoSerializer(manageExactClass);
        final KryoSerializer ser2 = new KryoSerializer(manageExactClass);
        ser2.setKryoRegistrationRequired(true);
        final byte[] data = ser1.serialize(new MockClass());
        ser2.deserialize(data, MockClass.class);
    }

    @Parameterized.Parameters(name = "manage exact classes: {0}")
    public static Collection<Object[]> manageExactClassParams() {
        return Arrays.asList(new Object[][] {
                { new Boolean(true) },
                { new Boolean(false) }
        });
    }

}
