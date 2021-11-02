// *****************************************************************************
//
// Copyright (c) 2021, Hatchbed, L.L.C.
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

package com.github.swrirobotics.bags.storage.s3;

import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.storage.*;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.BagRepository;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.BagTreeNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class S3BagStorageImpl extends StatusProvider implements BagStorage {
    private static final Logger myLogger = LoggerFactory.getLogger(S3BagStorageImpl.class);

    private final BagRepository bagRepository;
    private final ConfigService configService;
    private BagService bagService;

    // Contains a list of keys in the S3 bucket so we can detect changes after updating
    private List<String> myKeyCache = null;
    // Configuration set by the BagService
    private S3BagStorageConfigImpl myConfig = null;
    // Client for connecting to an S3 bucket
    private S3Client myS3Client = null;
    // Periodically checks for changes in the bucket
    private Timer myUpdateTimer = null;
    // For controlling concurrent access to myKeyCache
    private final Object myKeyCacheLock = new Object();
    // Objects that should be notified when our file list has changed
    private final Set<BagStorageChangeListener> myChangeListeners = new HashSet<>();

    private class UpdateTask extends TimerTask {
        @Override
        public void run() {
            checkForUpdates();
        }
    }

    public S3BagStorageImpl(BagRepository bagRepository, ConfigService configService) {
        this.bagRepository = bagRepository;
        this.configService = configService;
    }

    @Override
    public void addChangeListener(BagStorageChangeListener listener) {
        myChangeListeners.add(listener);
    }

    @Override
    public boolean bagExists(String path) {
        synchronized (myKeyCacheLock) {
            if (myKeyCache == null) {
                // Just in case this method is called before we've scanned for bags, initialize the key cache.
                initializeKeyCache();
            }
            return myKeyCache.contains(path);
        }
    }

    @Override
    @Transactional
    public void updateBagExistence() {
        myLogger.info(myConfig.storageId + ": updateBagExistence");
        Stream<Bag> bags = bagRepository.findByStorageId(myConfig.storageId);

        bags.forEach(bag -> {
            String fullPath = normalizePath(bag.getPath() + bag.getFilename());
            boolean isMissing = !bagExists(fullPath);
            myLogger.info("Is " + fullPath + " missing? " + isMissing);
            if (isMissing ^ bag.getMissing()) {
                myLogger.info("Bag at " + fullPath + " has " + (isMissing ? "gone missing." : "been found!"));
                bag.setMissing(isMissing);
                bagRepository.save(bag);
            }
        });
    }

    public boolean updateKeyCache(ListObjectsV2Response response) {
        List<String> newKeys = response.contents().parallelStream()
            .map(S3Object::key)
            .sorted(String::compareTo)
            .collect(Collectors.toList());
        synchronized (myKeyCacheLock) {
            if (!newKeys.equals(myKeyCache)) {
                myLogger.info("Keys in S3 have changed.");
                myKeyCache = newKeys;
                return true;
            }
        }
        return false;
    }

    public void initializeKeyCache() {
        updateKeyCache(listObjects());
    }

    public ListObjectsV2Response listObjects() {
        return listObjects(null);
    }

    public ListObjectsV2Response listObjects(String prefix) {
        var builder = ListObjectsV2Request.builder().bucket(myConfig.bucket);
        if (!"root".equals(prefix) && prefix != null) {
            builder = builder.prefix(prefix);
        }
        return myS3Client.listObjectsV2(builder.build());
    }

    public void checkForUpdates() {
        myLogger.trace(getStorageId() + ": checkForUpdates");
        var response = listObjects();
        if (updateKeyCache(response)) {
            updateListeners();
        }
    }

    @Override
    public void updateBags(boolean forceUpdate) {
        myLogger.info(getStorageId() + ": updateBags");
        final Stream<Bag> missingBags = bagRepository.findByStorageIdAndMissing(myConfig.storageId, true);
        final Stream<Bag> existingBags = bagRepository.findByStorageIdAndMissing(myConfig.storageId, false);

        // Get a list of bag files that have gone missing so we can update their locations if we find them
        final Map<String, Long> missingBagMd5sums = missingBags.collect(Collectors.toMap(Bag::getMd5sum, Bag::getId));
        // Get a list of existing bag files so that we don't re-process them
        final Map<String, Long> existingBagPaths = existingBags
            .collect(Collectors.toMap(bag -> normalizePath(bag.getPath() + bag.getFilename()), Bag::getId));

        var response = listObjects();
        updateKeyCache(response);
        for (var object : response.contents()) {
            String filename = object.key();
            if (!filename.endsWith(".bag")) {
                myLogger.debug("Skipping " + filename + " because it doesn't end in .bag.");
                continue;
            }

            if (existingBagPaths.get(filename) != null && !forceUpdate) {
                myLogger.debug("Skipping " + filename + " because it's already in the database and forceUpdate == false.");
                continue;
            }

            myLogger.info("Processing bag file: " + filename);

            // It's a little hackish, but we use the path we use for scripts for storing temporary backs
            // so that the script service can also access them
            try (S3BagWrapperImpl wrapper = new S3BagWrapperImpl(myS3Client, filename,
                configService.getConfiguration().getScriptTmpPath(), this)) {
                bagService.updateBagFile(wrapper, getStorageId(), missingBagMd5sums);
            }
            catch (IOException e) {
                myLogger.error("Error reading bag", e);
            }
            catch (ConstraintViolationException e) {
                // Constraint name is hard-coded in db.changelog-1.0.yaml
                if ("uk_a2r00kd2qd94dohkimsp5rdgn".equals(e.getConstraintName())) {
                    String message = "The data in " + filename + " seems to be a duplicate " +
                        "of an existing bag file.  If you believe this is incorrect, please " +
                        "report it as a bug.";
                    reportStatus(Status.State.ERROR, message);
                    myLogger.warn(message);
                    myLogger.warn(e.getLocalizedMessage());
                }
                else {
                    String message = e.getLocalizedMessage();
                    reportStatus(Status.State.ERROR, "Error checking bag file: " + message);
                    myLogger.error("Unexpected error updating bag file:", e);
                }
            }
            catch (RuntimeException e) {
                reportStatus(Status.State.ERROR,
                    "Error checking bag file: " + e.getLocalizedMessage());
                myLogger.error("Unexpected error updating bag file:", e);
            }
        }
    }

    @Override
    public BagStorageConfiguration getConfig() {
        return myConfig;
    }

    public String getBucket() {
        return myConfig.bucket;
    }

    @Override
    public String getRootPath() {
        return "/";
    }

    @Override
    public String getStorageId() {
        return myConfig.storageId;
    }

    @Override
    public List<BagTreeNode> getTreeNodes(String targetPath) throws IOException {
        myLogger.trace(getStorageId() + ": getTreeNodes: " + targetPath);
        List<BagTreeNode> nodes = new ArrayList<>();

        var response = listObjects(targetPath);
        // The ExtJS tree structure expects a branch node for every directory and a leaf node for every bag.
        // This is a little inconvenient here because S3 does not have any concept of directories; it just returns
        // a list of objects and the "key" is the full path to the file.  "Empty" directories can also be indicated
        // by a key that ends in ".bzEmpty".  Also, this function should only return nodes directly inside targetPath,
        // not any nodes below that.
        // This will attempt to filter that list down to a set of unique directories in targetPath.
        Set<String> paths = response.contents().stream().map(obj -> Splitter.on('/')
            .splitToList(obj.key().replaceFirst(targetPath, "")).get(0))
            .filter(obj -> !obj.isEmpty() && !obj.endsWith(".bzEmpty") && !obj.endsWith(".bag"))
            .collect(Collectors.toSet());
        String parentId = normalizePath(targetPath.equals("root") ? "" : targetPath);
        for (String path : paths) {
            myLogger.debug("Adding branch node: " + path);

            BagTreeNode childNode = new BagTreeNode();
            childNode.filename = path;
            childNode.parentId = parentId;
            childNode.leaf = false;
            childNode.storageId = getStorageId();
            childNode.id = normalizePath(Joiner.on('/').join(parentId, path));
            childNode.expanded = false;
            myLogger.debug("Counting bags in path: " + childNode.id);
            childNode.bagCount = bagRepository.countByPathStartsWithAndStorageId(childNode.id + "/", getStorageId());
            nodes.add(childNode);
        }

        List<Bag> bags = bagRepository.findByPathAndStorageId(parentId + "/", getStorageId());
        myLogger.debug("Found " + bags.size() + " bags in path: " + parentId);
        for (Bag bag : bags) {
            myLogger.debug("Adding leaf node: " + bag.getFilename());
            BagTreeNode childNode = new BagTreeNode();
            childNode.filename = bag.getFilename();
            childNode.parentId = parentId;
            childNode.storageId = getStorageId();
            childNode.leaf = true;
            childNode.id = bag.getPath() + bag.getFilename();
            childNode.bag = bag;
            nodes.add(childNode);
        }

        return nodes;
    }

    @Override
    @Transactional
    public BagWrapper getBagWrapper(Bag bag) {
        String key = normalizePath(bag.getPath() + bag.getFilename());
        return new S3BagWrapperImpl(myS3Client, key, configService.getConfiguration().getScriptTmpPath(), this);
    }

    @Override
    public void loadConfig(BagStorageConfiguration config) throws BagStorageConfigException {
        if (!(config instanceof S3BagStorageConfigImpl)) {
            throw new BagStorageConfigException("Unexpected configuration object class: " + config.getClass().toString());
        }

        S3BagStorageConfigImpl s3Config = (S3BagStorageConfigImpl) config;
        if (s3Config.accessKey == null || s3Config.accessKey.isEmpty() ||
            s3Config.secretKey == null || s3Config.secretKey.isEmpty() ||
            s3Config.bucket == null || s3Config.bucket.isEmpty()) {
            throw new BagStorageConfigException("Missing configuration options.");
        }

        myConfig = s3Config;
    }

    /**
     * Collapses multiple slashes in a path down to a single slash and removes a prepended slash.
     * S3 does not have a concept of folders; its structure is purely flat and every file is identified by
     * a unique key.  We will mimic a directory-like structure by having each file's key be a slash-separated
     * list of values, but we need to normalize them to remove unnecessary slashes.
     * For example:
     * Input: /path//to/file
     * Output: path/to/file
     * @param path The path to normalize.
     * @return A normalized path.
     */
    public String normalizePath(String path) {
        return CharMatcher.is('/').collapseFrom(path, '/').trim().replaceFirst("^/", "");
    }

    @Override
    public void removeChangeListener(BagStorageChangeListener listener) {
        myChangeListeners.remove(listener);
    }

    @Override
    public void setBagService(BagService bagService) {
        this.bagService = bagService;
    }

    @Override
    public void start() {
        myLogger.info(getStorageId() + ": Initializing S3 client.");
        AwsSessionCredentials credentials = AwsSessionCredentials.create(myConfig.accessKey, myConfig.secretKey, "");
        var builder = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials));
        if (myConfig.endPoint != null && !myConfig.endPoint.isEmpty()) {
            builder.endpointOverride(URI.create(myConfig.endPoint));
        }
        if (myConfig.region != null && !myConfig.region.isEmpty()) {
            builder.region(Region.of(myConfig.region));
        }
        else {
            // Although it doesn't matter for non-AWS services, the library expects to have a region set
            builder.region(Region.US_WEST_2);
        }
        myS3Client = builder.build();

        if (myUpdateTimer != null) {
            myUpdateTimer.cancel();
        }
        myUpdateTimer = new Timer("S3 Timer [" + myConfig.storageId + "]");
        myLogger.info(getStorageId() + ": Will check for updates every " + myConfig.updateIntervalMs + " ms.");
        myUpdateTimer.scheduleAtFixedRate(new UpdateTask(), myConfig.updateIntervalMs, myConfig.updateIntervalMs);
    }

    @Override
    @PreDestroy
    public void stop() {
        if (myUpdateTimer != null) {
            myUpdateTimer.cancel();
            myUpdateTimer = null;
        }
        myS3Client.close();
    }

    public void updateListeners() {
        myLogger.info(myConfig.storageId + ": updateListeners");
        BagStorageChangeEvent event = new BagStorageChangeEvent(this);
        for (BagStorageChangeListener listener : myChangeListeners) {
            listener.bagStorageChanged(event);
        }
    }

    @Override
    public void uploadBag(MultipartFile file, String targetDirectory) throws IOException {
        myLogger.info( myConfig.storageId + ": uploadBag");
        String absolutePath = normalizePath(targetDirectory + "/" + file.getOriginalFilename());

        myLogger.info("Uploading object with key: " + absolutePath);
        var request = PutObjectRequest.builder()
            .bucket(myConfig.bucket)
            .key(absolutePath)
            .build();
        myS3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        myLogger.info("Done uploading.");

        updateListeners();
    }

    @Override
    protected String getStatusProviderName() {
        return "S3BagStorage[" + myConfig.storageId + "]";
    }
}
