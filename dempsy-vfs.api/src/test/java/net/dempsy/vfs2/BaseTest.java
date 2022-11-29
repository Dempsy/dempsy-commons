package net.dempsy.vfs2;

import static net.dempsy.util.Functional.uncheck;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized;

import net.dempsy.vfs2.bz.Bz2FileSystem;
import net.dempsy.vfs2.gz.GzFileSystem;
import net.dempsy.vfs2.tar.TarFileSystem;
import net.dempsy.vfs2.xz.XzFileSystem;
import net.dempsy.vfs2.zip.ZipFileSystem;

public class BaseTest {
    public static final String tenc = TarFileSystem.ENC;
    public static final String zenc = ZipFileSystem.ENC;

    @Parameterized.Parameters
    public static Collection<Object[]> vfsConfigs() {
        return Arrays.asList(new Object[][] {
            {getCopyVfs()},
            {getStreamVfs()},
            {get7zPreferredVfs()},
            {get7zPreferredAltCopyingVfs()},
            // {getExtreme7zPreferredVfs()},
        });
    }

    private final Vfs vfs;

    public BaseTest(final Vfs vfs) {
        this.vfs = vfs;
    }

    public Vfs getVfs() {
        return vfs;
    }

    public static String removeTrailingSlash(final String str) {
        return str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
    }

    public static URI removeTrailingSlash(final URI uri) {
        return uncheck(() -> new URI(removeTrailingSlash(uri.toString())));
    }

    private static Vfs getCopyVfs() {
        return uncheck(() -> new Vfs(
            new CopyingCompressedFileSystem(new GzFileSystem())

            , new CopyingArchiveFileSystem(new TarFileSystem())

            , new CopyingArchiveFileSystem(new ZipFileSystem())

            , new CopyingCompressedFileSystem(new ZCompressedFileSystem())

            , new CopyingCompressedFileSystem(new Bz2FileSystem())

            , new CopyingCompressedFileSystem(new XzFileSystem())

            , new SevenZArchiveFileSystem()

        ));
    }

    private static Vfs getStreamVfs() {
        return uncheck(() -> new Vfs(
            new TarFileSystem()

            , new GzFileSystem()

            , new ZipFileSystem()

            , new ZCompressedFileSystem()

            , new Bz2FileSystem()

            , new XzFileSystem()

            , new SevenZArchiveFileSystem()

        ));
    }

    private static Vfs get7zPreferredVfs() {
        return uncheck(() -> new Vfs(
            new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz", "zip"),
            new GzFileSystem(),
            new ZCompressedFileSystem(),
            new Bz2FileSystem()

            , new XzFileSystem()

        ));
    }

    private static Vfs get7zPreferredAltCopyingVfs() {
        return uncheck(() -> new Vfs(
            new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz", "zip"),
            new CopyingCompressedFileSystem(new GzFileSystem()),
            new CopyingCompressedFileSystem(new ZCompressedFileSystem()),
            new CopyingCompressedFileSystem(new Bz2FileSystem())

            , new CopyingCompressedFileSystem(new XzFileSystem())

        ));
    }

    // doesn't work yet.
    // @SuppressWarnings("unused")
    // private static Vfs getExtreme7zPreferredVfs() {
    // return uncheck(() -> new Vfs(
    // new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz", "zip"),
    // new SevenZCompressFileSystem("gz", "Z", "bz2", "xz")
    //
    // ));
    // }
}
