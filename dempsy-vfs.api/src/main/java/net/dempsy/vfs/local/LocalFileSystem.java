package net.dempsy.vfs.local;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.io.MegaByteBuffer;
import net.dempsy.util.io.MegaByteBufferInputStream;
import net.dempsy.util.io.MessageBufferOutput;
import net.dempsy.vfs.FileSystem;
import net.dempsy.vfs.Path;

public class LocalFileSystem extends FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileSystem.class);

    public static final int DEFAULT_MAX_CACHED_FILE_SIZE = 1024 * 1024 * 1024;

    private static boolean cachingEnabled = false;
    private static int maxFileSizeToCache = -1;

    private static File cachedFile = null;
    private static MessageBufferOutput cache = null;

    public static void enableCaching() {
        enableCaching(true, DEFAULT_MAX_CACHED_FILE_SIZE);
    }

    public static void enableCaching(final boolean enableCaching) {
        enableCaching(enableCaching, DEFAULT_MAX_CACHED_FILE_SIZE);
    }

    public static void enableCaching(final int maxFileSizeToCache) {
        enableCaching(true, maxFileSizeToCache);
    }

    public static void enableCaching(final boolean enableCaching, final int maxFileSizeToCache) {
        cachingEnabled = enableCaching;
        LocalFileSystem.maxFileSizeToCache = maxFileSizeToCache;
    }

    public static void disableCaching() {
        cachingEnabled = false;
        LocalFileSystem.maxFileSizeToCache = -1;
        if(cachedFile != null)
            cachedFile = null;
        if(cache != null)
            cache = null;
    }

    public static class LocalFile extends Path {
        private final File file;

        public LocalFile(final File file) {
            this.file = file;
        }

        @Override
        public OutputStream write() throws IOException {
            return new FileOutputStream(file);
        }

        @Override
        public URI uri() {
            return file.toURI();
        }

        @Override
        public InputStream read() throws IOException {
            final long fileLen = file.length();
            if(cachingEnabled && fileLen <= maxFileSizeToCache) {
                if(file.equals(cachedFile) && cache != null) {
                    if(LOGGER.isTraceEnabled())
                        LOGGER.trace("Cached: {}", file.getAbsolutePath());
                    return new ByteArrayInputStream(cache.getBuffer(), 0, cache.getPosition());
                } else {
                    if(LOGGER.isTraceEnabled())
                        LOGGER.trace("Caching: {}", file.getAbsolutePath());
                    cachedFile = file;
                    if(cache != null && cache.getBuffer().length >= fileLen)
                        // we can reuse this buffer
                        cache.reset();
                    else {
                        // otherwise recreate the buffer from scratch
                        if(cache != null) {
                            cache = null;
                            // we just dropped a large chunk of memory
                            System.gc(); // not great but helps on qnap's ARM chip
                        }
                        cache = new MessageBufferOutput((int)fileLen);
                    }
                    IOUtils.copy(getEfficientInputStream(fileLen), cache);
                    return new ByteArrayInputStream(cache.getBuffer(), 0, cache.getPosition());
                }
            } else {
                if(LOGGER.isTraceEnabled())
                    LOGGER.trace("Reading: {}", file.getAbsolutePath());
                return getEfficientInputStream(length());
            }
        }

        @Override
        public Path[] list() throws IOException {
            return Arrays.stream(file.list())
                .map(subdir -> new File(file, subdir))
                .map(f -> setVfs(new LocalFile(f)))
                .toArray(Path[]::new);
        }

        @Override
        public long length() throws IOException {
            return file.length();
        }

        @Override
        public long lastModifiedTime() throws IOException {
            return file.lastModified();
        }

        @Override
        public boolean exists() throws IOException {
            return file.exists();
        }

        @Override
        public boolean delete() throws IOException {
            return file.delete();
        }

        @Override
        public boolean isDirectory() throws IOException {
            return file.isDirectory();
        }

        private static class ByteBufferResource implements Closeable {
            private final MegaByteBuffer mbb;
            private final RandomAccessFile raf;

            private ByteBufferResource(final File file, final long sizeToMap) throws IOException {
                raf = new RandomAccessFile(file, "r");
                final FileChannel channel = raf.getChannel();
                mbb = MegaByteBuffer.allocateMaped(0L, sizeToMap, channel, FileChannel.MapMode.READ_ONLY);
            }

            @Override
            public void close() throws IOException {
                if(raf != null)
                    raf.close();
            }
        }

        public ByteBufferResource mapFile() throws IOException {
            return mapFile(length());
        }

        public InputStream getEfficientInputStream(final long numBytes) throws IOException {
            final ByteBufferResource resource = mapFile(numBytes);
            return new MegaByteBufferInputStream(resource.mbb) {

                @Override
                public void close() throws IOException {
                    resource.close();
                    super.close();
                }
            };
        }

        private ByteBufferResource mapFile(final long bytesToMap) throws IOException {
            return new ByteBufferResource(toFile(), Math.min(length(), bytesToMap));
        }

    }

    public final static String[] SCHEMES = {"file"};

    @Override
    protected Path doCreatePath(final URI uri) throws IOException {
        return new LocalFile(Paths.get(uri).toFile());
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

}
