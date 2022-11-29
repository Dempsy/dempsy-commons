package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.UriUtils;

// TODO: This is broken when multiply nested.
public class SevenZCompressFileSystem extends RecursiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SevenZCompressFileSystem.class);
    private final String[] schemes;

    private final SevenZArchiveFileSystem underlying;

    public SevenZCompressFileSystem(final String scheme1, final String... schemes2) {
        schemes = Stream.concat(Stream.of(scheme1), Arrays.stream(schemes2))
            .filter(i -> i != null)
            .toArray(String[]::new);

        underlying = new SevenZArchiveFileSystem(scheme1, schemes2);
        underlying.noCaching();
    }

    @Override
    public String[] supportedSchemes() {
        return schemes;
    }

    @Override
    void setVfs(final Vfs vfs) {
        super.setVfs(vfs);
        underlying.setVfs(vfs);
    }

    @Override
    protected Path doCreatePath(final URI uri) throws IOException {

        final URI hackedUri = uri;
        final String otherThanScheme = hackedUri.getSchemeSpecificPart();
        final URI otherThanSchemeUri = uncheck(() -> UriUtils.sanitize(otherThanScheme));
        final Path innerPath = vfs.toPath(otherThanSchemeUri);

        return setVfs(new Path() {

            Path szUnderlying = null;

            @Override
            public OutputStream write() throws IOException {
                throw new UnsupportedOperationException("tar file entries are not writable.");
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public InputStream read() throws IOException {
                if(szUnderlying == null)
                    set7zUnderlying();
                return szUnderlying.read();
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
            public boolean delete() throws IOException {
                return innerPath.delete();
            }

            @Override
            public long length() throws IOException {
                return innerPath.length();
            }

            private void set7zUnderlying() throws IOException {
                final var parent = underlying.createPath(uri);
                if(!parent.isDirectory())
                    throw new IllegalStateException("The underlying 7z archive rep should look like an archive.");
                final Path[] children = parent.list();
                if(children == null)
                    throw new IllegalStateException("The underlying 7z archive rep should look like an archive but has no contents.");
                if(children.length != 1)
                    throw new IllegalStateException("The underlying 7z archive rep should look like an archive with a single subdir.");
                szUnderlying = children[0];
            }
        });
    }

}
