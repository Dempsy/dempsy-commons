package net.dempsy.vfs.apache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileNotFoundException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.provider.http4.Http4FileProvider;
import org.apache.commons.vfs2.provider.http4s.Http4sFileProvider;
import org.apache.commons.vfs2.provider.http5.Http5FileProvider;
import org.apache.commons.vfs2.provider.http5s.Http5sFileProvider;
import org.apache.commons.vfs2.provider.tar.TarFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.vfs.FileSystem;
import net.dempsy.vfs.Path;

public class ApacheVfsFileSystem extends FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheVfsFileSystem.class);
    private DefaultFileSystemManager manager;

    public ApacheVfsFileSystem() throws IOException {
        getApacheVfs2FileSystem();
        final String[] schemes = getApacheVfs2FileSystem().getSchemes();
        final Set<String> schemeSet = new HashSet<>(Arrays.asList(schemes));

        {
            final String proxiedScheme = "http4s";
            final String scheme = "https";

            if(schemeSet.contains(proxiedScheme) && !schemeSet.contains(scheme)) {
                manager.addProvider(scheme, new Http4sFileProvider() {
                    @Override
                    public FileObject createFileSystem(final String scheme, final FileObject file, final FileSystemOptions fileSystemOptions)
                        throws FileSystemException {
                        if(scheme.equals(scheme))
                            return super.createFileSystem(proxiedScheme, file, fileSystemOptions);
                        else
                            return super.createFileSystem(scheme, file, fileSystemOptions);
                    }
                });
                schemeSet.add(scheme);
            }
        }
        {
            final String proxiedScheme = "http4";
            final String scheme = "http";

            if(schemeSet.contains(proxiedScheme) && !schemeSet.contains(scheme)) {
                manager.addProvider(scheme, new Http4FileProvider() {
                    @Override
                    public FileObject createFileSystem(final String scheme, final FileObject file, final FileSystemOptions fileSystemOptions)
                        throws FileSystemException {
                        if(scheme.equals(scheme))
                            return super.createFileSystem(proxiedScheme, file, fileSystemOptions);
                        else
                            return super.createFileSystem(scheme, file, fileSystemOptions);
                    }
                });
                schemeSet.add(scheme);
            }
        }
        {
            final String proxiedScheme = "http5s";
            final String scheme = "https";

            if(schemeSet.contains(proxiedScheme) && !schemeSet.contains(scheme)) {
                manager.addProvider(scheme, new Http5sFileProvider() {
                    @Override
                    public FileObject createFileSystem(final String scheme, final FileObject file, final FileSystemOptions fileSystemOptions)
                        throws FileSystemException {
                        if(scheme.equals(scheme))
                            return super.createFileSystem(proxiedScheme, file, fileSystemOptions);
                        else
                            return super.createFileSystem(scheme, file, fileSystemOptions);
                    }
                });
                schemeSet.add(scheme);
            }
        }
        {
            final String proxiedScheme = "http5";
            final String scheme = "http";

            if(schemeSet.contains(proxiedScheme) && !schemeSet.contains(scheme)) {
                manager.addProvider(scheme, new Http5FileProvider() {
                    @Override
                    public FileObject createFileSystem(final String scheme, final FileObject file, final FileSystemOptions fileSystemOptions)
                        throws FileSystemException {
                        if(scheme.equals(scheme))
                            return super.createFileSystem(proxiedScheme, file, fileSystemOptions);
                        else
                            return super.createFileSystem(scheme, file, fileSystemOptions);
                    }
                });
                schemeSet.add(scheme);
            }
        }

        {
            if(!schemeSet.contains("tar")) {
                manager.addProvider("tar", new TarFileProvider());
            }
        }
    }

    @Override
    public String[] supportedSchemes() {
        return getApacheVfs2FileSystem().getSchemes();
    }

    @Override
    public void close() {}

    @Override
    protected Path doCreatePath(final URI uriuri) throws IOException {
        final String uri = uriuri.toString();
        return new ApacheVfsPath(getApacheVfs2FileSystem().resolveFile(uri));
    }

    private static final class ApacheVfsPath extends Path {
        private static final String FILE_NOT_FOUND = "Apache FileNotFoundException:";
        final private FileObject fileObject;

        private ApacheVfsPath(final FileObject fileObject) {
            this.fileObject = fileObject;
        }

        @Override
        public OutputStream write() throws IOException {
            try {
                return fileObject.getContent().getOutputStream();
            } catch(final FileNotFoundException fnfe) {
                LOGGER.info(FILE_NOT_FOUND, fnfe);
                throw new java.io.FileNotFoundException(fnfe.getLocalizedMessage());
            }
        }

        @Override
        public InputStream read() throws IOException {
            try {
                return fileObject.getContent().getInputStream();
            } catch(final FileNotFoundException fnfe) {
                LOGGER.info(FILE_NOT_FOUND, fnfe);
                throw new java.io.FileNotFoundException(fnfe.getLocalizedMessage());
            }
        }

        @Override
        public void delete() throws IOException {
            try {
                fileObject.close();
                fileObject.delete(new AllFileSelector());
            } catch(final FileNotFoundException fnfe) {
                LOGGER.info(FILE_NOT_FOUND, fnfe);
                throw new java.io.FileNotFoundException(fnfe.getLocalizedMessage());
            }
        }

        @Override
        public boolean exists() throws IOException {
            return fileObject.exists();
        }

        @Override
        public Path[] list() throws IOException {
            if(!fileObject.isFolder())
                return null;

            FileObject[] rets;
            try {
                rets = fileObject.getChildren();
            } catch(final IOException ioe) {
                // some implementations throw an exception when they aren't dirs
                rets = new FileObject[0];
            }

            final Path[] ret = new Path[rets.length];
            for(int i = 0; i < rets.length; i++)
                ret[i] = new ApacheVfsPath(rets[i]);

            return ret;
        }

        @Override
        public String toString() {
            return fileObject.toString();
        }

        @Override
        public URI uri() {
            try {
                return fileObject.getURL().toURI();
            } catch(FileSystemException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void mkdirs() throws IOException {
            if(exists()) {
                if(isDirectory())
                    return;
                else
                    throw new IOException("Can't create direcotory \"" + uri() + " since it already exists as a file.");
            }
            fileObject.createFolder();
        }

        @Override
        public long lastModifiedTime() throws IOException {
            try {
                return fileObject.getContent().getLastModifiedTime();
            } catch(final FileSystemException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private DefaultFileSystemManager getApacheVfs2FileSystem() {
        try {
            manager = (DefaultFileSystemManager)VFS.getManager();
        } catch(final FileSystemException e) {
            throw new IllegalStateException(e);
        }
        return manager;
    }
}
