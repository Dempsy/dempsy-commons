package net.dempsy.vfs.local;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;

import net.dempsy.vfs.FileSystem;
import net.dempsy.vfs.Path;

public class LocalFileSystem extends FileSystem {

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
            return new FileInputStream(file);
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
