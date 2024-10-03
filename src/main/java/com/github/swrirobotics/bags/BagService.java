// *****************************************************************************
//
// Copyright (c) 2015, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

package com.github.swrirobotics.bags;

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.MessageHandler;
import com.github.swrirobotics.bags.reader.TopicInfo;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.*;
import com.github.swrirobotics.bags.reader.records.Connection;
import com.github.swrirobotics.bags.storage.*;
import com.github.swrirobotics.bags.storage.filesystem.FilesystemBagStorageConfigImpl;
import com.github.swrirobotics.bags.storage.filesystem.FilesystemBagStorageImpl;
import com.github.swrirobotics.bags.storage.s3.S3BagStorageConfigImpl;
import com.github.swrirobotics.bags.storage.s3.S3BagStorageImpl;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.MessageType;
import com.github.swrirobotics.persistence.*;
import com.github.swrirobotics.remote.GeocodingService;
import com.github.swrirobotics.scripts.NonexistentScriptException;
import com.github.swrirobotics.scripts.ScriptRunException;
import com.github.swrirobotics.scripts.ScriptService;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.BagList;
import com.github.swrirobotics.support.web.BagTreeNode;
import com.github.swrirobotics.support.web.ExtJsFilter;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BagService extends StatusProvider {
    private final BagRepository myBagRepository;
    private final BagPositionRepository myBagPositionRepository;
    private final MessageTypeRepository myMTRepository;
    private final TopicRepository myTopicRepository;
    private final TagRepository myTagRepository;
    public final ConfigService myConfigService;
    private final GeocodingService myGeocodingService;
    private final ScriptService myScriptService;
    @PersistenceContext
    private EntityManager myEM;

    private final Map<String, BagStorage> myBagStorages = Maps.newHashMap();

    final private Object myBagDbLock = new Object();

    final private int[] rgba2rgb = {0,0, 1,1, 2,2};
    final private int[] bgra2rgb = {0,2, 1,1, 2,0};

    private final GeometryFactory myGeometryFactory =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    private static final Logger myLogger = LoggerFactory.getLogger(BagService.class);

    static {
        try {
            nu.pattern.OpenCV.loadShared();
        }
        catch (UnsatisfiedLinkError e) {
            myLogger.warn("Library was already loaded.", e);
        }
        catch (Exception e) {
            myLogger.warn("Unable to load OpenCV.  Some image formats will be unreadable", e);
        }
    }

    public BagService(BagRepository bagRepository, BagPositionRepository myBagPositionRepository,
                      MessageTypeRepository myMTRepository, TopicRepository myTopicRepository,
                      TagRepository myTagRepository, ConfigService myConfigService, GeocodingService myGeocodingService,
                      ScriptService myScriptService, ApplicationContext applicationContext) {
        this.myBagRepository = bagRepository;
        this.myBagPositionRepository = myBagPositionRepository;
        this.myMTRepository = myMTRepository;
        this.myTopicRepository = myTopicRepository;
        this.myTagRepository = myTagRepository;
        this.myConfigService = myConfigService;
        this.myGeocodingService = myGeocodingService;
        this.myScriptService = myScriptService;

        // Initialize bag storage configurations
        List<BagStorageConfiguration> storageConfigs;

        if (myConfigService != null && myConfigService.getConfiguration() != null) {
            storageConfigs = myConfigService.getConfiguration().getStorageConfigurations();
        }
        else {
            storageConfigs = new ArrayList<>();
        }

        if (storageConfigs.isEmpty()) {
            myLogger.info("No storage configs explicitly defined; creating default filesystem storage.");
            FilesystemBagStorageConfigImpl fsConfig = new FilesystemBagStorageConfigImpl();
            if (myConfigService != null && myConfigService.getConfiguration() != null) {
                fsConfig.basePath = myConfigService.getConfiguration().getBagPath();
                fsConfig.dockerPath = "/bags";
            }
            fsConfig.storageId = "default";
            storageConfigs.add(fsConfig);
        }

        for (BagStorageConfiguration config : storageConfigs) {
            try {
                BagStorage storage;
                if (config instanceof FilesystemBagStorageConfigImpl) {
                    myLogger.info("Creating FilesystemBagStorageImpl.");
                    storage = applicationContext.getBean(FilesystemBagStorageImpl.class);
                    storage.setBagService(this);
                }
                else if (config instanceof S3BagStorageConfigImpl) {
                    myLogger.info("Creating S3BagStorageImpl.");
                    storage = applicationContext.getBean(S3BagStorageImpl.class);
                    storage.setBagService(this);
                }
                else {
                    myLogger.warn("Unexpected storage config type: " + config.getClass().toString());
                    continue;
                }
                storage.loadConfig(config);
                myBagStorages.put(storage.getStorageId(), storage);
                storage.start();
            }
            catch (BagStorageConfigException e) {
                myLogger.error("Error configuring BagStorage", e);
            }
        }
    }

    /**
     * Deletes every bag file in the database that either has a null storageId or a storageId that is not present
     * in the list of our configured storage backends.
     */
    @Transactional
    public void deleteUnownedBags() {
        myLogger.trace("deleteUnownedBags");
        List<String> storageIds = getBagStorageIds();
        Query query = myEM.createQuery("delete from Bag b where b.storageId not in ?1 or b.storageId is null");
        query.setParameter(1, storageIds);
        int numberRemoved = query.executeUpdate();
        myLogger.info("Removed " + numberRemoved + " bags that were not owned by any configured storage mechanism.");
    }

    public Collection<BagStorage> getBagStorages() {
        return this.myBagStorages.values();
    }

    public List<String> getBagStorageIds() {
        List<String> storageList = Lists.newArrayList(myBagStorages.keySet());
        storageList.sort(String::compareToIgnoreCase);
        return storageList;
    }

    @Transactional
    public String getBagMd5Sum(long bagId) throws NonexistentBagException {
        return myBagRepository.findById(bagId).orElseThrow(() ->
            new NonexistentBagException("Bag not found: " + bagId)).getMd5sum();
    }

    @Transactional
    public BagWrapper getBagWrapper(long bagId) throws NonexistentBagException {
        Bag bag = myBagRepository.findById(bagId).orElseThrow(() ->
            new NonexistentBagException("Bag not found: " + bagId));
        BagStorage storage = myBagStorages.get(bag.getStorageId());
        return storage.getBagWrapper(bag);
    }

    @Transactional
    public BagWrapper getBagWrapper(Bag bag) {
        BagStorage storage = myBagStorages.get(bag.getStorageId());
        return storage.getBagWrapper(bag);
    }

    @Transactional(readOnly = true)
    public Bag getBag(Long bagId) throws NonexistentBagException {
        Bag response = myBagRepository.findById(bagId).orElseThrow(() ->
                new NonexistentBagException("Bag not found: " + bagId));
        myLogger.debug("Successfully got bag: " + response.getFilename());
        return response;
    }

    @Transactional(readOnly = true)
    public byte[] getImage(Long bagId, String topicName, int index) throws BagReaderException, NonexistentBagException {
        try (BagWrapper wrapper = getBagWrapper(bagId)) {
            BagFile bagFile = wrapper.getBagFile();

            myLogger.debug("Reading message #" + index + " from bag " + bagId +
                           " on topic [" + topicName + "]");
            com.github.swrirobotics.bags.reader.messages.serialization.MessageType
                    mt = bagFile.getFirstMessageOnTopic(topicName);
                    // TODO Currently indexing to an arbitrary message is just
                    // too slow; it can take several seconds every time you want
                    // to get a message.  Maybe build a database table that has
                    // all of the indexes and use that...
                    //bagFile.getMessageOnTopicAtIndex(topicName.trim(),
                    //                                      index);
            if (mt == null) {
                String errorMsg = "No messages found on topic: " + topicName;
                myLogger.warn(errorMsg);
                throw new BagReaderException(errorMsg);
            }
            String messageType = mt.getPackage() + "/" + mt.getType();
            if (messageType.equals("sensor_msgs/Image")) {
                return convertImageToJpeg(getUncompressedImage(mt));
            }
            else if (messageType.equals("sensor_msgs/CompressedImage")) {
                return getCompressedImage(mt);
            }
            else {
                String errorMsg = "Unknown image message type: " + mt.getType();
                myLogger.error(errorMsg);
                throw new BagReaderException(errorMsg);
            }

        }
        catch (BagReaderException | UninitializedFieldException | IOException e) {
            String msg = "Unable to read image for bag " + bagId + ": " + e.getLocalizedMessage();
            myLogger.error(msg, e);
            throw new BagReaderException(e);
        }
    }

    /**
     * Estimates determines the frame rate and duration of a topic from a bag
     * file.
     *
     * Although we only use this for images, this should work for any message
     * type that has a std_msgs/Header.
     */
    private static class FrameRateDeterminer implements MessageHandler {
        private double myDurationS = 0.0;
        private double myFrameRate = 10.0;
        private long myCurrentFrame = 0;
        private final long myTotalFrameCount;
        private final List<Long> myFrameTimes = Lists.newArrayList();

        private static final int FRAMES_TO_COUNT = 30;

        FrameRateDeterminer(long totalFrameCount) {
            myTotalFrameCount = totalFrameCount;
        }

        /**
         * The most accurate way to determine the frame rate and duration would be to look
         * at the first and last timestamps from all of the messages on a topic.
         *
         * Unfortunately, this is slow, because it's impossible to index directly to an
         * arbitrary message in a bag file; it would require iterating through every message
         * on the topic, and that can take a while for big topics.
         *
         * Instead, we figure out reasonable estimates by collecting up to the first ten
         * timestamps on a topic, determining the average period between them, and combining
         * that with the number of messages on a topic (which is retrieved from the connection
         * header) to estimate the frame rate and duration.
         * @param message The message to process; should have a Header with a valid stamp
         * @param connection The connection the message arrived on; unused
         * @return true as long as we're still processing frames, false if there was an error
         *              or we've collected enough frames.
         */
        @Override
        public boolean process(com.github.swrirobotics.bags.reader.messages.serialization.MessageType message,
                               Connection connection) {
            if (myCurrentFrame > FRAMES_TO_COUNT) {
                return false;
            }

            long timeMs;
            if (message.getType().equals("stereo_msgs/DisparityImage")) {
                message = message.getField("image");
            }
            com.github.swrirobotics.bags.reader.messages.serialization.MessageType header =
                    message.getField("header");
            TimeType time = header.getField("stamp");

            try {
                timeMs = time.getValue().getTime();
            }
            catch (UninitializedFieldException e) {
                myLogger.warn("Message had uninitialized timestamp in header.");
                return false;
            }

            myFrameTimes.add(timeMs);

            myCurrentFrame++;

            return true;
        }

        private void calculateFrameRate() {
            if (myFrameTimes.size() > 1) {
                long sum = 0;
                long previousTimeMs = myFrameTimes.get(0);

                for (int i = 1; i < myFrameTimes.size(); i++) {
                    sum += myFrameTimes.get(i) - previousTimeMs;
                    previousTimeMs = myFrameTimes.get(i);
                }

                double avgPeriodS = ((double)sum / (double)(myFrameTimes.size() - 1)) / 1000.0;

                myFrameRate = 1.0 / avgPeriodS;
                myDurationS = (double)myTotalFrameCount * avgPeriodS;

                myFrameTimes.clear();
            }
        }

        double getFrameRate() {
            calculateFrameRate();

            return this.myFrameRate;
        }

        double getDurationS() {
            calculateFrameRate();

            return myDurationS;
        }
    }

    /**
     * Runs ffmpeg as an external process in order to convert an image topic
     * into a VP8 video stream.
     */
    private class FfmpegImageHandler implements MessageHandler {
        private boolean myIsBigEndian = false;
        private boolean myIsInitialized = false;
        private long myFrameCount = 0;
        private final double myDurationS;
        private final double myFrameRate;
        private int myHeight = 0;
        private int myWidth = 0;
        private long myFrameSkip = 1;
        private OutputConsumer myConsumer = null;
        private final OutputStream myOutput;
        private Process myFfmpegProc = null;
        private String myPixelFormat = "";
        private int byteNb = 3;

        private class OutputConsumer extends Thread {
            @Override
            public void run() {
                try {
                    myLogger.debug("Piping data from ffmpeg to the client.");
                    // IOUtils.copy will block until the input stream is closed,
                    // so it needs to run in a separate thread.
                    IOUtils.copy(myFfmpegProc.getInputStream(), myOutput);
                }
                catch (IOException e) {
                    if (e.getClass().getTypeName().equals("org.apache.catalina.connector.ClientAbortException")) {
                        myLogger.warn("Client disconnected.");
                    }
                    else {
                        myLogger.error("Error processing ffmpeg output:", e);
                    }
                }
                finally {
                    myLogger.debug("Finished processing output from ffmpeg.");
                    IOUtils.closeQuietly(myOutput);
                }
            }
        }

        FfmpegImageHandler(OutputStream output, double frameRate, double durationS) {
            myOutput = output;
            myFrameRate = frameRate;
            myDurationS = durationS;
            myLogger.info("Starting video stream.");
        }

        void setFrameSkip(long frameSkip) {
            this.myFrameSkip = frameSkip;
        }

        @Override
        public boolean process(com.github.swrirobotics.bags.reader.messages.serialization.MessageType message,
                               Connection connection) {
            if (myIsInitialized) {
                if (myFrameCount % myFrameSkip != 0) {
                    myFrameCount++;
                    return true;
                }

                if (!myConsumer.isAlive()) {
                    // After we've initialized ffmpeg and started processing frames, this thread
                    // should be alive until we've finished.  If it dies early, that means the
                    // client disconnected, so there's no point in continuing.
                    myLogger.debug("Consumer thread terminated early.");
                    return false;
                }
            }
            try {
                String messageType = message.getPackage() + "/" + message.getType();
                boolean isDisparity = messageType.equals("stereo_msgs/DisparityImage");
                boolean isCompressed = messageType.equals("sensor_msgs/CompressedImage");
                float minDisparity = 0.0f;
                float maxDisparity = 0.0f;

                if (isDisparity) {
                    // If we're examining a DisparityImage, it contains the actual image
                    // inside it in a field named "image".  We can just get that and
                    // continue as normal.
                    minDisparity = message.<Float32Type>getField("min_disparity").getValue();
                    maxDisparity = message.<Float32Type>getField("max_disparity").getValue();
                    message = message.getField("image");
                }

                ArrayType dataArray = message.getField("data");
                byte[] byteData;

                if (isCompressed) {
                    byteData = processCompressedImage(dataArray);
                }
                else if (isDisparity) {
                    byteData = processDisparityImage(dataArray, minDisparity, maxDisparity);
                }
                else {
                    // If it's not compressed, and it's a regular image, just get the raw image.
                    if (!myIsInitialized) {
                        myIsBigEndian = message.<UInt8Type>getField("is_bigendian").getValue() > 0;
                    }
                    dataArray.setOrder(myIsBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

                    byteData = dataArray.getAsBytes();
                }

                if (byteData == null) {
                    myLogger.error("Unable to retrieve image bytes.");
                    return false;
                }

                if (!myIsInitialized) {
                    String rosEncoding;
                    if (!isCompressed) {
                        // For uncompressed images, including disparity, we need to pull the
                        // encoding, height, and width from the image.  Assume all images on
                        // the same topic after the first have the same parameters.
                        // For compressed images, these will be encoded in the image data
                        // and are set by the processCompressedImage method.
                        rosEncoding = message.<StringType>getField("encoding").getValue().trim().toLowerCase();
                        myPixelFormat = convertRosEncodingToFfmpeg(rosEncoding);

                        myHeight = message.<UInt32Type>getField("height").getValue().intValue();
                        myWidth = message.<UInt32Type>getField("width").getValue().intValue();
                    }
                    else {
                        rosEncoding = message.<StringType>getField("format").getValue().trim().toLowerCase();
                        if (rosEncoding.contains("bgr8") && myPixelFormat.equals("bgr24")) {
                            // If the compressed image is in bgr, Java's ImageIO will flip the channels to rgb when
                            // it reads it in, which means that telling ffmpeg it's still bgr will cause it to flip
                            // them back.  Instead we'll just tell ffmpeg that it's rgb24 now...
                            myPixelFormat = "rgb24";
                        }
                    }

                    myIsInitialized = true;
                    myLogger.debug("Image format: " + myPixelFormat +
                                   " / " + myWidth + "x" + myHeight +
                                   " / " + (myDurationS / (double)myFrameSkip) + "s" +
                                   " / " + myFrameRate + " Hz");

                    startFfmpeg();
                }

                IOUtils.write(byteData, myFfmpegProc.getOutputStream());

                myFrameCount++;

                return true;
            }
            catch (Exception e) {
                if (e.getClass().getTypeName().equals("org.apache.catalina.connector.ClientAbortException")) {
                    myLogger.warn("Client disconnected.");
                }
                else {
                    myLogger.error("Error encoding video:", e);
                }
                return false;
            }
        }

        /**
         * Reads in a compressed image from a binary stream using ImageIO and returns
         * the decompressed bytes.  This also has a side effect of setting the
         * myPixelFormat, myWidth, and myHeight member variables based on properties
         * found in the compressed image.
         * @param dataArray An array from a ROS message containing a compressed image.
         * @return The decompressed image's bytes.
         * @throws IOException If there was an error reading the image.
         */
        private byte[] processCompressedImage(ArrayType dataArray) throws IOException {
            // If the image is compressed, we need to decompress it and get a few
            // pieces of metadata from it.
            byte[] compressedData = dataArray.getAsBytes();
            byte[] byteData;
            try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData)) {
                BufferedImage image = ImageIO.read(byteStream);
                if (!myIsInitialized) {
                    // Only need to check these things for the first image; assume
                    // the rest are the same.
                    myWidth = image.getWidth();
                    myHeight = image.getHeight();
                    switch (image.getType()) {
                        case BufferedImage.TYPE_3BYTE_BGR:
                            myPixelFormat = "bgr24";
                            byteNb = 3;
                            break;
                        case BufferedImage.TYPE_BYTE_GRAY:
                            myPixelFormat = "gray";
                            byteNb = 1;
                            break;
                        default:
                            myLogger.warn("Unexpected encoding type: " + image.getType());
                            return null;
                    }
                }

                // This probably isn't the most efficient way to do it, but it's
                // easiest for us to just extract the raw image bytes so we can
                // pipe them into ffmpeg the same way as an uncompressed image.
                byteData = new byte[myWidth * myHeight * byteNb];
                int[] intData = new int[myWidth * myHeight * byteNb];
                image.getData().getPixels(0, 0, myWidth, myHeight, intData);
                for (int i = 0; i < intData.length; i++) {
                    byteData[i] = (byte) intData[i];
                }
            }

            return byteData;
        }

        /**
         * Reads in a disparity image and transforms it into a displayable RGB8 image.
         * Disparity images are sequences of 32-bit floating point values in a single channel
         * (32FC1 in OpenCV terms) that are constrained between a minimum and maximum value.
         * To make the output easier for a human to visually process, we map those values to
         * integers between 0 and 255 and then put it through a Jet color map.
         * @param dataArray A ROS byte array containing a disparity image.
         * @param minDisparity The minimum of any disparity value.
         * @param maxDisparity The maximum of any dispairty value.
         * @return A color RGB8 image representing the disparity.
         */
        private byte[] processDisparityImage(ArrayType dataArray, float minDisparity, float maxDisparity) {
            // For disparity images, we have to convert them into a format
            // that ffmpeg can interpret.
            dataArray.setOrder(ByteOrder.LITTLE_ENDIAN);
            float[] floatData = dataArray.getAsFloats();
            float multiplier = 255.0f / (maxDisparity - minDisparity);
            byte[] byteData = new byte[floatData.length];
            for (int i = 0; i < floatData.length; i++) {
                byteData[i] = (byte)Math.min(255.0f,
                                             Math.max(0.0f,
                                                      (floatData[i] - minDisparity) * multiplier));
            }
            // At this point we've got an 8-bit grayscale image, but we
            // can make it prettier by putting it through a color map.
            Mat grayMat = new Mat(myHeight, myWidth, CvType.CV_8UC1);
            grayMat.put(0, 0, byteData);
            Mat colorMat = new Mat();
            Imgproc.applyColorMap(grayMat, colorMat, Imgproc.COLORMAP_JET);
            byteData = new byte[(int)colorMat.total() * colorMat.channels()];
            colorMat.get(0, 0, byteData);

            return byteData;
        }

        /**
         * Launches ffmpeg as an external process and passes in all of the command
         * line parameters necessary for us to pipe raw images into stdin and
         * get VP8 frames from stdout.
         * @throws IOException If there was an error launching ffmpeg.
         */
        private void startFfmpeg() throws IOException {
            // TODO This should probably be configurable.  Lower-power system will
            // want a lower bitrate...
            String bitrate = "1M";
            String durationStr = Double.toString(myDurationS / (double)myFrameSkip);
            String frameRateStr = Double.toString(myFrameRate);
            // Generate key frames for seeking every 3 seconds
            String keyFrameRate = Double.toString(3*myFrameRate);
            // use (n-1) of n available processors, minimum 1
            String numThreads = Integer.toString(Math.min(16, Math.max(Runtime.getRuntime().availableProcessors()-1, 1)));

            // Default encoding to create a VP8 stream
            String[] command = new String[]{"ffmpeg",
                    "-f", "rawvideo",
                    "-c:v", "rawvideo",
                    "-pix_fmt", myPixelFormat,
                    "-s:v", myWidth + "x" + myHeight,
                    "-r:v", frameRateStr,
                    "-i", "pipe:0",
                    "-c:v", "libvpx",
                    "-f", "webm",
                    "-auto-alt-ref", "0",
                    "-minrate", bitrate,
                    "-maxrate", bitrate,
                    "-b:v", bitrate,
                    "-threads", numThreads,
                    "-crf", "10",
                    "-t", durationStr,
                    "-g", keyFrameRate,
                    "pipe:1",
                    "-v", "warning"
            };

            // Faster encoding
            if (myConfigService.getConfiguration().getFasterCodec()) {
                command = new String[]{"ffmpeg",
                        "-f", "rawvideo",
                        "-c:v", "rawvideo",
                        "-pix_fmt", myPixelFormat,
                        "-s:v", myWidth + "x" + myHeight,
                        "-r:v", frameRateStr,
                        "-i", "pipe:0",
                        "-c:v", "libvpx",
                        "-f", "webm",
                        "-auto-alt-ref", "0",
                        "-vf", "scale=400:-1",
                        "-threads", numThreads,
                        "-crf", "28",
                        "-r", "24",
                        "-t", durationStr,
                        "-g", keyFrameRate,
                        "pipe:1",
                        "-v", "warning"
                };
            }

            myFfmpegProc = Runtime.getRuntime().exec(command);

            myConsumer = new OutputConsumer();
            myConsumer.start();

            myLogger.info("Beginning to stream image data to ffmpeg.");
        }

        /**
         * Maps a string containing a ROS image encoding to a pixel format that
         * ffmpeg can understand.
         * ROS encodings: http://wiki.ros.org/cv_bridge/Tutorials/UsingCvBridgeToConvertBetweenROSImagesAndOpenCVImages
         * ffmpeg encodings: Execute 'ffmpeg -pix_fmts'
         * @param encoding A ROS image encoding string
         * @return The equivalent ffmpeg pixel format
         */
        private String convertRosEncodingToFfmpeg(String encoding) {
            // Many image formats have the same name between ffmpeg and ROS, but
            // some don't, so convert them...
            switch (encoding.toLowerCase()) {
                case "bgr8":
                    return "bgr24";
                case "32fc1":
                    // If the pixel format is "32fc1", that means we're actually rendering a
                    // disparity image, which is a single-channel image made of 32-bit floats.
                    // We convert that to an rgb8 image using a OpenCV color map.
                case "8uc3":
                case "rgb8":
                    return "rgb24";
                case "8uc4":
                case "bgra8":
                    return "bgra";
                case "rgba8":
                    return "rgba";
                case "8uc1":
                case "mono8":
                    return "gray";
                case "16uc1":
                case "mono16":
                    if (myIsBigEndian) {
                        return "gray16be";
                    }
                    else {
                        return "gray16le";
                    }
                default:
                    return encoding;
            }
        }

        /**
         * Closes ffmpeg's output stream, which will make the process exit
         * and produce any frames it has remaining.
         * Also prints out anything that ffmpeg printed on stderr.
         */
        void finish() {
            if (myFfmpegProc != null) {
                IOUtils.closeQuietly(myFfmpegProc.getOutputStream());
                try {
                    myConsumer.join();

                    List<String> lines =
                            IOUtils.readLines(myFfmpegProc.getErrorStream(), StandardCharsets.UTF_8);
                    String output = Joiner.on("\n").skipNulls().join(lines).trim();
                    if (!output.isEmpty()) {
                        myLogger.error("ffmpeg output:\n" + Joiner.on("\n").join(lines));
                    }
                }
                catch (InterruptedException e) {
                    myLogger.warn("Interrupted waiting for consumer to finish.");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    void writeVideoStream(Long bagId, String topicName, Long frameSkip, OutputStream output) throws BagReaderException,
        NonexistentBagException {

        try (BagWrapper wrapper = getBagWrapper(bagId)) {
            BagFile bagFile = wrapper.getBagFile();

            long messageCount = -1;
            for (TopicInfo topic : bagFile.getTopics()) {
                if (topic.getName().equals(topicName)) {
                    messageCount = topic.getMessageCount();
                    break;
                }
            }
            myLogger.debug("Expecting " + messageCount + " frames.");
            myLogger.debug("Reading message from bag " + bagId +
                           " on topic [" + topicName + "]");

            // We need to set the frame rate of the video we're producing, but
            // that's not encoded anywhere in a ROS image.  So, we'll quickly
            // examine the first ten frames and estimate the frame rate from them.
            FrameRateDeterminer determiner = new FrameRateDeterminer(messageCount);
            bagFile.forMessagesOnTopic(topicName, determiner);

            // Now we can actually convert the images to a WebM stream.
            FfmpegImageHandler handler = new FfmpegImageHandler(output,
                                                                determiner.getFrameRate(),
                                                                determiner.getDurationS());
            handler.setFrameSkip(frameSkip);
            bagFile.forMessagesOnTopic(topicName, handler);
            handler.finish();
        }
        catch (BagReaderException e) {
            String msg = "Unable to read image for bag " + bagId + ": " + e.getLocalizedMessage();
            myLogger.error(msg, e);
            throw new BagReaderException(e);
        }
        catch (Exception e) {
            myLogger.error("Unexpected exception: " + e.getLocalizedMessage());
            throw new BagReaderException(e);
        }
        finally {
            myLogger.info("Done streaming video.");
        }
    }

    private byte[] getCompressedImage(com.github.swrirobotics.bags.reader.messages.serialization.MessageType mt)
            throws IOException, UninitializedFieldException {
        String type = mt.<StringType>getField("format").getValue();
        ArrayType data = mt.getField("data");
        byte[] byteData = data.getAsBytes();

        if (type.equalsIgnoreCase("jpeg")) {
            return byteData;
        }

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(byteData)) {
            // If it's not a JPEG, convert it to one
            BufferedImage image = ImageIO.read(byteStream);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", stream);

            return stream.toByteArray();
        }
    }

    private byte[] convertImageToJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", stream)) {
            myLogger.warn("ImageIO said it couldn't find a valid writer.");
        }

        return stream.toByteArray();
    }

    private BufferedImage getUncompressedImage(com.github.swrirobotics.bags.reader.messages.serialization.MessageType mt)
            throws UninitializedFieldException, BagReaderException {

        String encoding = mt.<StringType>getField("encoding").getValue().trim().toLowerCase();
        int imageType;
        // TODO Implement support for 16-bit image types and OpenCV CvMat types
        // First, figure out what type of image we're displaying...
        switch (encoding) {
            case "rgb8":
            case "bayer_rggb8":
            case "bayer_bggr8":
            case "bayer_gbrg8":
            case "bayer_grbg8":
                imageType = BufferedImage.TYPE_INT_RGB;
                break;
            case "bgra8":
            case "rgba8":
                imageType = BufferedImage.TYPE_INT_ARGB;
                break;
            case "bgr8":
                imageType = BufferedImage.TYPE_INT_BGR;
                break;
            case "mono8":
            case "8uc1":
                imageType = BufferedImage.TYPE_BYTE_GRAY;
                break;
            case "mono16":
                imageType = BufferedImage.TYPE_USHORT_GRAY;
                break;
            default:
                String errorMsg = "Unsupported image encoding: " + encoding;
                myLogger.warn(errorMsg);
                throw new BagReaderException(errorMsg);
        }

        // Then get metadata about the image...
        int height = mt.<UInt32Type>getField("height").getValue().intValue();
        int width = mt.<UInt32Type>getField("width").getValue().intValue();
        Short isBigEndian = mt.<UInt8Type>getField("is_bigendian").getValue();
        ArrayType dataArray = mt.getField("data");
        if (isBigEndian > 0) {
            dataArray.setOrder(ByteOrder.BIG_ENDIAN);
        }

        byte[] byteData = dataArray.getAsBytes();
        return decodeImage(width, height, encoding, imageType, byteData);
    }

    public BufferedImage decodeImage(int width, int height, String encoding, int imageType, byte[] byteData) {
        if (encoding.startsWith("bayer")) {
            // If the image is in a Bayer filter format, use OpenCV
            // to convert it to RGB8.
            byteData = convertBayer(width, height, byteData, encoding);
        }

        // ImageIO won't output four-channel images to JPEG, so we have to downmix them to 3 channels, and
        // we also need to swap the red and blue channels for bgra8
        int[] mixChannels;
        // See https://docs.opencv.org/3.4/d2/de8/group__core__array.html#ga51d768c270a1cdd3497255017c4504be
        switch (encoding) {
            case "bgra8":
                mixChannels = bgra2rgb;
                break;
            case "rgba8":
                mixChannels = rgba2rgb;
                break;
            default:
                mixChannels = null;
                break;
        }
        if (mixChannels != null) {
            byteData = mix2rgb(width, height, byteData, mixChannels);
        }

        // Java's ImageIO expects pixel data as an array of ints, so
        // cast them all...
        int[] intData = new int[byteData.length];
        for (int i = 0; i < byteData.length; i++) {
            intData[i] = byteData[i];
        }

        if (imageType == BufferedImage.TYPE_INT_ARGB) {
            imageType = BufferedImage.TYPE_INT_RGB;
        }

        BufferedImage image = new BufferedImage(width, height, imageType);
        image.getRaster().setPixels(0, 0, width, height, intData);
        return image;
    }

    private byte[] convertBayer(int width, int height, byte[] input, String encoding) {
        int type;
        int pattern;
        if (encoding.startsWith("bayer_rggb")) {
            pattern = Imgproc.COLOR_BayerBG2RGB;
        }
        else if (encoding.startsWith("bayer_bggr")) {
            pattern = Imgproc.COLOR_BayerRG2RGB;
        }
        else if (encoding.startsWith("bayer_gbrg")) {
            pattern = Imgproc.COLOR_BayerGR2RGB;
        }
        else {
            pattern = Imgproc.COLOR_BayerGB2RGB;
        }
        if (encoding.endsWith("8")) {
            type = CvType.CV_8U;
        }
        else {
            type = CvType.CV_16U;
        }
        Mat sourceMat = new Mat(height, width, type);
        sourceMat.put(0, 0, input);
        Mat destMat = new Mat(height, width, type);
        Imgproc.cvtColor(sourceMat, destMat, pattern);
        byte[] output = new byte[(int)destMat.total() * destMat.channels()];
        destMat.get(0, 0, output);
        return output;
    }

    private byte[] mix2rgb(int width, int height, byte[] input, int[] mixChannels) {
        MatOfInt fromto = new MatOfInt(mixChannels);
        var sourceMatList = Lists.newArrayList(new Mat(height, width, CvType.CV_8UC4));
        sourceMatList.get(0).put(0, 0, input);
        var destMatList = Lists.newArrayList(new Mat(height, width, CvType.CV_8UC3));
        Core.mixChannels(sourceMatList, destMatList, fromto);
        var destMat = destMatList.get(0);
        byte[] output = new byte[(int)destMat.total() * destMat.channels()];
        destMat.get(0, 0, output);
        return output;
    }

    @Transactional
    public void removeDuplicateBags() {
        String msg = "Removing duplicate bag files.";
        myLogger.info(msg);
        reportStatus(Status.State.WORKING, msg);
        List<Bag> bags = myBagRepository.findAll();
        Map<String, List<Bag>> md5Bags = Maps.newHashMap();

        for (Bag bag : bags) {
            List<Bag> tmp = md5Bags.computeIfAbsent(bag.getMd5sum(), k -> Lists.newArrayList());
            tmp.add(bag);
        }

        myLogger.info("Found " + bags.size() + " bags with " +
                      md5Bags.keySet().size() + " different MD5s.");

        for (List<Bag> sublist : md5Bags.values()) {
            if (sublist.size() > 1) {
                myLogger.debug("Found " + sublist.size() +
                    " duplicates for MD5 sum " + sublist.get(0).getMd5sum() + ".");
                for (int i = 1; i < sublist.size(); i++) {
                    Bag dupBag = sublist.get(i);
                    msg = "Removing bag w/ ID " + dupBag.getMd5sum();
                    myLogger.debug(msg);
                    reportStatus(Status.State.WORKING, msg);
                    myBagRepository.delete(dupBag);
                }
            }
        }
        msg = "Done removing duplicates.";
        myLogger.info(msg);
        reportStatus(Status.State.IDLE, msg);
    }

    public Point makePoint(Double latitude, Double longitude) {
        return myGeometryFactory.createPoint(new Coordinate(longitude, latitude));
    }

    @Transactional
    public void updateBag(Bag newBag) {
        Bag dbBag = myBagRepository.findById(newBag.getId()).orElseThrow();
        dbBag.setDescription(newBag.getDescription());
        if (newBag.getLatitudeDeg() != null && newBag.getLongitudeDeg() != null)
        {
            dbBag.setCoordinate(makePoint(newBag.getLatitudeDeg(), newBag.getLongitudeDeg()));
        }
        else
        {
            dbBag.setCoordinate(null);
        }
        dbBag.setLocation(newBag.getLocation());
        dbBag.setVehicle(newBag.getVehicle());
        dbBag.getTags().addAll(newBag.getTags());
        dbBag.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
        myBagRepository.save(dbBag);
    }

    public void uploadBag(MultipartFile file, String targetDirectory, String storageId) throws IOException {
        BagStorage storage = myBagStorages.get(storageId);
        if (storage == null) {
            throw new IOException("Unknown bag storage: " + storageId);
        }
        storage.uploadBag(file, targetDirectory);
    }

    private Pageable createPageRequest(int page, int size, String dir, String sort) {
        // ExtJS starts counting pages at 1, but Spring Data JPA starts counting at 0.
        return PageRequest.of(page-1,
                               size,
                               dir.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
                               sort);
    }

    private <X> Predicate fromFilter(ExtJsFilter filter, CriteriaBuilder cb, Path<X> path) {
        Timestamp ts = null;
        switch (filter.getProperty()) {
            case "startTime":
            case "endTime":
            case "updatedOn":
            case "createdOn":
                ts = new Timestamp(Long.parseLong(filter.getValue()));
                break;
        }

        Path propertyPath = path.get(filter.getProperty());

        Predicate pred = null;

        switch (filter.getOperator()) {
            case "like":
                pred = cb.like(cb.lower(propertyPath), "%" + filter.getValue().toLowerCase() + "%");
                break;
            case "lt":
                if (ts != null) {
                    pred = cb.lessThan(propertyPath, ts);
                }
                else {
                    pred = cb.lessThan(propertyPath, Double.valueOf(filter.getValue()));
                }
                break;
            case "gt":
                if (ts != null) {
                    pred = cb.greaterThan(propertyPath, ts);
                }
                else {
                    pred = cb.greaterThan(propertyPath, Double.valueOf(filter.getValue()));
                }
                break;
            case "eq":
                if (ts != null) {
                    pred = cb.equal(propertyPath, ts);
                }
                else {
                    pred = cb.equal(path.get(filter.getProperty()), Double.valueOf(filter.getValue()));
                }
                break;
            case "=":
                pred = cb.equal(path.get(filter.getProperty()), filter.getValue().equals("true"));
                break;
            default:
                break;
        }

        return pred;
    }

    private Predicate fullTextPredicate(final String text,
                                        final String[] fields,
                                        CriteriaBuilder cb,
                                        Root<Bag> root) {
        final String wildcardText = "%" + text.toLowerCase() + "%";
        // We'll be searching through the text fields in all of the related tables
        // Fields that currently aren't being searched: md5sum
        // md5sum because nobody really cares about that

        List<Predicate> preds = Lists.newArrayList();
        for (String field : fields) {
            switch(field) {
                case "messageType":
                    Join<Bag, MessageType> mtJoin = root.join(Bag_.messageTypes, JoinType.LEFT);
                    preds.add(cb.like(cb.lower(mtJoin.get(MessageType_.name)), wildcardText));
                    break;
                case "tags":
                    Join<Bag, Tag> tagJoin = root.join(Bag_.tags, JoinType.LEFT);
                    preds.add(cb.like(cb.lower(tagJoin.get(Tag_.tag)), wildcardText));
                    preds.add(cb.like(cb.lower(tagJoin.get(Tag_.value)), wildcardText));
                    break;
                case "topicName":
                    Join<Bag, Topic> topicJoin = root.join(Bag_.topics, JoinType.LEFT);
                    preds.add(cb.like(cb.lower(topicJoin.get(Topic_.topicName)), wildcardText));
                    break;
                default:
                    preds.add(cb.like(cb.lower(root.get(field)), wildcardText));
                    break;
            }
        }

        return cb.or(preds.toArray(new Predicate[0]));
    }

    @Transactional(readOnly = true)
    public BagList findBagsContainingText(final String text,
                                          final String[] fields,
                                          final ExtJsFilter[] filters,
                                          int page,
                                          int size,
                                          String dir,
                                          String sort) {
        myLogger.trace("Executing specification.");

        Page<Bag> bags;
        Pageable pageReq = createPageRequest(page, size, dir, sort);

        if ((text == null || text.trim().isEmpty() || fields == null || fields.length == 0) &&
            (filters == null || filters.length == 0)) {
            bags = myBagRepository.findAll(pageReq);
        }
        else {
            bags = myBagRepository.findAll((root, query, cb) -> {
                // We only want one result per bag file, though.
                query.distinct(true);

                List<Predicate> preds = Lists.newArrayList();
                if (text != null && !text.trim().isEmpty() &&
                    fields != null && fields.length != 0) {
                    preds.add(fullTextPredicate(text, fields, cb, root));
                }
                if (filters != null && filters.length > 0) {
                    for (ExtJsFilter filter : filters) {
                        preds.add(fromFilter(filter, cb, root));
                    }
                }

                if (preds.size() == 1) {
                    return preds.get(0);
                }
                else {
                    return cb.and(preds.toArray(new Predicate[0]));
                }
            }, pageReq);
        }
        myLogger.trace("Finished executing.");

        return new BagList(bags.getContent(), bags.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<Double[]> getGpsCoordsForBags(final Collection<Long> bagIds) {
        myLogger.debug("getGpsCoordsForBags: " + Joiner.on(", ").skipNulls().join(bagIds));

        List<BagPosition> positions =
                myBagPositionRepository.findByBagIdInOrderByPositionTimeAsc(bagIds);

        List<Double[]> coords = positions.parallelStream().map(p -> new Double[] {
                p.getLongitude(), p.getLatitude()}).collect(Collectors.toList());

        myLogger.debug("Returning " + coords.size() + " points.");

        return coords;
    }

    @Transactional(readOnly = true)
    public List<Long> getAllBagIds() {
        List<Bag> bags = myBagRepository.findAll();
        List<Long> bagIds = Lists.newArrayListWithCapacity(bags.size());

        bagIds.addAll(bags.parallelStream().map(Bag::getId).collect(Collectors.toList()));

        return bagIds;
    }

    private List<GpsPosition> getAllGpsMessages(BagFile bag) {
        List<GpsPosition> positions = Lists.newArrayList();

        MessageHandler gpsHandler = ((message, conn) -> {
            try {
                positions.add(new GpsPosition(message.getField("latitude"),
                                              message.getField("longitude"),
                                              message.<com.github.swrirobotics.bags.reader.messages.serialization.MessageType>getField(
                                                      "header").getField("stamp")));
            }
            catch (UninitializedFieldException e) {
                return false;
            }
            catch (NullPointerException e) {
                // This will be thrown if the topic we're looking at doesn't actually
                // have "latitude", "longitude", or "header" fields, in which case
                // it's pointless to look at any other messages on this topic.
                return true;
            }
            return true;
        });

        try {
            String[] gpsTopics = myConfigService.getConfiguration().getGpsTopics();
            for (String topic : gpsTopics) {
                bag.forMessagesOnTopic(topic, gpsHandler);
                if (!positions.isEmpty()) {
                    break;
                }
            }

            if (positions.isEmpty()) {
                bag.forFirstTopicWithMessagesOfType("sensor_msgs/NavSatFix", gpsHandler);
            }
            if (positions.isEmpty()) {
                bag.forFirstTopicWithMessagesOfType("gps_common/GPSFix", gpsHandler);
            }
            if (positions.isEmpty()) {
                bag.forFirstTopicWithMessagesOfType("marti_gps_common/GPSFix", gpsHandler);
            }
        }
        catch (BagReaderException e) {
            e.printStackTrace();
        }

        return positions;
    }

    public String getVehicleName(BagFile bag) {
        String[] vehicleNames = myConfigService.getConfiguration().getVehicleNameTopics();
        try {
            for (String topic : vehicleNames) {
                com.github.swrirobotics.bags.reader.messages.serialization.MessageType
                        mt = bag.getFirstMessageOnTopic(topic);
                if (mt != null) {
                    return mt.<StringType>getField("data").getValue().replaceAll("\\p{C}", "").trim();
                }
            }
        }
        catch (BagReaderException | UninitializedFieldException e) {
            // Do nothing
        }
        return null;
    }

    /**
     * Extracts key:value metadata from a bag file and returns it in a map.
     * This assumes you have metadata topics defined in the configuration;
     * for example, "/metadata".  This topic should contain std_msgs/String
     * messages, and each message should be a newline-separated set of tags
     * that consist of key:value pairs separated by colons.  For example,
     * a sample message might contain:
     * "name: John Doe
     * email: jdoe@example.com"
     * This function will create a map that contains every tag on every
     * metadata topic.  If there are any duplicate keys, the values from
     * later messages will overwrite earlier messages.
     * @param bagFile The bag file to read metadata for.
     * @return A map of all of the key:value metadata in the bag.
     */
    public Map<String, String> getMetadata(BagFile bagFile) {
        final Map<String, String> tags = Maps.newHashMap();
        String[] topics = myConfigService.getConfiguration().getMetadataTopics();
        try {
            final Splitter tagSplitter = Splitter.on(':').limit(2).trimResults();
            final Splitter.MapSplitter lineSplitter =
                    Splitter.on(System.getProperty("line.separator")).omitEmptyStrings().trimResults().withKeyValueSeparator(tagSplitter);
            for (String topic : topics) {
                bagFile.forMessagesOnTopic(topic, (message, connection) -> {
                    try {
                        String data = message.<StringType>getField("data").getValue();
                        myLogger.debug("Examining message: " + data);
                        tags.putAll(lineSplitter.split(data));
                    }
                    catch (IllegalArgumentException | UninitializedFieldException e) {
                        reportStatus(Status.State.ERROR,
                            "Unable to parse metadata on topic " + topic + " in bag file " + bagFile.getPath());
                    }
                    return true;
                });
            }
        }
        catch (BagReaderException | java.util.NoSuchElementException e) {
            reportStatus(Status.State.ERROR,
                    "Unable to get metadata from bag file " + bagFile.getPath() + ": " + e.getLocalizedMessage());
        }
        return tags;
    }

    /**
     * Gets all of the known paths in which bags are stored.  The paths here are
     * relative to the configured base directory.
     * @return All known bag paths.
     */
    public List<String> getPaths() {
        Set<String> paths = new HashSet<>();
        for (BagStorage storage : myBagStorages.values()) {
            List<String> tmpPaths = myBagRepository.getDisinctPathsByStorageId(storage.getStorageId());
            List<String> filteredPaths = new ArrayList<>();
            for (String path : tmpPaths) {
                filteredPaths.add(path.replaceFirst(storage.getRootPath(), ""));
            }
            paths.addAll(filteredPaths);
        }
        List<String> sortedPaths = Lists.newArrayList(paths);
        sortedPaths.sort(String::compareToIgnoreCase);
        return sortedPaths;
    }

    /**
     * Extracts metadata tags from a bag file and returns a set of Tags.
     * This differs slightly from getMetadata in that the Tags it returns
     * are suitable for inserting into the database, and the lengths of the
     * value strings are truncated to 255 characters.
     * @param bagFile The bag file to pull tags from.
     * @return A set of all of the tags in the bag file.
     */
    private Set<Tag> extractTagsFromBagFile(BagFile bagFile) {
        Map<String, String> metadata = getMetadata(bagFile);
        Set<Tag> tags = Sets.newHashSet();

        final int MAX_VALUE_LENGTH = 255;

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            Tag tag = new Tag();
            tag.setTag(entry.getKey());
            String value = entry.getValue();
            tag.setValue(value.length() > MAX_VALUE_LENGTH ?
                                 value.substring(0, MAX_VALUE_LENGTH) : value);
            tags.add(tag);
        }

        if (tags.size() > 0) {
            myLogger.debug("Found " + tags.size() + " tags");
        }
        return tags;
    }

    @Transactional
    public void updateGpsPositionsForBagId(long bagId) throws NonexistentBagException {
        try (BagWrapper wrapper = getBagWrapper(bagId)) {
            BagFile bagFile = wrapper.getBagFile();

            Bag bag = myBagRepository.getOne(bagId);
            updateGpsPositions(bag, getAllGpsMessages(bagFile));
            myBagRepository.save(bag);
        }
        catch (BagReaderException | IOException e) {
            reportStatus(Status.State.ERROR,
                         "Unable to get GPS info for bag " + bagId + ": " + e.getLocalizedMessage());
        }
    }

    @Transactional
    public void updateGpsPositions(final Bag bag, Collection<GpsPosition> gpsPositions) {
        List<BagPosition> existingPositions = bag.getBagPositions();
        if (!existingPositions.isEmpty()) {
            myLogger.warn("Adding new GPS positions for a bag that already has " +
                                  "some in the database is not supported.  " +
                                  "Manually remove the old ones first.");
            bag.setHasPath(true);
            return;
        }

        String msg = "Inserting GPS positions for " + bag.getFilename();
        myLogger.debug(msg);
        reportStatus(Status.State.WORKING, msg);
        bag.setHasPath(!gpsPositions.isEmpty());
        for (GpsPosition gpsPos : gpsPositions) {
            BagPosition pos = new BagPosition();
            pos.setBag(bag);
            pos.setPosition(makePoint(gpsPos.latitude, gpsPos.longitude));
            pos.setPositionTime(gpsPos.stamp);
            pos = myBagPositionRepository.save(pos);
            bag.getBagPositions().add(pos);
        }
        msg = "Saved " + gpsPositions.size() + " GPS positions for " +
                bag.getFilename() + ".";
        myLogger.debug(msg);
        reportStatus(Status.State.WORKING, msg);
    }

    @Transactional
    public void removeTagForBag(Collection<String> tagNames,
                                final Long bagId) throws NonexistentBagException {
        if (!myBagRepository.existsById(bagId)) {
            throw new NonexistentBagException("No bag found with ID: " + bagId);
        }

        myTagRepository.deleteByBagIdAndTagIn(bagId, tagNames);
    }

    @Transactional
    public void setTagForBag(String tagName,
                             final String value,
                             final Long bagId) throws NonexistentBagException {
        if (!myBagRepository.existsById(bagId)) {
            throw new NonexistentBagException("No bag found with ID: " + bagId);
        }

        tagName = tagName.trim();

        Tag tag = myTagRepository.findByTagAndBagId(tagName, bagId);
        if (tag == null) {
            myLogger.debug("No tag found with key " + tagName + "; creating a new one.");
            tag = new Tag();
            tag.setTag(tagName);
            tag.setBagId(bagId);
        }

        tag.setValue(value == null ? "" : value.trim());
        myLogger.debug("Setting value of tag with key '" + tagName + "' to '" + tag.getValue() + "'");
        myTagRepository.save(tag);
    }

    @Transactional
    public Bag insertNewBag(final BagWrapper wrapper,
                            final String md5sum,
                            final String locationName,
                            final List<GpsPosition> gpsPositions,
                            final String storageId) throws BagReaderException, DuplicateBagException {
        Bag bag = myBagRepository.findByMd5sum(md5sum);

        // We checked earlier if there were any other bags with this MD5 sum,
        // but that was before we entered the synchronized area, so we need
        // to check again just in case somebody managed to insert one before we
        // got the lock.
        if (bag != null) {
            throw new DuplicateBagException("Duplicate of: " + bag.getStorageId() + ":" +
                bag.getPath() + bag.getFilename());
        }

        bag = new Bag();

        BagFile bagFile = wrapper.getBagFile();
        String absPath = wrapper.getPath() + wrapper.getFilename();

        myLogger.info("Adding new bag: " + absPath);
        // If it doesn't exist in the DB, create a new entry.
        bag.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        bag.setPath(wrapper.getPath());
        bag.setFilename(wrapper.getFilename());
        bag.setMd5sum(md5sum);
        bag.setCompressed(false);
        bag.setDuration(bagFile.getDurationS());
        bag.setStartTime(bagFile.getStartTime());
        bag.setEndTime(bagFile.getEndTime());
        bag.setIndexed(bagFile.isIndexed());
        bag.setMessageCount(bagFile.getMessageCount());
        bag.setMissing(false);
        bag.setSize(bagFile.getPath().toFile().length());
        bag.setStorageId(storageId);
        bag.setVersion(bagFile.getVersion());
        bag.setVehicle(getVehicleName(bagFile));
        if (!gpsPositions.isEmpty()) {
            GpsPosition pos = gpsPositions.get(0);
            bag.setCoordinate(makePoint(pos.latitude, pos.longitude));
        }
        bag.setLocation(locationName);
        bag = myBagRepository.save(bag);
        myLogger.trace("Initial bag save for " + absPath);

        Map<String, MessageType> dbMessageTypes = addMessageTypesToBag(bagFile, bag);

        addTopicsToBag(bagFile, bag, dbMessageTypes);
        addTagsToBag(bagFile, bag);

        updateGpsPositions(bag, gpsPositions);

        return bag;
    }

    @Transactional
    protected Map<String, MessageType> addMessageTypesToBag(final BagFile bagFile, final Bag bag) {
        myLogger.trace("Adding message types.");
        Multimap<String, String> messageTypes = bagFile.getMessageTypes();
        Map<String, MessageType> dbMessageTypes = new HashMap<>();
        for (Map.Entry<String, String> entry : messageTypes.entries()) {
            MessageType mt = getMessageType(entry.getKey(), entry.getValue(), bag);
            dbMessageTypes.put(entry.getKey(), mt);
        }

        return dbMessageTypes;
    }

    @Transactional
    protected void addTopicsToBag(final BagFile bagFile,
                                final Bag bag,
                                final Map<String, MessageType> dbMessageTypes) throws BagReaderException {
        myLogger.trace("Adding topics.");
        List<TopicInfo> topics = bagFile.getTopics();
        for (TopicInfo topic : topics) {
            MessageType dbType = dbMessageTypes.get(topic.getMessageType());
            if (dbType == null) {
                myLogger.trace("Need to add new message type. That's a little odd, " +
                               "addMessageTypesToBag should've gotten them all.");
                dbType = getMessageType(
                        topic.getMessageType(),
                        topic.getMessageMd5Sum(),
                        bag);
                dbMessageTypes.put(topic.getMessageType(), dbType);
            }
            else {
                myLogger.trace("Found cached message type.");
            }

            myLogger.trace("Finding existing topics.");
            List<Topic> bagTopics = myTopicRepository.findByTopicNameAndBagId(topic.getName(), bag.getId());
            //myLogger.info("Found " + bagTopics + " existing topics.");
            Topic dbTopic;
            if (!bagTopics.isEmpty()) {
                dbTopic = bagTopics.get(0);
            }
            else {
                //myLogger.info("Creating new topic.");
                dbTopic = new Topic();
            }
            dbTopic.setTopicName(topic.getName());
            dbTopic.setType(dbType);
            dbTopic.setMessageCount(topic.getMessageCount());
            dbTopic.setConnectionCount(topic.getConnectionCount());
            dbTopic.setBag(bag);
            bag.getTopics().add(dbTopic);
        }
    }

    @Transactional
    public void addTagsToBag(final BagFile bagFile,
                             final Bag bag) {
        myLogger.trace("Adding tags to " + bagFile.getPath());
        Set<Tag> bagTags = extractTagsFromBagFile(bagFile);

        // Note that this method doesn't *synchronize* tags between the bag file and
        // the database, it only adds ones that exist in the bag file to the database.
        // Tags that a user has created will not be removed, although ones that have
        // been modified from values that exist in the bag file will be overwritten.
        // TODO Is that desirable, or should user-entered tags take precedence?

        for (Tag bagTag : bagTags) {
            boolean found = false;
            for (Tag dbTag : bag.getTags()) {
                if (dbTag.getTag().equals(bagTag.getTag())) {
                    found = true;
                    if (!dbTag.getValue().equals(bagTag.getValue())) {
                        myLogger.debug("Updating existing tag '" + dbTag.getTag() +
                                       "': Old tag value: '" + bagTag.getValue() +
                                       "'; New tag value: '" + dbTag.getValue() + "')");
                        dbTag.setValue(bagTag.getValue());
                        myTagRepository.save(dbTag);
                        break;
                    }
                }
            }
            if (!found) {
                myLogger.debug("Saving new tag '" + bagTag.getTag() + ": " + bagTag.getValue() + "'");
                bagTag.setBag(bag);
                bag.getTags().add(bagTag);
                myTagRepository.save(bagTag);
            }
        }
    }

    public void updateBagFile(final BagWrapper wrapper,
                              final String storageId,
                              final Map<String, Long> missingBagMd5sums) {
        String absPath = wrapper.getPath() + wrapper.getFilename();
        myLogger.debug("Checking " + absPath + "...");
        reportStatus(Status.State.WORKING, "Processing " + absPath + ".");

        BagFile bagFile;
        try {
            bagFile = wrapper.getBagFile();
        }
        catch (BagReaderException e) {
            myLogger.error("Error reading bag file.", e);
            reportStatus(Status.State.ERROR, "Unable to read " + absPath + ".  Check its permissions.");
            return;
        }

        if (!bagFile.getPath().toFile().canRead()) {
            myLogger.error("Can't read file.");
            reportStatus(Status.State.ERROR, "Unable to read " + absPath + ".  Check its permissions.");
            return;
        }

        String md5sum;
        Timer timer = new Timer();
        try {
            // First, get the MD5 sum so we can see if this bag exists but
            // has been moved.
            TimerTask updateTask = new TimerTask() {
                @Override
                public void run() {
                    reportStatus(Status.State.WORKING,
                                 "Calculating MD5 Sum for " + absPath + "...");
                }
            };
            // Periodically notify the front end if we're still calculating MD5 sums.
            // Otherwise, if we're analyzing multiple bags in parallel, an error could
            // occur that might make the user think we're not working on anything else.
            timer.scheduleAtFixedRate(updateTask, 0, 3000);

            md5sum = bagFile.getUniqueIdentifier();
            myLogger.debug("Calculated bag md5sum: " + md5sum);
        }
        catch (BagReaderException e) {
            myLogger.error("Unable to calculate MD5 sum for bag " + absPath, e);
            return;
        }
        finally {
            timer.cancel();
        }

        // It's possible that this file could already be in the database but is marked as missing because it's
        // path changed, so check the missing bags list.
        Long bagId = missingBagMd5sums.get(md5sum);

        // If it's still null, it is still possible that this is a duplicate of another bag, so check that, too.
        if (bagId == null) {
            Bag existingBag = myBagRepository.findByMd5sum(md5sum);
            if (existingBag != null) {
                String msg = "File " + absPath + " is a duplicate of " +
                             existingBag.getStorageId() + ":" + existingBag.getPath() + existingBag.getFilename() + ".";
                reportStatus(Status.State.ERROR, msg);
                myLogger.warn(msg);
                return;
            }
        }

        // Getting the list of GPS positions is a bit expensive, and getting the location
        // name can block while waiting for a network response, so let's do those
        // before locking on the mutex.
        String locationName = null;
        List<GpsPosition> gpsPositions;
        try {
            bagFile = wrapper.getBagFile();

            gpsPositions = getAllGpsMessages(bagFile);
            if (!gpsPositions.isEmpty()) {
                GpsPosition firstPos = gpsPositions.get(0);
                locationName = myGeocodingService.getLocationName(firstPos.latitude, firstPos.longitude);
            }
        }
        catch (BagReaderException e) {
            myLogger.error("Error reading GPS messages from bag file:", e);
            return;
        }

        // We can do the work up to this point in parallel -- mostly calculating
        // md5sums -- but we need to synchronize around DB transactions, since
        // different bags could all try to insert the same types of messages at
        // the same time.
        Bag newBag = null;
        synchronized (myBagDbLock) {
            try {
                newBag = updateBagInDatabase(bagId, wrapper, md5sum, missingBagMd5sums, locationName, gpsPositions,
                    storageId);
                String msg = "Done processing: " + bagFile.getPath().toFile();
                myLogger.debug(msg);
                reportStatus(Status.State.IDLE, msg);
            }
            catch (BagReaderException | DuplicateBagException e) {
                reportStatus(Status.State.ERROR, "Error reading " +
                             absPath + ": " + e.getLocalizedMessage());
                myLogger.error("Error reading bag file: " + absPath, e);
            }
        }

        // If bagId is null but we have a newBag at this point, that means we just inserted
        // a new bag file.  Check to see if we need to run any scripts on it.
        if (bagId == null && newBag != null) {
            runAutomaticScripts(newBag);
        }
    }

    private void runAutomaticScripts(Bag bag) {
        List<Script> scripts = myScriptService.getAutomaticScripts();

        myLogger.debug("Running " + scripts.size() + " scripts on the bag file.");
        for (Script script : scripts) {
            try {
                if (myScriptService.bagMatchesCriteria(bag.getId(), script.getId())) {
                    myLogger.info("Running script " + script.getId()
                        + " [" + script.getName() + "] on new bag " + bag.getId());
                    myScriptService.runScript(script.getId(), Collections.singletonList(bag.getId()));
                }
                else {
                    myLogger.debug("Bag " + bag.getId() + " did not match criteria for script "
                        + script.getId() + " [" + script.getName() + "]");
                }
            }
            catch (ScriptRunException e) {
                myLogger.warn("Error automatically running script", e);
            }
            catch (NonexistentScriptException e) {
                myLogger.error("Script did not exist; this should never happen", e);
            }
            catch (NonexistentBagException e) {
                myLogger.error("Bag did not exist; was it successfully inserted in the DB?", e);
            }
        }
    }

    /***
     * Inserts a new bag or updates an existing bag in the database.
     * @param bagId The database ID of the bag to update; if null, inserts a new bag.
     * @param wrapper The bag to insert.
     * @param md5sum Our calculated MD5 sum of the bag's contents.
     * @param missingBagMd5sums All of the MD5 sums of any bags that have been marked as missing.
     * @param locationName The friendly name of the bag's location, if available.
     * @param gpsPositions GPS coordinates extracted from the bag.
     * @param storageId The identifier of the bag's storage backend.
     * @return The bag that was just inserted.
     * @throws DuplicateBagException If this bag already exists in the database
     * @throws BagReaderException If there is an error reading the bag file
     */
    @Transactional
    public Bag updateBagInDatabase(Long bagId,
                                   final BagWrapper wrapper,
                                   final String md5sum,
                                   final Map<String, Long> missingBagMd5sums,
                                   final String locationName,
                                   final List<GpsPosition> gpsPositions,
                                   final String storageId)
            throws DuplicateBagException, BagReaderException {
        Bag bag;
        if (bagId == null) {
            bag = insertNewBag(wrapper, md5sum, locationName, gpsPositions, storageId);
        }
        else {
            if (missingBagMd5sums.remove(md5sum) != null) {
                myLogger.info("Missing bag was found.");
            }
            else {
                myLogger.info("Force updating bag info.");
            }
            // If we found a missing one, remove it from the list and update
            // its path.
            bag = myBagRepository.findById(bagId).orElseThrow();
            bag.setPath(wrapper.getPath());
            bag.setFilename(wrapper.getFilename());
            bag.setMissing(false);
            bag.setMd5sum(md5sum);
            bag.setStorageId(storageId);
            addTagsToBag(wrapper.getBagFile(), bag);
        }
        myBagRepository.save(bag);
        String msg = "Committing: " + wrapper.getPath() + wrapper.getFilename();
        myLogger.debug(msg);
        reportStatus(Status.State.WORKING, msg);

        return bag;
    }

    @Transactional(readOnly = true)
    public List<BagTreeNode> getTreePath(String targetPath) throws IOException {
        List<BagTreeNode> nodes = Lists.newArrayList();

        for (BagStorage storage : myBagStorages.values()) {
            nodes.addAll(storage.getTreeNodes(targetPath));
        }

        return nodes;
    }


    /**
     * Examines all of the paths on the filesystem known to the bag database and returns
     * a recursive count of how many bags there are under that path that match the
     * given filter text.  If the filter text is empty, all bags will match.
     * @param filterText The text to filter against.
     * @return A set of paths and how many matching bags exist under each path.
     */
    @Transactional(readOnly = true)
    public BagCount[] checkFilteredBagCounts(String filterText) {
        TypedQuery<BagCount> query = myEM.createNamedQuery("Bag.countBagPaths", BagCount.class);
        query.setParameter("text", filterText);
        List<BagCount> results = query.getResultList();
        return results.toArray(new BagCount[0]);
    }

    @Transactional
    public void removeMissingBags() {
        myLogger.info("removeMissingBags()");
        reportStatus(Status.State.WORKING, "Removing missing bag entries.");
        // Using bagRepository.delete here doesn't work.  It just executes another
        // select statement.  No idea why.  Spring Data JPA repositories are so
        // annoying sometimes.
        Query query = myEM.createQuery("delete from Bag b where b.missing = true");
        int numberRemoved = query.executeUpdate();
        String msg = "Removed " + numberRemoved + " missing bags.";
        myLogger.debug(msg);
        reportStatus(Status.State.IDLE, msg);
    }

    private MessageType getMessageType(final String name,
                                       final String md5sum,
                                       final Bag bag) {
        MessageTypeKey key = new MessageTypeKey();
        key.name = name;
        key.md5sum = md5sum;
        MessageType dbType = myMTRepository.findById(key).orElse(null);
        if (dbType == null) {
            myLogger.info("Adding new MessageType to DB: " +
                                  name + " / " + md5sum);
            dbType = new MessageType();
            dbType.setMd5sum(md5sum);
            dbType.setName(name);
        }
        else {
            myLogger.debug("Found existing MessageType in DB: " +
                                   name + " / " + md5sum);
        }
        bag.getMessageTypes().add(dbType);
        return dbType;
    }

    @Override
    protected String getStatusProviderName() {
        return "Bag Service";
    }
}
