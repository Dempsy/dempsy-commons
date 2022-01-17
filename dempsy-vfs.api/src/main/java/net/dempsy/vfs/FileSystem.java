package net.dempsy.vfs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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

    public File toFile(final URI uri) throws UnsupportedOperationException {
        return Paths.get(uri).toFile();
    }

    public abstract String[] supportedSchemes();

    @Override
    public abstract void close() throws IOException;
}
