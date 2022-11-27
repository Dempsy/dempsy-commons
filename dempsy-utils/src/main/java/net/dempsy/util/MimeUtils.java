package net.dempsy.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MimeUtils {
    private static final Map<String, String> mimeToScheme = Map.of(
        "application/gzip", "gz",
        "application/x-compress", "Z",
        "application/x-bzip2", "bz2",
        "application/x-gtar", "tar",
        "application/x-tar", "tar",
        "application/zip", "zip",
        "application/x-7z-compressed", "sevenz",
        "application/x-xz", "xz",
        "application/java-archive", "jar",
        "application/x-rar-compressed", "rar"

    );

    private static final Map<String, String> beginsWith = Map.of(
        "application/x-rar-compressed;", "rar"

    );

    private static final Set<String> schemeIsCompressed = new HashSet<>(Arrays.asList(
        "gz",
        "Z",
        "xz",
        "b2z"

    ));

    private static final Set<String> schemeIsArchve = new HashSet<>(Arrays.asList(
        "file",
        "tar",
        "zip",
        "jar",
        "sevenz",
        "rar"

    ));

    public static boolean isArchive(final String mime) {
        return Optional.ofNullable(mimeToScheme.get(mime)).map(s -> schemeIsArchve.contains(s)).orElse(false);
    }

    public static boolean isCompressed(final String mime) {
        return Optional.ofNullable(mimeToScheme.get(mime)).map(s -> schemeIsCompressed.contains(s)).orElse(false);
    }

    public static String recurseScheme(final String mime) {
        if(mime == null)
            return null;
        final String scheme = mimeToScheme.get(mime);
        if(scheme == null) {
            for(final var e: beginsWith.entrySet()) {
                if(mime.startsWith(e.getKey()))
                    return e.getValue();
            }
        }
        return scheme;
    }
}
