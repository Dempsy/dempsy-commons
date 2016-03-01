package net.dempsy.cluster.zookeeper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.dempsy.cluster.TestClusterImpls;
import net.dempsy.serialization.jackson.JsonSerializer;

public class TestZookeeperClusterImpl extends TestClusterImpls {
    public static ZookeeperTestServer server;

    @BeforeClass
    public static void setupServer() throws IOException {
        server = new ZookeeperTestServer();
    }

    @AfterClass
    public static void shutdown() {
        if (server != null)
            server.close();
    }

    public TestZookeeperClusterImpl() {
        super(new ZookeeperSessionFactory(server.connectString(), 5000, new JsonSerializer()));
    }

    KeeperException appropriateException = null;

    @Test
    public void testBadZooKeeperConnection() throws Throwable {
        Throwable receivedException = null;
        try (final ZookeeperSession session = new ZookeeperSession(new JsonSerializer(), server.connectString(), 5000) {

            @Override
            protected ZooKeeper makeZooKeeperClient(final String connectString, final int sessionTimeout) throws IOException {
                return new ZooKeeper(connectString, sessionTimeout, new ZkWatcher()) {
                    @Override
                    public List<String> getChildren(final String path, final Watcher watcher) throws KeeperException {
                        throw (appropriateException = new KeeperException(Code.DATAINCONSISTENCY) {
                            private static final long serialVersionUID = 1L;
                        });
                    }
                };
            }

        };) {

            try {
                session.getSubdirs("/test/test", null);
            } catch (final Exception e) {
                receivedException = e.getCause();
            }
        }

        assertNotNull(appropriateException);
        assertTrue(receivedException == appropriateException);
    }

}
