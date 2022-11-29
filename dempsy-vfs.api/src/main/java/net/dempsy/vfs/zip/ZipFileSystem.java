package net.dempsy.vfs.zip;

import static net.dempsy.vfs.internal.DempsyArchiveInputStream.wrap;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import net.dempsy.vfs.EncArchiveFileSystem;
import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.internal.DempsyArchiveInputStream;

public class ZipFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"zip","jar"};
    public final static String ENC = "!";

    public ZipFileSystem() {
        super(ENC);
    }

    @Override
    protected DempsyArchiveInputStream createArchiveInputStream(final String scheme, final URI archiveUri, final boolean listingOnly, final OpContext ctx)
        throws IOException {
        return wrap(new ZipArchiveInputStream(new BufferedInputStream(ctx.toPath(archiveUri).read())));
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }
}
