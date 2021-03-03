package net.dempsy.serialization.kryo;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.dempsy.serialization.TestSerializerImplementation;

@RunWith(Parameterized.class)
public class TestKryoSerializer extends TestSerializerImplementation {
    public TestKryoSerializer(final Boolean manageExactClass) {
        super(new KryoSerializer(manageExactClass.booleanValue()), true, manageExactClass.booleanValue());
    }

    @Parameterized.Parameters(name = "manage exact classes: {0}")
    public static Collection<Object[]> manageExactClassParams() {
        return Arrays.asList(new Object[][] {
            {Boolean.TRUE},
            {Boolean.FALSE}
        });
    }
}
