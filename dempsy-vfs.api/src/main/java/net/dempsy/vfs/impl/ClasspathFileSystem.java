package net.dempsy.vfs.impl;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import net.dempsy.vfs.FileSystem;
import net.dempsy.vfs.Path;

public class ClasspathFileSystem extends FileSystem {
    static private final Logger LOGGER = LoggerFactory.getLogger(ClasspathFileSystem.class);

    @Override
    public String[] supportedSchemes() {
        return new String[] {"classpath"};
    }

    @Override
    protected Path doCreatePath(final URI uriuri) throws IOException {
        return new ClasspathPath(uriuri);
    }

    @Override
    public File toFile(final URI uri) {
        final String fullPath = ClasspathFileSystem.class.getClassLoader().getResource(getPath(uri)).getFile();
        final File ret = new File(fullPath);
        if(!ret.exists()) {
            // we're going to attempt to copy it
            try {
                final File newRet = File.createTempFile("classpathVfs.", ".tmp");
                LOGGER.debug("Copying the classpath resource \"" + fullPath + "\" to the temporary file \"" + newRet.getAbsolutePath()
                    + " in order to allow access as a file.");
                try(final InputStream is = doCreatePath(uri).read();
                    final OutputStream os = new BufferedOutputStream(new FileOutputStream(newRet));) {
                    IOUtils.copy(is, os);
                    newRet.deleteOnExit();
                    return newRet;
                }
            } catch(final IOException ioe) {
                throw new RuntimeException(ioe);
            }
        } else
            return ret;
    }

    @Override
    public void close() {}

    private static InputStream buffer(final InputStream is, final boolean gzipped) throws IOException {
        return new BufferedInputStream(gzipped ? new GZIPInputStream(is) : is);
    }

    private static String getPath(final URI uriuri) {
        // test for authority
        final String host = uriuri.getHost();
        String path = uriuri.getPath();
        if(path == null) {
            LOGGER.warn("classpath uri (" + uriuri
                + ") is invalid. It should provide a /// after the scheme and colon. Working around the problem.");
            path = uriuri.toString().substring(10).trim();
        } else {
            if(host != null)
                path = host + path;
        }
        // strip the leading slash.
        if(path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    private static String getPathPotentialJarPath(final URI uri) {
        final String pathToUse;
        if("jar".equals(uri.getScheme())) {
            final String uriStr = uri.toString();
            int indexOfEx = uriStr.lastIndexOf('!');
            if(indexOfEx < 0) {
                // just cut off from the last ':'
                indexOfEx = uriStr.lastIndexOf(':');
                if(indexOfEx < 0)
                    throw new IllegalArgumentException("Cannot interpret the jar uri: " + uriStr);
            }
            pathToUse = uriStr.substring(indexOfEx + 1);
        } else
            pathToUse = uri.getPath();
        return pathToUse;
    }

    private static final class ClasspathPath extends Path {
        private final URI uriuri;
        final private String path;
        final boolean isGzipped;

        private Resource resource = null;

        private ClasspathPath(final URI uriuri) {
            this.uriuri = uriuri;
            path = getPath(uriuri);
            isGzipped = path.endsWith(".gz");
        }

        @Override
        public OutputStream write() throws IOException {
            throw new UnsupportedOperationException("Writing to files at \"classpath:\" uris is not supported.");
        }

        @Override
        public InputStream read() throws IOException {
            final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if(is == null)
                throw new FileNotFoundException(
                    "The resource on the classpath given by \"" + uriuri + "\" doesn't seem to exist.");
            return buffer(is, isGzipped);
        }

        @Override
        public void delete() throws IOException {
            throw new UnsupportedOperationException("Delete is not supported for \"classpath:\" uris.");
        }

        @Override
        public boolean exists() throws IOException {
            return Thread.currentThread().getContextClassLoader().getResource(path) != null;
        }

        @Override
        public boolean handlesUngzipping() {
            return true;
        }

        @Override
        public Path[] list() throws IOException {
            if(!exists())
                throw new FileNotFoundException("Directory \"" + this.uri() + "\" doesn't seem to exist.");

            final Resource thsResource = getResource();
            final String mappedPath = getPathPotentialJarPath(uncheck(() -> thsResource.getURI()));

            String lpath = path;
            if(!lpath.endsWith("/"))
                lpath += "/";
            lpath += "**";
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(lpath);

            // =====================================================================
            // When there's an empty directory in a jar file, it ends up on the list
            // of resources. So we are going to filter it out.
            final String dirOnListOfResources = path.endsWith("/") ? path : (path + "/");
            String tmp = (!mappedPath.startsWith("/")) ? ("/" + mappedPath) : mappedPath;
            if(tmp.endsWith("/"))
                tmp = tmp.substring(0, tmp.length() - 1);
            final String pathToCompare = tmp;
            resources = Arrays.stream(resources)
                // this condition seems to manifest when an empty directory is on the classpath in a jar.
                .filter(r -> !((r instanceof ClassPathResource) && (((ClassPathResource)r).getPath().equals(dirOnListOfResources))))
                // we need to force recursion. We only want the contents of the immediate directory.
                // .peek(r -> System.out.println(new File(getPathPotentialJarPath(uncheck(() -> r.getURI()))).getParent()))
                .filter(r -> {
                    String curPath = new File(getPathPotentialJarPath(uncheck(() -> r.getURI()))).getParent();
                    if(curPath.endsWith("/"))
                        curPath = curPath.substring(0, curPath.length() - 1);
                    return pathToCompare.equals(curPath);
                })
                .toArray(Resource[]::new);
            // =====================================================================

            final Path[] ret = new Path[resources.length];

            for(int i = 0; i < ret.length; i++)
                ret[i] = vfs.toPath(resources[i].getURI());

            return ret;
        }

        @Override
        public String toString() {
            return uriuri.toString();
        }

        @Override
        public URI uri() {
            return uriuri;
        }

        @Override
        public long lastModifiedTime() throws IOException {
            return getResource().lastModified();
        }

        @Override
        public long length() throws IOException {
            return getResource().contentLength();
        }

        private Resource getResource() {
            if(resource == null)
                resource = new PathMatchingResourcePatternResolver().getResource(path);
            return resource;
        }

    }

}
