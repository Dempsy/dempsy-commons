package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.tika.Tika;

import net.dempsy.util.MimeUtils;
import net.dempsy.util.QuietCloseable;
import net.dempsy.util.UriUtils;
import net.dempsy.util.io.MegaByteBuffer;
import net.dempsy.util.io.MegaByteBufferInputStream;

public class FileSpec {

    private final Path path;
    private URI uriX = null;
    private String mime = null;

    private Header curHeader = null;
    private static final String RAR_MIME = "application/x-rar-compressed";
    private static final String DEFAULT_MIME = "application/octet-stream";

    private static final Set<String> MIMES_NAME_MATCHED_REQUIRED = Set.of(

    );

    private static final Set<String> MIME_NAMES_AND_MAGIC_MATCH_REQUIRED = Set.of(
        // too many files look like x-compress from the raw data that actually aren't.
        // This is probably because .Z LZW or LZH files only have a 2-byte magic number
        "application/x-compress",

        // Zip is used for many files that don't have a ".zip" extension that I don't care about traversing into.
        "application/zip"

    );

    /**
     * The point of this is that if you read a chunk of the file more than once,
     * then you wont go back to the disk. This is useful when scanning the files
     * because calculating the MD5 scans all of the data in the file leaving the
     * header full but then going back to get the MIME type only requires the first
     * few bytes.
     */
    private class Header implements QuietCloseable {
        private final byte[] buffer;
        private final int capacity;
        private int pos = 0;
        private boolean isActive = false;

        private Header(final int maxSize) {
            this.capacity = maxSize;
            this.buffer = new byte[capacity];
        }

        @Override
        public void close() {
            if(FileSpec.this.curHeader == this)
                FileSpec.this.curHeader = null;
        }

        public InputStream wrap(final InputStream is) {
            if(isActive)
                throw new IllegalStateException();
            return new InputStream() {
                {
                    isActive = true;
                }

                @Override
                public int read() throws IOException {
                    if(pos >= capacity)
                        return is.read();

                    final int read = is.read();
                    if(read < 0)
                        return read;

                    buffer[pos++] = (byte)(read & 0xff);
                    return read;
                }

                @Override
                public void close() throws IOException {
                    isActive = false;
                    is.close();
                }
            };
        }
    }

    public QuietCloseable preserveHeader(final int size) {
        if(curHeader != null)
            throw new IllegalStateException("Cannot have two active headers on a " + FileSpec.class.getSimpleName());
        curHeader = new Header(size);
        return curHeader;
    }

    public static class ByteBufferResource implements Closeable {
        private final MegaByteBuffer mbb;
        private final RandomAccessFile raf;

        private ByteBufferResource(final File file, final long sizeToMap) throws IOException {
            raf = new RandomAccessFile(file, "r");
            final FileChannel channel = raf.getChannel();
            mbb = MegaByteBuffer.allocateMaped(0L, sizeToMap, channel, FileChannel.MapMode.READ_ONLY);
        }

        public MegaByteBuffer getBuffer() {
            return mbb;
        }

        @Override
        public void close() throws IOException {
            if(raf != null)
                raf.close();
        }
    }

    public FileSpec(final Path path) {
        this.path = path;
        this.uriX = null;
    }

    public URI uri() throws IOException {

        if(uriX == null) {
            uriX = path.uri();
        }
        return uriX;
    }

    public boolean exists() throws IOException {

        return path.exists();
    }

    public boolean isDirectory() throws IOException {

        return path.isDirectory();
    }

    public boolean isRecursable() throws IOException {

        if(isDirectory())
            return true;

        final String lmime = mimeType();
        if(lmime != null && lmime.startsWith(RAR_MIME)) {
            // we need to make sure the file ends with .rar to handle multi-volume rar files correctly.
            final String uri = uri().toString();
            return uri.endsWith(".rar") && uri.indexOf(".part") < 0;
        } else
            return lmime != null && MimeUtils.recurseScheme(lmime) != null;
    }

    public long lastModifiedTime() throws IOException {

        return path.lastModifiedTime();
    }

    public long size() throws IOException {

        return path.length();
    }

    public boolean supportsMemoryMap() throws IOException {

        File file;
        try {
            file = path.toFile();
        } catch(final IOException ioe) {
            file = null;
        } catch(final FileSystemNotFoundException fsnfe) {
            file = null;
        } catch(final UnsupportedOperationException uoe) {
            file = null;
        } // any RTE?
        catch(final RuntimeException rte) {
            file = null;
        }

        return file != null;
    }

    @SuppressWarnings("resource")
    public InputStream getStandardInputStream() throws IOException {

        if(curHeader != null)
            return curHeader.wrap(new BufferedInputStream(path.read()));
        return new BufferedInputStream(path.read());
    }

