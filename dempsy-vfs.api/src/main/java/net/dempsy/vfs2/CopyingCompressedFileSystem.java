package net.dempsy.vfs2;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs2.internal.TempSpace;

public class CopyingCompressedFileSystem extends RecursiveFileSystem {
    // private static final Logger LOGGER = LoggerFactory.getLogger(CopyingCompressedFileSystem.class);

    private static final String CTX_KEY = CopyingCompressedFileSystem.class.getSimpleName();

    private final CompressedFileSystem underlying;
    private static final File TMP = TempSpace.get();

    public CopyingCompressedFileSystem(final CompressedFileSystem underlying) {
        this.underlying = underlying;
    }

    private static class Context implements QuietCloseable {
        File file;

        Context() {
            this.file = new File(TMP, UUID.randomUUID().toString());
        }

        @Override
        public void close() {
            FileUtils.deleteQuietly(file);
        }
    }

    @Override
    protected Path doCreatePath(final URI uri, final OpContext pctx) throws IOException {
        return new Path() {

            Path upath = underlying.doCreatePath(uri, pctx);

            @Override
            public OutputStream write() throws IOException {
                throw new UnsupportedOperationException("Copying/unpacking compressed files cannot be written to.");
            }

            @Override
            public URI uri() throws IOException {
                return upath.uri();
            }

            @Override
            public InputStream read() throws IOException {
                final Context c = setup(ctx);
                return new BufferedInputStream(new FileInputStream(c.file));
            }

            @Override
            protected Path[] list(final OpContext junk) throws IOException {
                return null;
            }

            @Override
            public long length() throws IOException {
                return upath.length();
            }

            @Override
            public long lastModifiedTime() throws IOException {
                return upath.lastModifiedTime();
            }

            @Override
            public boolean exists() throws IOException {
                return upath.exists();
            }

            @Override
            public boolean delete() throws IOException {
                return upath.delete();
            }

            @Override
            public File toFile() throws IOException {
                final Context c = setup(ctx);
                return c.file;
            }

            private synchronized Context setup(final OpContext ctx) throws IOException {
                final Context c = getContext(CTX_KEY, () -> new Context());
                if(!c.file.exists()) {
                    try(var is = upath.read();
                        var os = new BufferedOutputStream(new FileOutputStream(c.file))) {
                        IOUtils.copy(is, os);
                    }
                }
                return c;
            }
        };
    }

    @Override
    public String[] supportedSchemes() {
        return underlying.supportedSchemes();
    }

    @Override
    protected void setVfs(final Vfs vfs) {
        super.setVfs(vfs);
        underlying.setVfs(vfs);
    }

}
