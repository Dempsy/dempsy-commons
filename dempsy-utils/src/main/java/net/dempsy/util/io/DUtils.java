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

package net.dempsy.util.io;

import java.io.File;
import java.util.Optional;

import org.slf4j.Logger;

public class DUtils {

    private static final String FAILED_TO_GET_TMP_DIR = "Couldn't determine temp directory. Assuming /tmp";
    public static final String SYS_PROP_WITH_TMP_DIR = "java.io.tmpdir";

    public static File systemTempDir(final Logger logger) {
        return new File(Optional.ofNullable(System.getProperty(SYS_PROP_WITH_TMP_DIR)).orElseGet(() -> {
            try {
                final File f = File.createTempFile("prefix", "suffix");
                return Optional.ofNullable(f.getAbsoluteFile().getParent()).orElseGet(() -> {
                    if(logger != null)
                        logger.warn(FAILED_TO_GET_TMP_DIR);
                    final var ret = new File("/tmp");
                    if(!ret.exists())
                        throw new IllegalStateException("Couldn't determine temp directory.");
                    return "/tmp";
                });
            } catch(final Throwable ioe) { // yuck
                if(logger != null)
                    logger.warn(FAILED_TO_GET_TMP_DIR, ioe);
                final var ret = new File("/tmp");
                if(!ret.exists())
                    throw new IllegalStateException("Couldn't determine temp directory.");
                return "/tmp";
            }
        }));
    }

}
