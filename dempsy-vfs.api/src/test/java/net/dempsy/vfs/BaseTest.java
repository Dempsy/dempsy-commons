package net.dempsy.vfs;

import java.io.IOException;

import net.dempsy.vfs.bz.Bz2FileSystem;
import net.dempsy.vfs.gz.GzFileSystem;
import net.dempsy.vfs.local.OptimizedZipFileSystem;
import net.dempsy.vfs.tar.TarFileSystem;
import net.dempsy.vfs.xz.XzFileSystem;
import net.dempsy.vfs.zip.ZipFileSystem;

public class BaseTest {
    public static final String tenc = TarFileSystem.ENC;
    public static final String zenc = ZipFileSystem.ENC;

    public static Vfs getVfs() throws IOException {
        return new Vfs(
            new TarFileSystem(),
            new GzFileSystem(),
            // new ZipFileSystem(),
            new OptimizedZipFileSystem(),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem()

        );
    }
}
