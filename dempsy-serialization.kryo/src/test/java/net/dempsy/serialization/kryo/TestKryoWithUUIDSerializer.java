package net.dempsy.serialization.kryo;

import static net.dempsy.serialization.kryo.KryoTestUtils.defaultMock3Optimizer;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.serialization.TestSerializerImplementation;

@RunWith(Parameterized.class)
public class TestKryoWithUUIDSerializer extends TestSerializerImplementation {
    public TestKryoWithUUIDSerializer(final Boolean manageExactClass) {
        super(new KryoSerializer(manageExactClass.booleanValue(), defaultMock3Optimizer), false, manageExactClass.booleanValue());
    }

    @Parameterized.Parameters(name = "manage exact classes: {0}")
    public static Collection<Object[]> manageExactClassParams() {
        return Arrays.asList(new Object[][] {
                { new Boolean(true) },
                { new Boolean(false) }
        });
    }
}
