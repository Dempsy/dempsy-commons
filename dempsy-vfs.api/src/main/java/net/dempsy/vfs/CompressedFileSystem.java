package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import net.dempsy.util.UriUtils;

public abstract class CompressedFileSystem extends RecursiveFileSystem {

    protected abstract InputStream wrap(InputStream is) throws IOException;

    protected abstract OutputStream wrap(OutputStream os) throws IOException;

    @Override
    protected Path doCreatePath(final URI uri) throws IOException {

        final URI hackedUri = uri;
        final String otherThanScheme = hackedUri.getSchemeSpecificPart();
        final URI otherThanSchemeUri = uncheck(() -> UriUtils.sanitize(otherThanScheme));
        final Path innerPath = vfs.toPath(otherThanSchemeUri);

        return new Path() {

            @Override
            public OutputStream write() throws IOException {
                return wrap(innerPath.write());
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public InputStream read() throws IOException {
                return wrap(innerPath.read());
            }

            // compressed filesystems paths are never directories. Think gzip, not zip or tar.
            @Override
            public Path[] list() throws IOException {
                return null;
            }

            @Override
            public long lastModifiedTime() throws IOException {
                return innerPath.lastModifiedTime();
            }

            @Override
            public boolean exists() throws IOException {
                return innerPath.exists();
            }

            @Override
            public void delete() throws IOException {
                innerPath.delete();
            }

            @Override
            public long length() throws IOException {
                return innerPath.length();
            }
        };
    }

    @Override
    public void close() throws IOException {}

}
