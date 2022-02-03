package net.dempsy.vfs.tar;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import net.dempsy.vfs.EncArchiveFileSystem;

public class TarFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"tar","tgz","tbz2","txz"};
    public final static String ENC = "!";

    public TarFileSystem() {
        super(ENC);
    }

    @SuppressWarnings("resource")
    @Override
    public ArchiveInputStream createArchiveInputStream(final String scheme, final InputStream inner) throws IOException {
        InputStream ret = inner;
        if("tgz".equals(scheme))
            ret = new GZIPInputStream(ret);
        else if("tbz2".equals(scheme))
            ret = new BZip2CompressorInputStream(ret);
        else if("txz".equals(scheme))
            ret = new XZCompressorInputStream(ret);
        return new TarArchiveInputStream(ret);
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }
}
