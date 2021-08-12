package com.github.swrirobotics.bags.storage.s3;

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.BagReader;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.storage.BagStorage;
import com.github.swrirobotics.bags.storage.BagWrapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3BagWrapperImpl implements BagWrapper {
    private final Logger myLogger = LoggerFactory.getLogger(S3BagWrapperImpl.class);
    private final S3Client myS3Client;
    private final String myKey;
    private final S3BagStorageImpl myBagStorage;
    private final String myPath;
    private final String myFilename;
    private File file = null;
    private BagFile bagFile = null;

    public S3BagWrapperImpl(S3Client s3Client, String key, S3BagStorageImpl storage) {
        myS3Client = s3Client;
        myKey = key;
        myBagStorage = storage;
        Pattern pathPattern = Pattern.compile("^(.*/)?(.*)$");
        Matcher keyMatcher = pathPattern.matcher(key);
        if (!keyMatcher.find()) {
            myLogger.error("Unable to parse S3 bag key.");
        }
        myPath = keyMatcher.group(1);
        myFilename = keyMatcher.group(2);
    }

    @Override
    public BagFile getBagFile() throws BagReaderException {
        myLogger.info("getBagFile: " + myKey);
        if (bagFile != null) {
            return bagFile;
        }

        try {
            myLogger.info("Opening bag file at: " + getFile().getAbsolutePath());
            bagFile = BagReader.readFile(file);
        }
        catch (IOException e) {
            throw new BagReaderException(e);
        }
        return bagFile;
    }

    public File getFile() throws IOException {
        // TODO This will download the source object every time a BagWrapper is created for a file.
        // This is bad because it means the file will be downloaded every time a user looks at an image,
        // streams a video, runs a script, etc.
        // Probably should have some way of caching downloaded files with a configurable cache size.
        myLogger.info("getFile: " + myKey);
        if (file != null) {
            return file;
        }
        file = File.createTempFile("s3obj", ".bag", new File("/tmp"));
        file.deleteOnExit();
        FileOutputStream output = new FileOutputStream(file);
        var request = GetObjectRequest.builder().bucket(myBagStorage.getBucket()).key(myKey).build();
        myLogger.info("Downloading S3 object to: " + file.getAbsolutePath());
        IOUtils.copy(myS3Client.getObject(request), output);
        myLogger.info("Done downloading.  File size: " + Files.size(file.toPath()));
        return file;
    }

    @Override
    public BagStorage getBagStorage() {
        return myBagStorage;
    }

    @Override
    public String getPath() {
        return myPath;
    }

    @Override
    public String getFilename() {
        return myFilename;
    }

    @Override
    public InputStream getInputStream() {
        var request = GetObjectRequest.builder().bucket(myBagStorage.getBucket()).key(myKey).build();
        return myS3Client.getObject(request);
    }

    @Override
    public void close() throws IOException {
        myLogger.info("Closing " + myKey);
        if (file != null && !file.delete()) {
            throw new IOException("Error deleting temporary file: " + file.getAbsolutePath());
        }
        bagFile = null;
        file = null;
    }
}
