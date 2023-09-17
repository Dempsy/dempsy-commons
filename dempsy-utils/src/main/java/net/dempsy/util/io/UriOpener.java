package net.dempsy.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * This is a helper that decouples Vfs from other places like dempsy-spring-utils.
 * In this way, dempsy-spring-utils can rely on OpenUri rather than Vfs directly
 * and you can then use spring-utils without Vfs simply by providing a URI to
 * InputStream function
 */
@FunctionalInterface
public interface UriOpener {
    public InputStream open(URI uri) throws IOException;
}
