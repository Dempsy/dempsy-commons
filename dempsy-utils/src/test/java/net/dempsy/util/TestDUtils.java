/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
