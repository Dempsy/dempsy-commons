package net.dempsy.vfs2.internal;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

public abstract class DempsyArchiveInputStream extends ArchiveInputStream {

    @Override
    public abstract DempsyArchiveEntry getNextEntry() throws IOException;

    public static DempsyArchiveInputStream wrap(final ArchiveInputStream ais) {
        return new DempsyArchiveInputStream() {

            @Override
            public int read(final byte b[], final int off, final int len) throws IOException {
                return ais.read(b, off, len);
            }

            @Override
            public DempsyArchiveEntry getNextEntry() throws IOException {
                final ArchiveEntry e = ais.getNextEntry();
                return e == null ? null : new DempsyArchiveEntry() {

                    @Override
                    public boolean isDirectory() {
                        return e.isDirectory();
                    }

                    @Override
                    public long getSize() {
                        return e.getSize();
                    }

                    @Override
                    public String getName() {
                        return e.getName();
                    }

                    @Override
                    public Date getLastModifiedDate() {
                        return e.getLastModifiedDate();
                    }

                    @Override
                    public File direct() {
                        return null;
                    }
                };
            }

        };
    }

}
