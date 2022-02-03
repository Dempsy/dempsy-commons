package net.dempsy.vfs.local;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import net.dempsy.vfs.EncArchiveFileSystem;
import net.lingala.zip4j.io.inputstream.ZipInputStream;

public class OptimizedZipFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"zip","jar"};
    public final static String ENC = "!";

    public OptimizedZipFileSystem() {
        super(ENC);
    }

    @Override
    protected ArchiveInputStream createArchiveInputStream(final String scheme, final InputStream inner) throws IOException {
        return new ArchiveInputStream() {

            ZipInputStream zis = new ZipInputStream(inner);

            @Override
            public int read(final byte b[], final int off, final int len) throws IOException {
                return zis.read(b, off, len);
            }

            @Override
            public ArchiveEntry getNextEntry() throws IOException {

                final var zae = zis.getNextEntry();
                if(zae == null)
                    return null;

                return new ArchiveEntry() {

                    @Override
                    public boolean isDirectory() {
                        return zae.isDirectory();
                    }

                    @Override
                    public long getSize() {
                        return zae.getUncompressedSize();
                    }

                    @Override
                    public String getName() {
                        return zae.getFileName();
                    }

                    @Override
                    public Date getLastModifiedDate() {
                        return new Date(zae.getLastModifiedTime());
                    }
                };
            }
        };
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }
}
