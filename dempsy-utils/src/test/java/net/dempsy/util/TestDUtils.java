package net.dempsy.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.io.DUtils;

public class TestDUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDUtils.class);

    @Test
    public void testSystemTmpWoProp() throws Exception {

        final MutableInt result = new MutableInt(0);

        try(@SuppressWarnings("resource")
        var props = new SystemPropertyManager()
            .remove(DUtils.SYS_PROP_WITH_TMP_DIR);) {

            final File f = DUtils.systemTempDir(null);
            result.val = f.exists() ? 1 : 0;

        } catch(final IllegalStateException ise) {
            // this will happen on windows when java.io.tmpdir is unset.
            result.val = 1;
        }

        assertEquals(1, result.val);
    }

    @Test
    public void testSystemTmp() throws Exception {
        assertTrue(DUtils.systemTempDir(LOGGER).exists());
    }

}