    public InputStream getEfficientInputStream() throws IOException {

        return getEfficientInputStream(path.length());
    }

    public InputStream getEfficientInputStream(final long numBytes) throws IOException {

        if(supportsMemoryMap()) {
            final ByteBufferResource resource = mapFile(numBytes);
            return new MegaByteBufferInputStream(resource.mbb) {

                @Override
                public void close() throws IOException {
                    resource.close();
                    super.close();
                }
            };
        } else
            return getStandardInputStream();
    }

    public ByteBufferResource mapFile() throws IOException {

        return mapFile(path.length());
    }

    public ByteBufferResource mapFile(final long bytesToMap) throws IOException {

        if(!supportsMemoryMap())
            throw new UnsupportedOperationException("The file system \"" + uri().getScheme() + "\" doesn't support memory mapping.");
        return new ByteBufferResource(toFile(), Math.min(path.length(), bytesToMap));
    }

    /**
     * @throws IOException
     * @throws FileSystemNotFoundException when the underlying java file system isn't supported by the
     *     built in java Paths functionality.
     */
    public File toFile() throws IOException {

        return path.toFile();
    }

    public String mimeType() throws IOException {

        return mimeType(DEFAULT_MIME);
    }

    public String mimeType(final String defaultValue) throws IOException {

        if(mime == null) {
            final Tika tika = new Tika();
            final String name = UriUtils.getName(uri());
            if(curHeader != null && curHeader.pos != 0) {
                try(var is = new ByteArrayInputStream(curHeader.buffer, 0, curHeader.pos);) {
                    final String ret = tika.detect(is, name);
                    mime = ret == null ? defaultValue : ret;
                }
            } else {
                try(var is = getEfficientInputStream(4096);) {
                    final String ret = tika.detect(is, name);
                    mime = ret == null ? defaultValue : ret;
                } catch(final IOException ioe) {
                    final String ret = tika.detect(name);
                    mime = ret == null ? defaultValue : ret;
                }
            }

            // exceptions.
            if(mime != null) {
                if(MIMES_NAME_MATCHED_REQUIRED.contains(mime)) {
                    final var tmime = tika.detect(name);
                    if(!mime.equals(tmime))
                        mime = defaultValue;
                } else if(MIME_NAMES_AND_MAGIC_MATCH_REQUIRED.contains(mime)) {
                    try(var is = getEfficientInputStream(4096 * 4);) {
                        final String ret = tika.detect(is);
                        if(ret == null)
                            mime = defaultValue;
                        else {
                            final var tmime = tika.detect(name);
                            mime = ret.equals(tmime) ? ret : defaultValue;
                        }
                    } catch(final IOException ioe) {
                        mime = defaultValue;
                    }
                }
            }
        }
        return mime;
    }

    public void delete() throws IOException {

        path.delete();
    }

    public FileSpec[] list(final OpContext oc) throws IOException {
        if(path.isDirectory())
            return Arrays.stream(path.list())
                .map(p -> new FileSpec(p))
                .toArray(FileSpec[]::new);
        else {
            final String recurseScheme = MimeUtils.recurseScheme(mimeType());
            if(recurseScheme == null)
                return new FileSpec[0];
            final URI newPath = uncheck(() -> UriUtils.prependScheme(recurseScheme, path.uri()));
            return new FileSpec[] {new FileSpec(oc.toPath(newPath))};
        }
    }

    public FileSpec[] listSorted(final OpContext oc) throws IOException {

        if(path.isDirectory()) {
            final List<QuietCloseable> toClose = new ArrayList<>();

            try(var qc = (QuietCloseable)() -> toClose.forEach(q -> q.close());) {
                final var ret = Arrays.stream(path.list())
                    .map(p -> new FileSpec(p))
                    .sorted((o1, o2) -> uncheck(() -> o1.uri().toString().compareTo(o2.uri().toString())))
                    .toArray(FileSpec[]::new);

                return ret;
            }
        } else {
            final String recurseScheme = MimeUtils.recurseScheme(mimeType());
            if(recurseScheme == null)
                return new FileSpec[0];
            final URI newPath = uncheck(() -> UriUtils.prependScheme(recurseScheme, path.uri()));
            return new FileSpec[] {new FileSpec(oc.toPath(newPath))};
        }
    }

    public void setMime(final String mime) {
        this.mime = mime;
    }

    public BasicFileAttributes getAttr() throws IOException {
        if(!supportsMemoryMap())
            return null;
        return Files.readAttributes(
            toFile().toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public String toString() {
        return uncheck(() -> uri()).toString();
    }
}
