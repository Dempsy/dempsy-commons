package net.dempsy.vfs2.tar;

import static net.dempsy.vfs2.internal.DempsyArchiveInputStream.wrap;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import net.dempsy.vfs2.EncArchiveFileSystem;
import net.dempsy.vfs2.OpContext;
import net.dempsy.vfs2.internal.DempsyArchiveInputStream;

public class TarFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"tar","tgz","tbz2","txz"};
    public final static String ENC = "!";

    public TarFileSystem() {
        super(ENC);
    }

    @SuppressWarnings("resource")
    @Override
    public DempsyArchiveInputStream createArchiveInputStream(final String scheme, final URI archiveUri, final boolean listingOnly, final OpContext ctx)
        throws IOException {
        InputStream ret = new BufferedInputStream(ctx.toPath(archiveUri).read());
        if("tgz".equals(scheme))
            ret = new GZIPInputStream(ret);
        else if("tbz2".equals(scheme))
            ret = new BZip2CompressorInputStream(ret);
        else if("txz".equals(scheme))
            ret = new XZCompressorInputStream(ret);
        return wrap(new TarArchiveInputStream(ret, TarConstants.DEFAULT_BLKSIZE, TarConstants.DEFAULT_RCDSIZE, "UTF-8", true));
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }
}
