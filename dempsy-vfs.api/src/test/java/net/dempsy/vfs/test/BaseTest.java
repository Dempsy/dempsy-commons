package net.dempsy.vfs.test;

import static net.dempsy.util.Functional.uncheck;

import java.net.URI;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.dempsy.vfs.CopyingArchiveFileSystem;
import net.dempsy.vfs.SevenZArchiveFileSystem;
import net.dempsy.vfs.Vfs;
import net.dempsy.vfs.ZCompressedFileSystem;
import net.dempsy.vfs.bz.Bz2FileSystem;
import net.dempsy.vfs.gz.GzFileSystem;
import net.dempsy.vfs.tar.TarFileSystem;
import net.dempsy.vfs.xz.XzFileSystem;
import net.dempsy.vfs.zip.ZipFileSystem;

public class BaseTest {
    public static final String tenc = TarFileSystem.ENC;
    public static final String zenc = ZipFileSystem.ENC;

    public static Stream<Object[]> vfsConfigs() {
        return Stream.of(
            new Object[] {(Supplier<Vfs>)() -> getCopyVfs()},
            new Object[] {(Supplier<Vfs>)() -> getStreamVfs()},
            new Object[] {(Supplier<Vfs>)() -> get7zPreferredVfs()}
        );
    }

    public static String removeTrailingSlash(final String str) {
        return str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
    }

    public static URI removeTrailingSlash(final URI uri) {
        return uncheck(() -> new URI(removeTrailingSlash(uri.toString())));
    }

    private static Vfs getCopyVfs() {
        return uncheck(() -> new Vfs(
            new CopyingArchiveFileSystem(new TarFileSystem()),
            new GzFileSystem(),
            new CopyingArchiveFileSystem(new ZipFileSystem()),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem(),
            new SevenZArchiveFileSystem()

        ));
    }

    private static Vfs getStreamVfs() {
        return uncheck(() -> new Vfs(
            new TarFileSystem(),
            new GzFileSystem(),
            new ZipFileSystem(),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem(),
            new SevenZArchiveFileSystem()

        ));
    }

    private static Vfs get7zPreferredVfs() {
        return uncheck(() -> new Vfs(
            new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz", "zip"),
            new GzFileSystem(),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem()

        ));
    }

}
