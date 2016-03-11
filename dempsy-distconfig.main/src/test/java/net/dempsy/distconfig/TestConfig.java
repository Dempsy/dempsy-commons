package net.dempsy.distconfig;

import static net.dempsy.util.Functional.chainThrows;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.junit.Test;

import net.dempsy.cluster.zookeeper.ZookeeperTestServer;

public class TestConfig {

    @Test
    public void testSetAndGet() throws Exception {
        final PrintStream defaultOut = System.out;
        try (final ZookeeperTestServer server = new ZookeeperTestServer()) {
            System.setProperty("ZK_CONNECT", server.connectString());

            Config.main(new String[] { "push", "target/test-classes/test.properties" });

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));
            Config.main(new String[] { "read" });
            System.out.flush();
            assertEquals(chainThrows(new Properties(), p -> p.load(new FileInputStream("target/test-classes/test.properties"))),
                    chainThrows(new Properties(), p -> p.load(new ByteArrayInputStream(baos.toByteArray()))));

            Config.main(new String[] { "set", "hello=world2" });

            final ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos2));
            Config.main(new String[] { "read" });
            System.out.flush();
            assertEquals(chainThrows(new Properties(), p -> p.setProperty("hello", "world2")),
                    chainThrows(new Properties(), p -> p.load(new ByteArrayInputStream(baos2.toByteArray()))));

            Config.main(new String[] { "merge", "target/test-classes/test2.properties" });

            final ByteArrayOutputStream baos3 = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos3));
            Config.main(new String[] { "read" });
            System.out.flush();
            assertEquals(chainThrows(new Properties(), p -> p.setProperty("hello", "world2"), p -> p.setProperty("hello2", "world3")),
                    chainThrows(new Properties(), p -> p.load(new ByteArrayInputStream(baos3.toByteArray()))));
        } finally {
            System.clearProperty("ZK_CONNECT");
            System.setOut(defaultOut);
        }
    }
}
