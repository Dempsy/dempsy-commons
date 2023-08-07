package net.dempsy.vfs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.function.Supplier;

import net.dempsy.util.QuietCloseable;

public abstract class Path {
    protected Vfs vfs = null;

    protected OpContext ctx;

    protected void setVfs(final Vfs vfs, final OpContext ctx) {
        this.vfs = vfs;
        this.ctx = ctx;
    }

    public abstract URI uri();

    public abstract boolean exists() throws IOException;

    public abstract boolean delete() throws IOException;

    public abstract long lastModifiedTime() throws IOException;

    public abstract long length() throws IOException;

    public abstract InputStream read() throws IOException;

    public abstract OutputStream write() throws IOException;

    /**
     * This should return {@code null} if the {@link Path} isn't a directory/folder.
     * An empty folder will be an array with no elements. A {@link FileNotFoundException}
     * will be thrown if the path doesn't exist at all.
     */
    protected abstract Path[] list(OpContext ctx) throws IOException;

    public Path[] list() throws IOException {
        return list(ctx);
    }

    public boolean isDirectory() throws IOException {
        return list() != null;
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

    public File toFile() throws IOException {
        return vfs.toFile(uri());
    }

    @Override
    public String toString() {
        return uri().toString();
    }

    /**
     * Helper for allowing implementors to set the vfs on newly create Paths
     */
    protected <T extends Path> T setVfs(final T p, final OpContext ctx) {
        p.setVfs(vfs, ctx);
        return p;
    }

    protected <T extends QuietCloseable> T getContext(final String key, final Supplier<T> computeIfAbsent) {
        return ctx.get(this, key, computeIfAbsent);
    }

}
