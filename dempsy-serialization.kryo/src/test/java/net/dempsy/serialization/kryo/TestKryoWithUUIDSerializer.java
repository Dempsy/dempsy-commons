package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;

import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.dempsy.serialization.TestSerializerImplementation;

public class TestKryoWithUUIDSerializer extends TestSerializerImplementation {

    public static Stream<Object[]> manageExactClassParams() {
        return Stream.of(
            new Object[] {Boolean.TRUE},
            new Object[] {Boolean.FALSE}
        );
    }

    private void setup(final Boolean manageExactClass) {
        init(new KryoSerializer(manageExactClass.booleanValue(), defaultMock3Optimizer), false, manageExactClass.booleanValue());
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
}
