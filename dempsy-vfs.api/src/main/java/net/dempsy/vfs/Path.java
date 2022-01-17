package net.dempsy.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

public abstract class Path {
    private static final String SLASH = "/";

    protected Vfs vfs = null;

    void setVfs(final Vfs vfs) {
        this.vfs = vfs;
    }

    public static URI resolve(final URI base, final String child) {
        final String path = base.getPath();
        final String childStripped = child.startsWith(SLASH) ? child.substring(1) : child;
        final String newpath = path.endsWith(SLASH) ? (path + childStripped) : (path + SLASH + childStripped);
        try {
            return new URI(base.getScheme(), base.getAuthority(), newpath, base.getQuery(), base.getFragment());
        } catch(final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract URI uri();

    public abstract boolean exists() throws IOException;

    public abstract InputStream read() throws IOException;

    public abstract OutputStream write() throws IOException;

    public abstract void delete() throws IOException;

    public boolean handlesUngzipping() {
        return false;
    }

    /**
     * Makes the directory (and ancestors if necessary). This method should always throw an exception if the
     * directory doesn't exist when the method completes and NEVER throw an exception if it does.
     *
     * It will throw an {@link UnsupportedOperationException} when the FileSystem implementation doesn't
     * support creating new directories.
     */
    public void mkdirs() throws IOException {
        throw new UnsupportedOperationException("'mkdir' isn't supported for file system implementation " + this.getClass().getSimpleName());
    }

    /**
     * This should return {@code null} if the {@link Path} isn't a directory/folder.
     * An empty folder will be an array with no elements. A {@link FileNotFoundException}
     * will be thrown if the path doesn't exist at all.
     */
    public abstract Path[] list() throws IOException;

    public boolean isDirectory() throws IOException {
        return list() != null;
    }
}
