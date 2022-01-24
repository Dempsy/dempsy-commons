package net.dempsy.vfs;

import java.io.IOException;

public class BaseTest {
    public static final String tenc = TarFileSystem.ENC;
    public static final String zenc = ZipFileSystem.ENC;

    public static Vfs getVfs() throws IOException {
        return new Vfs(
            new TarFileSystem(),
            new GzFileSystem(),
            new ZipFileSystem(),
            new ZCompressedFileSystem(),
            new Bz2FileSystem()

        );
    }
}
