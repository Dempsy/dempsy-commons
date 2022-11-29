package net.dempsy.vfs.internal;

import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempSpace {
    private static final Logger LOGGER = LoggerFactory.getLogger(TempSpace.class);

    static final File TMP;

    static {
        final String v = System.getProperty("java.io.tmpdir");
        if(v == null) { // I have no idea on what system this can be null, if any.
            TMP = uncheck(() -> Files.createTempDirectory("TMP").toFile());
        } else {
            final File tmpRoot = new File(v);
            TMP = new File(tmpRoot, "CFS");
        }
        LOGGER.debug("Temp directory for Dempsy vfs is \"{}\"", TMP);
        if(!TMP.exists()) {
            if(!TMP.mkdirs())
                throw new IllegalStateException("Cannot find or make the system's temp directory");
        }

        Arrays.stream(TMP.listFiles()).forEach(f -> FileUtils.deleteQuietly(f));
    }

    public static File get() {
        return TMP;
    }

}
