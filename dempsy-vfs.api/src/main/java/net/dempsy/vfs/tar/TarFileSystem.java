package net.dempsy.vfs.tar;

import static net.dempsy.vfs.internal.DempsyArchiveInputStream.wrap;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import net.dempsy.vfs.EncArchiveFileSystem;
import net.dempsy.vfs.internal.DempsyArchiveInputStream;

public class TarFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"tar","tgz","tbz2","txz"};
    public final static String ENC = "!";

    public TarFileSystem() {
        super(ENC);
    }

    @Override
    public DempsyArchiveInputStream createArchiveInputStream(final String scheme, final URI archiveUri, final boolean listingOnly) throws IOException {
        InputStream ret = new BufferedInputStream(vfs.toPath(archiveUri).read());
        if("tgz".equals(scheme))
            ret = new GZIPInputStream(ret);
        else if("tbz2".equals(scheme))
            ret = new BZip2CompressorInputStream(ret);
        else if("txz".equals(scheme))
            ret = new XZCompressorInputStream(ret);
        return wrap(new TarArchiveInputStream(ret));
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }
}
