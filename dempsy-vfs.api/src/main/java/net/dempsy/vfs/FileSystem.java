package net.dempsy.vfs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * This abstract class is the parent for the various means of converting from a scheme
 * to an instance of a {@link Path}, which is the means by which the file system
 * is accessed.
 */
public abstract class FileSystem implements AutoCloseable {
    protected Vfs vfs = null;

    /**
     * This is called automatically by the {@link Vfs} instance as part of registering
     * the file system.
     */
    void setVfs(final Vfs vfs) {
        this.vfs = vfs;
    }

    /**
     * The main public interface to convert a URI of a file or directory to a {@link Path}.
     */
    public final Path createPath(final URI uri) throws IOException {
        final Path path = doCreatePath(uri);
        if(path != null)
            path.setVfs(vfs);
        return path;
    }

    protected abstract Path doCreatePath(URI uri) throws IOException;

    protected Vfs getVfs() {
        return vfs;
    }

    public File toFile(final URI uri) throws UnsupportedOperationException {
        return Paths.get(uri).toFile();
    }

    public abstract String[] supportedSchemes();

    public static class SplitUri {
        public final URI baseUri;
        public final String remainder;

        public SplitUri(final URI baseUri, final String remainder) {
            this.baseUri = baseUri;
            this.remainder = remainder;
        }
    }

    /**
     * By default this will split the URI around the outerEnc. For example, if the URI is:
     * <p>
     * file:///path/to/file.tar!inside/archive/file.xyz
     * <p>
     * With an enc of '!' then The result will be:
     * <p>
     * { innerUri: file:///path/to/file.tar , remainder = inside/archive/file.xyz }
     * <p>
     * Notice, the remainder DOES NOT include the enc.
     * <p>
     * Recursive URIs will return slightly different results.
     */
    public SplitUri splitUri(final String uri, final String outerEnc) throws URISyntaxException {
        final int encIndex = uri.indexOf(outerEnc);
        if(encIndex < 0)
            return new SplitUri(new URI(uri), "");
        return new SplitUri(new URI(uri.substring(0, encIndex)), uri.substring(encIndex + 1));
    }

    @Override
    public abstract void close() throws IOException;
}
