package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.dempsy.serialization.MockClass;
import net.dempsy.serialization.TestSerializerImplementation;
import net.dempsy.util.SystemPropertyManager;

public class TestKryoWithRegistration extends TestSerializerImplementation {

    private boolean manageExactClass;

    public static Stream<Object[]> manageExactClassParams() {
        return Stream.of(
            new Object[] {Boolean.TRUE},
            new Object[] {Boolean.FALSE}
        );
    }

    private void setup(final Boolean manageExactClass) {
        this.manageExactClass = manageExactClass.booleanValue();
        init(new KryoSerializer(this.manageExactClass, defaultMock3Optimizer, new Registration(MockClass.class.getName()),
            new Registration(Mock3.class.getName())), false, this.manageExactClass);
    }

    @Override @Disabled public void testJavaSerializeDeserialize() {}
    @Override @Disabled public void testMultithreadedSerialization() {}
    @Override @Disabled public void testWithFinalFields() {}
    @Override @Disabled public void testChildClassSerialization() {}

    @ParameterizedTest(name = "manage exact classes: {0}")
    @MethodSource("manageExactClassParams")
    public void testJavaSerializeDeserializeParameterized(final Boolean manageExactClass) throws Throwable {
        setup(manageExactClass);
        doTestJavaSerializeDeserialize();
    }

    @ParameterizedTest(name = "manage exact classes: {0}")
    @MethodSource("manageExactClassParams")
    public void testMultithreadedSerializationParameterized(final Boolean manageExactClass) throws Throwable {
        setup(manageExactClass);
        doTestMultithreadedSerialization();
    }

    @ParameterizedTest(name = "manage exact classes: {0}")
    @MethodSource("manageExactClassParams")
    public void testWithFinalFieldsParameterized(final Boolean manageExactClass) throws Throwable {
        setup(manageExactClass);
        doTestWithFinalFields();
    }

    @ParameterizedTest(name = "manage exact classes: {0}")
    @MethodSource("manageExactClassParams")
    public void testChildClassSerializationParameterized(final Boolean manageExactClass) throws Throwable {
        setup(manageExactClass);
        doTestChildClassSerialization();
    }

    @ParameterizedTest(name = "manage exact classes: {0}")
    @MethodSource("manageExactClassParams")
    public void testKryoDeserializeWithRegisterFail(final Boolean manageExactClass) throws Throwable {
        setup(manageExactClass);
        assertThrows(IOException.class, () -> {
            final KryoSerializer ser1 = new KryoSerializer(this.manageExactClass);
            final KryoSerializer ser2 = new KryoSerializer(this.manageExactClass);
            ser2.setKryoRegistrationRequired(true);
            final byte[] data = ser1.serialize(new MockClass());
            ser2.deserialize(data, MockClass.class);
        });
    }

    @ParameterizedTest(name = "manage exact classes: {0}")
    @MethodSource("manageExactClassParams")
    public void testWithRegisterFromResource(final Boolean manageExactClass) throws Throwable {
        setup(manageExactClass);
        try(@SuppressWarnings("resource")
        SystemPropertyManager p = new SystemPropertyManager()
            .set(KryoSerializer.SYS_PROP_REGISTRAION_RESOURCE, "kryo/registration.txt")) {
            final KryoSerializer ser1 = new KryoSerializer(this.manageExactClass);
            final KryoSerializer ser2 = new KryoSerializer(this.manageExactClass);
            ser2.setKryoRegistrationRequired(true);
            ser1.setKryoRegistrationRequired(true);
            final MockClass ser = new MockClass(43, "Yo");
            final byte[] data = ser1.serialize(ser);
            final MockClass dser = ser2.deserialize(data, MockClass.class);
            assertEquals(ser, dser);
        }
    }
}
