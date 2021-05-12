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
