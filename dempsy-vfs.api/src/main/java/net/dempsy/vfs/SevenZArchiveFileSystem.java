package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.uriCompliantRelPath;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemNotFoundException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.HexStringUtil;
import net.dempsy.util.QuietCloseable;
import net.dempsy.util.UriUtils;
import net.dempsy.vfs.internal.DempsyArchiveEntry;
import net.dempsy.vfs.internal.DempsyArchiveInputStream;
import net.dempsy.vfs.internal.LocalArchiveInputStream;
import net.dempsy.vfs.internal.TempSpace;
import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback;
import net.sf.sevenzipjbinding.ICryptoGetTextPassword;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

public class SevenZArchiveFileSystem extends EncArchiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SevenZArchiveFileSystem.class);

    private static final String CTX_KEY = SevenZArchiveFileSystem.class.getSimpleName();

    private static final File TMP = TempSpace.get();

    public final static String SCHEME_RAR = "rar";
    public final static String[] SCHEMES = {"sevenz",SCHEME_RAR};
    public final static String ENC = "!";

    private final String[] schemes;
    private final Map<String, String> compositeSchemes;
    private final List<String> passwordsToTry = new ArrayList<>();

    public SevenZArchiveFileSystem() {
        super(ENC);
        schemes = SCHEMES;
        compositeSchemes = new HashMap<>();
    }

    public SevenZArchiveFileSystem(final String scheme1, final String... schemes2) {
        super(ENC);

        compositeSchemes = new HashMap<>();

        schemes = Stream.concat(Stream.of(scheme1), Arrays.stream(schemes2 == null ? new String[0] : schemes2))
            .map(s -> {
                final int index = s.indexOf('|');
                if(index >= 0) {
                    final String base = s.substring(0, index);
                    final String comp = s.substring(index + 1);
                    compositeSchemes.put(base, comp);
                    return base;
                } else
                    return s;
            })
            .toArray(String[]::new);
    }

    @Override
    public void tryPasswords(final String... passwordsToTry) {
        this.passwordsToTry.addAll(Arrays.asList(passwordsToTry));
    }

    private static class ArEntry implements DempsyArchiveEntry {

        public final String name;
        public final boolean isDir;
        public final long size;
        public final Date lmdate;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public boolean isDirectory() {
            return isDir;
        }

        @Override
        public Date getLastModifiedDate() {
            return lmdate;
        }

        public ArEntry(final String name, final boolean isDir, final long size, final Date lmdate) {
            this.name = name;
            this.isDir = isDir;
            this.size = size;
            this.lmdate = lmdate;
        }

        @Override
        public File direct() {
            return null;
        }

    }

    private static class Context implements QuietCloseable {
        private final File directory;

        private File forcedFile = null;

        private final File extractDir;
        private boolean isExtracted = false;
        private LinkedHashMap<String, FileDetails> results = null;

        Context(final Vfs vfs, final OpContext ctx) throws IOException {
            this.directory = new File(TMP, UUID.randomUUID().toString());
            ctx.toPath(directory.toURI()).mkdirs();
            extractDir = new File(directory, "extract");
            ctx.toPath(extractDir.toURI()).mkdirs();
        }

        @Override
        public void close() {
            LOGGER.debug("Cleaning {}", directory);
            FileUtils.deleteQuietly(directory);
        }
    }

    @Override
    public DempsyArchiveInputStream createArchiveInputStream(final String scheme, final URI archiveUri, final boolean listingOnly, final OpContext ctx)
        throws IOException {

        final Path archivePath = ctx.toPath(archiveUri);
        final Context c = Functional.<Context, IOException>recheck(() -> archivePath.getContext(CTX_KEY, () -> uncheck(() -> new Context(vfs, ctx))));

        if(c.forcedFile == null)
            c.forcedFile = forceToFile(scheme, archiveUri, ctx, c.directory);

        if(listingOnly)
            return listingStream(scheme, archiveUri, ctx, c.forcedFile);

        if(!c.isExtracted) {
            c.results = extract(scheme, archiveUri, c.extractDir, c.forcedFile);
            c.isExtracted = true;
        }

        return new LocalArchiveInputStream(c.extractDir, c.results);
    }

    private DempsyArchiveInputStream listingStream(final String scheme, final URI archiveUri, final OpContext ctx, final File archiveFile) throws IOException {
        final boolean isRar = SCHEME_RAR.equals(scheme);

        try(final RandomAccessFile randomAccessFile = isRar ? null : new RandomAccessFile(archiveFile, "r");
            final ArchiveOpenVolumeCallback ovcb = isRar ? new ArchiveOpenVolumeCallback() : null;
            final IInArchive inArchive = isRar ? SevenZip.openInArchive(ArchiveFormat.RAR, ovcb.getStream(archiveFile.getAbsolutePath()), ovcb)
                : SevenZip.openInArchive(null, // autodetect archive type
                    new RandomAccessFileInStream(randomAccessFile));

        ) {
            final var archiveFormat = inArchive.getArchiveFormat();
            final int numItems = inArchive.getNumberOfItems();
            final boolean compressedOnly = archiveFormat == null ? numItems == 1 : !archiveFormat.supportMultipleFiles();
            final List<ArEntry> ret = new ArrayList<>(numItems);
            final int[] in = new int[numItems];
            for(int i = 0; i < in.length; i++) {
                in[i] = i;
                final String pathInsideArchive = compressedOnly ? outputFileName(archiveUri, scheme) : (String)inArchive.getProperty(i, PropID.PATH);
                final String name = uriCompliantRelPath(pathInsideArchive);
                final Date lastModTimeObj = (Date)inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME);
                final Long sizeObj = (Long)inArchive.getProperty(i, PropID.SIZE);
                final long size = sizeObj == null ? -1 : sizeObj;
                final boolean isDir = (Boolean)inArchive.getProperty(i, PropID.IS_FOLDER);

                ret.add(new ArEntry(name, isDir, size, lastModTimeObj));
            }

            return new DempsyArchiveInputStream() {

                private final Iterator<ArEntry> iter = ret.iterator();

                @Override
                public DempsyArchiveEntry getNextEntry() throws IOException {
                    return iter.hasNext() ? iter.next() : null;
                }

                @Override
                public int read() throws IOException {
                    throw new UnsupportedOperationException("Cannot read the data from an archive entry creating for listing only");
                }
            };
        }
    }

    private static String outputFileName(final URI archiveUri, final String scheme) {
        // if the URI ends in the scheme (ignoring case) then we strip it.
        final String name = UriUtils.getName(archiveUri);
        if(name.toLowerCase().endsWith("." + scheme.toLowerCase())) {
            final int suffixLen = scheme.toLowerCase().length() + 1;
            return name.substring(0, name.length() - suffixLen);
        }
        throw new IllegalStateException("Cannot determine the output file for archive uri: \"" + archiveUri + "\" given the scheme: " + scheme);
    }

    private static class PasswordProvider implements Supplier<String> {
        private int index = -1;
        private final List<String> passwordsToTry = new ArrayList<>();
        private boolean wasChecked = false;

        private PasswordProvider(final List<String> passwordsToTry) {
            this.passwordsToTry.addAll(passwordsToTry);
        }

        @Override
        public String get() {
            wasChecked = true;
            index++;
            if(index >= passwordsToTry.size())
                return null;
            return passwordsToTry.get(index);
        }

        public boolean hasNext() {
            return (index + 1) < passwordsToTry.size();
        }

    }

    public LinkedHashMap<String, FileDetails> extract(final String scheme, final URI archiveUri, final File destinationDirectory, final File archiveFile)
        throws IOException {

        LOGGER.debug("Extracting {} which is at {} to {}", archiveUri, archiveFile, destinationDirectory);

        final boolean isRar = SCHEME_RAR.equals(scheme);

        final LinkedHashMap<String, FileDetails> ret = new LinkedHashMap<>();

        try(final RandomAccessFile randomAccessFile = isRar ? null : new RandomAccessFile(archiveFile, "r");
            final ArchiveOpenVolumeCallback ovcb = isRar ? new ArchiveOpenVolumeCallback() : null;
            final IInArchive inArchive = isRar ? SevenZip.openInArchive(ArchiveFormat.RAR, ovcb.getStream(archiveFile.getAbsolutePath()), ovcb)
                : SevenZip.openInArchive(null, // autodetect archive type
                    new RandomAccessFileInStream(randomAccessFile));

        ) {
            final var archiveFormat = inArchive.getArchiveFormat();
            final int numItems = inArchive.getNumberOfItems();
            final boolean compressedOnly = archiveFormat == null ? numItems == 1 : !archiveFormat.supportMultipleFiles();

            final int[] in = new int[inArchive.getNumberOfItems()];
            final File[] files = new File[in.length];
            for(int i = 0; i < in.length; i++) {
                in[i] = i;
                final String pathInsideArchive = compressedOnly ? outputFileName(archiveUri, scheme) : (String)inArchive.getProperty(i, PropID.PATH);
                final String name = uriCompliantRelPath(pathInsideArchive);
                final File file = new File(destinationDirectory, name);

                final Date lastModTimeObj = (Date)inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME);
                final long lastModTime = (lastModTimeObj == null) ? -1 : lastModTimeObj.getTime();
                final Long sizeObj = (Long)inArchive.getProperty(i, PropID.SIZE);
                final long size = sizeObj == null ? -1 : sizeObj;

                final boolean isFolder = (Boolean)inArchive.getProperty(i, PropID.IS_FOLDER);

                ret.put(name, new FileDetails(file, lastModTime, size, isFolder));

                { // make sure the parent dirs exist.
                    final File parent = file.getParentFile();
                    if(parent != null)
                        parent.mkdirs();
                }
                files[i] = file;

                if(!isFolder) {
                    if(file.exists())
                        file.delete();

                    // touch the file
                    try(var fos = new FileOutputStream(file, true);
                        FileChannel outChan = fos.getChannel();) {
                        outChan.truncate(0);
                    }
                } else {
                    if(!file.exists())
                        file.mkdirs();
                }
            }

            final var passwordProvider = new PasswordProvider(passwordsToTry);
            try(final var extractor = new MyExtractCallback(inArchive, files, passwordProvider);) {

                for(boolean done = false; !done;) {
                    try {
                        inArchive.extract(in, false, // Non-test mode
                            extractor);
                        done = true;
                    } catch(final SevenZipException e) {
                        if(passwordProvider.wasChecked) {
                            done = !passwordProvider.hasNext();
                            if(done)
                                throw e;
                        } else
                            throw e;
                    }
                }
            }

        }
        return ret;
    }

    @Override
    public String[] supportedSchemes() {
        return schemes;
    }

    private File forceToFile(final String archiveScheme, final URI archiveUri, final OpContext ctx, final File parentDir) throws IOException {
        final Path archivePath;
        File archiveFile;
        {
            if(!compositeSchemes.containsKey(archiveScheme)) {
                archivePath = ctx.toPath(archiveUri);
                try {
                    archiveFile = archivePath.toFile();
                } catch(final FileSystemNotFoundException fsnfe) {
                    archiveFile = null;
                }
            } else {
                archivePath = ctx.toPath(uncheck(() -> new URI(compositeSchemes.get(archiveScheme) + ":" + archiveUri.toString())));
                archiveFile = null;
            }

            if(archiveFile == null) {
                LOGGER.debug("Copying file so I can work with it from {} to {}", archiveUri, archiveFile);
                archiveFile = new File(makeFileFromArchiveUriX(archiveUri, parentDir).getAbsolutePath() + ".archive");
                try(InputStream is = new BufferedInputStream(archivePath.read());
                    OutputStream os = new BufferedOutputStream(new FileOutputStream(archiveFile))) {
                    IOUtils.copy(is, os);
                }
            }
        }
        return archiveFile;
    }

    private static File makeFileFromArchiveUriX(final URI archiveUri, final File parentDir) {
        // generate a filename.
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        final String fname = HexStringUtil.bytesToHex(md.digest(archiveUri.toString().getBytes()));

        return new File(parentDir, fname);
    }

    private static class ArchiveOpenVolumeCallback implements IArchiveOpenVolumeCallback, IArchiveOpenCallback, Closeable {

        /**
         * Cache for opened file streams
         */
        private final Map<String, RandomAccessFile> openedRandomAccessFileList = new HashMap<String, RandomAccessFile>();

        /**
         * Name of the last volume returned by {@link #getStream(String)}
         */
        private String name;

        /**
         * This method should at least provide the name of the last
         * opened volume (propID=PropID.NAME).
         *
         * @see IArchiveOpenVolumeCallback#getProperty(PropID)
         */
        @Override
        public Object getProperty(final PropID propID) throws SevenZipException {
            switch(propID) {
                case NAME:
                    return name;
                default:
                    return null;
            }
        }

        /**
         * The name of the required volume will be calculated out of the
         * name of the first volume and a volume index. In case of RAR file,
         * the substring ".partNN." in the name of the volume file will
         * indicate a volume with id NN. For example:
         * <ul>
         * <li>test.rar - single part archive or multi-part archive with
         * a single volume</li>
         * <li>test.part23.rar - 23-th part of a multi-part archive</li>
         * <li>test.part001.rar - first part of a multi-part archive.
         * "00" indicates, that at least 100 volumes must exist.</li>
         * </ul>
         */
        @Override
        public IInStream getStream(final String filename) throws SevenZipException {
            try {
                // We use caching of opened streams, so check cache first
                RandomAccessFile randomAccessFile = openedRandomAccessFileList.get(filename);
                if(randomAccessFile != null) { // Cache hit.
                    // Move the file pointer back to the beginning
                    // in order to emulating new stream
                    randomAccessFile.seek(0);

                    // Save current volume name in case getProperty() will be called
                    name = filename;

                    return new RandomAccessFileInStream(randomAccessFile);
                }

                // Nothing useful in cache. Open required volume.
                randomAccessFile = new RandomAccessFile(filename, "r");

                // Put new stream in the cache
                openedRandomAccessFileList.put(filename, randomAccessFile);

                // Save current volume name in case getProperty() will be called
                name = filename;
                return new RandomAccessFileInStream(randomAccessFile);
            } catch(final FileNotFoundException fileNotFoundException) {
                // Required volume doesn't exist. This happens if the volume:
                // 1. never exists. 7-Zip doesn't know how many volumes should
                // exist, so it have to try each volume.
                // 2. should be there, but doesn't. This is an error case.

                // Since normal and error cases are possible,
                // we can't throw an error message
                return null; // We return always null in this case
            } catch(final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Close all opened streams
         */
        @Override
        public void close() throws IOException {
            for(final RandomAccessFile file: openedRandomAccessFileList.values()) {
                file.close();
            }
        }

        @Override
        public void setCompleted(final Long files, final Long bytes) throws SevenZipException {}

        @Override
        public void setTotal(final Long files, final Long bytes) throws SevenZipException {}
    }

    private static class MyExtractCallback implements IArchiveExtractCallback, ICryptoGetTextPassword, Closeable {
        private boolean skipExtraction;
        private final IInArchive inArchive;
        private final File[] files;
        private final Supplier<String> fetchPassword;

        public MyExtractCallback(final IInArchive inArchive, final File[] files, final Supplier<String> password) {
            this.inArchive = inArchive;
            this.files = files;
            this.fetchPassword = password;
        }

        int osIndex = -1;
        private OutputStream prev = null;
        Object osLock = new Object();

        @Override
        public ISequentialOutStream getStream(final int index, final ExtractAskMode extractAskMode) throws SevenZipException {
            skipExtraction = (Boolean)inArchive.getProperty(index, PropID.IS_FOLDER);
            if(skipExtraction || extractAskMode != ExtractAskMode.EXTRACT)
                return null;

            return new ISequentialOutStream() {
                int myIndex = index;

                @Override
                public int write(final byte[] data) throws SevenZipException {

                    final OutputStream os;
                    try {
                        synchronized(osLock) {
                            if(prev == null) {
                                prev = new BufferedOutputStream(new FileOutputStream(files[myIndex], true));
                                osIndex = myIndex;
                            } else if(osIndex != myIndex) {
                                prev.close();
                                prev = new BufferedOutputStream(new FileOutputStream(files[myIndex], true));
                                osIndex = myIndex;
                            }
                            os = prev;

                            os.write(data);
                            return data.length; // Return amount of copied data
                        }
                    } catch(final IOException e) {
                        throw new SevenZipException(e);
                    }
                }
            };
        }

        @Override
        public void close() throws IOException {
            if(prev != null) {
                prev.close();
                osIndex = -1;
            }
        }

        @Override
        public void prepareOperation(final ExtractAskMode extractAskMode) throws SevenZipException {}

        @Override
        public void setOperationResult(final ExtractOperationResult extractOperationResult) throws SevenZipException {
            if(skipExtraction) {
                return;
            }
            if(extractOperationResult != ExtractOperationResult.OK) {
                LOGGER.error("Extraction error:" + extractOperationResult);
                throw new SevenZipException("Extraction error:" + extractOperationResult);
            }
        }

        @Override
        public String cryptoGetTextPassword() throws SevenZipException {
            return fetchPassword.get();
        }

        @Override
        public void setCompleted(final long completeValue) throws SevenZipException {}

        @Override
        public void setTotal(final long total) throws SevenZipException {}
    }
}
