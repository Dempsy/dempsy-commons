package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

public class SevenZFileSystem extends CopiedArchiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SevenZFileSystem.class);

    public final static String SCHEME_RAR = "rar";
    public final static String[] SCHEMES = {"sevenz",SCHEME_RAR};
    public final static String ENC = "!";

    private final String[] schemes;
    private final Map<String, String> compositeSchemes;

    public SevenZFileSystem() {
        super(ENC);
        schemes = SCHEMES;
        compositeSchemes = new HashMap<>();
    }

    public SevenZFileSystem(final String scheme1, final String... schemes2) {
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
    public LinkedHashMap<String, FileDetails> extract(final String scheme, final URI archiveUri, final File destinationDirectory) throws IOException {

        final boolean isRar = SCHEME_RAR.equals(scheme);

        final LinkedHashMap<String, FileDetails> ret = new LinkedHashMap<>();

        final ForcedFile ff = forceToFile(scheme, archiveUri);
        final File archiveFile = ff.file;
        final boolean deleteArchiveFile = ff.deleteArchiveFile;

        try(RandomAccessFile randomAccessFile = isRar ? null : new RandomAccessFile(archiveFile, "r");
            final ArchiveOpenVolumeCallback ovcb = isRar ? new ArchiveOpenVolumeCallback() : null;
            IInArchive inArchive = isRar ? SevenZip.openInArchive(ArchiveFormat.RAR, ovcb.getStream(archiveFile.getAbsolutePath()), ovcb)
                : SevenZip.openInArchive(null, // autodetect archive type
                    new RandomAccessFileInStream(randomAccessFile));

        ) {
            final int[] in = new int[inArchive.getNumberOfItems()];
            final File[] files = new File[in.length];
            for(int i = 0; i < in.length; i++) {
                in[i] = i;
                final String name = (String)inArchive.getProperty(i, PropID.PATH);
                final File file = new File(destinationDirectory, name);

                final Date lastModTimeObj = (Date)inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME);
                final long lastModTime = (lastModTimeObj == null) ? -1 : lastModTimeObj.getTime();
                final Long sizeObj = (Long)inArchive.getProperty(i, PropID.SIZE);
                final long size = sizeObj == null ? -1 : sizeObj;

                ret.put(name, new FileDetails(file, lastModTime, size));

                { // make sure the parent dirs exist.
                    final File parent = file.getParentFile();
                    if(parent != null)
                        parent.mkdirs();
                }
                files[i] = file;

                if(!((Boolean)inArchive.getProperty(i, PropID.IS_FOLDER))) {
                    if(file.exists())
                        file.delete();

                    // touch the file
                    try(FileChannel outChan = new FileOutputStream(file, true).getChannel()) {
                        outChan.truncate(0);
                    }
                } else {
                    if(!file.exists())
                        file.mkdirs();
                }
            }

            try(var extractor = new MyExtractCallback(inArchive, files);) {
                inArchive.extract(in, false, // Non-test mode
                    extractor);
            }

        } finally {
            if(deleteArchiveFile)
                FileUtils.deleteQuietly(archiveFile);
        }

        return ret;
    }

    @Override
    public String[] supportedSchemes() {
        return schemes;
    }

    private static class ForcedFile {
        public final File file;
        public final boolean deleteArchiveFile;

        public ForcedFile(final File file, final boolean deleteArchiveFile) {
            this.file = file;
            this.deleteArchiveFile = deleteArchiveFile;
        }
    }

    private ForcedFile forceToFile(final String archiveScheme, final URI archiveUri) throws IOException {
        final Path archivePath;
        File archiveFile;
        boolean deleteArchiveFile = false;
        {
            if(!compositeSchemes.containsKey(archiveScheme)) {
                archivePath = vfs.toPath(archiveUri);
                try {
                    archiveFile = archivePath.toFile();
                } catch(final FileSystemNotFoundException fsnfe) {
                    archiveFile = null;
                }
            } else {
                archivePath = vfs.toPath(uncheck(() -> new URI(compositeSchemes.get(archiveScheme) + ":" + archiveUri.toString())));
                archiveFile = null;
            }

            if(archiveFile == null) {
                archiveFile = new File(makeFileFromArchiveUri(archiveUri).getAbsolutePath() + ".archive");
                try(InputStream is = new BufferedInputStream(archivePath.read());
                    OutputStream os = new BufferedOutputStream(new FileOutputStream(archiveFile))) {
                    IOUtils.copy(is, os);
                }
                deleteArchiveFile = true;
            }
        }
        return new ForcedFile(archiveFile, deleteArchiveFile);
    }

    private static class ArchiveOpenVolumeCallback
        implements IArchiveOpenVolumeCallback, IArchiveOpenCallback, Closeable {

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

    private static class MyExtractCallback implements IArchiveExtractCallback, Closeable {
        private boolean skipExtraction;
        private final IInArchive inArchive;
        private final File[] files;

        public MyExtractCallback(final IInArchive inArchive, final File[] files) {
            this.inArchive = inArchive;
            this.files = files;
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
        public void setCompleted(final long completeValue) throws SevenZipException {}

        @Override
        public void setTotal(final long total) throws SevenZipException {}
    }
}
