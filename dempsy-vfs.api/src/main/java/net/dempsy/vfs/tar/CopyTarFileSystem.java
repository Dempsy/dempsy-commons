package net.dempsy.vfs.tar;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import net.dempsy.vfs.CopiedArchiveFileSystem;
import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;

public class CopyTarFileSystem extends CopiedArchiveFileSystem {

    public final static String[] SCHEMES = {"tar","tgz","tbz2","txz"};
    public final static String ENC = "!";

    public CopyTarFileSystem() {
        super(ENC);
    }

    @Override
    public LinkedHashMap<String, FileDetails> extract(final String scheme, final URI archiveUri, final File destinationDirectory) throws IOException {
        return copyArchiveInputStream(archiveUri, makeTarAis(scheme, new BufferedInputStream(vfs.toPath(archiveUri).read())), destinationDirectory);
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    private ArchiveInputStream makeTarAis(final String scheme, final InputStream inner) throws IOException {
        InputStream ret = inner;
        if("tgz".equals(scheme))
            ret = new GZIPInputStream(ret);
        else if("tbz2".equals(scheme))
            ret = new BZip2CompressorInputStream(ret);
        else if("txz".equals(scheme))
            ret = new XZCompressorInputStream(ret);
        return new TarArchiveInputStream(ret,TarConstants.DEFAULT_BLKSIZE,TarConstants.DEFAULT_RCDSIZE, "UTF-8", true);
    }
}
