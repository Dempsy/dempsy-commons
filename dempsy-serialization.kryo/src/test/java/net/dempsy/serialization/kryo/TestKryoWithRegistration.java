package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.serialization.MockClass;
import net.dempsy.serialization.TestSerializerImplementation;
import net.dempsy.utils.test.SystemPropertyManager;

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

    @Test
    public void testWithRegisterFromResource() throws Throwable {
        try(@SuppressWarnings("resource")
        SystemPropertyManager p = new SystemPropertyManager()
            .set(KryoSerializer.SYS_PROP_REGISTRAION_RESOURCE, "kryo/registration.txt")) {
            final KryoSerializer ser1 = new KryoSerializer(manageExactClass);
            final KryoSerializer ser2 = new KryoSerializer(manageExactClass);
            ser2.setKryoRegistrationRequired(true);
            ser1.setKryoRegistrationRequired(true);
            final MockClass ser = new MockClass(43, "Yo");
            final byte[] data = ser1.serialize(ser);
            final MockClass dser = ser2.deserialize(data, MockClass.class);
            assertEquals(ser, dser);
        }
    }

    @Parameterized.Parameters(name = "manage exact classes: {0}")
    public static Collection<Object[]> manageExactClassParams() {
        return Arrays.asList(new Object[][] {
            {Boolean.TRUE},
            {Boolean.FALSE}
        });
    }
}
