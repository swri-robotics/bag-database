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
import com.github.swrirobotics.bags.NonexistentBagException;
import com.github.swrirobotics.bags.storage.*;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.BagRepository;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.BagTreeNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class S3BagStorageImpl extends StatusProvider implements BagStorage {
    public static final String type = "s3";

    private static final Logger myLogger = LoggerFactory.getLogger(S3BagStorageImpl.class);

    private final BagRepository bagRepository;
    private final ConfigService configService;
    private BagService bagService;

    private final Set<BagStorageChangeListener> myChangeListeners = Sets.newHashSet();
    private S3BagStorageConfigImpl myConfig = null;

    private S3Client myS3Client = null;

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
        String noramlizedPath = normalizePath(path);
        var request = HeadObjectRequest.builder().bucket(myConfig.bucket).key(noramlizedPath).build();
        try {
            myS3Client.headObject(request);
            return true;
        }
        catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void updateBagExistence() {
        myLogger.info("Storage[" + myConfig.storageId + "]: updateBagExistence");

    }

    @Override
    public void updateBags(boolean forceUpdate) {
        myLogger.info("Storage[" + myConfig.storageId + "]: updateBags");
        final Stream<Bag> missingBags = bagRepository.findByStorageIdAndMissing(myConfig.storageId, true);
        final Stream<Bag> existingBags = bagRepository.findByStorageIdAndMissing(myConfig.storageId, false);

        final Map<String, Long> missingBagMd5sums = missingBags.collect(Collectors.toMap(Bag::getMd5sum, Bag::getId));
        final Map<String, Long> existingBagPaths = existingBags
            .collect(Collectors.toMap(bag -> normalizePath(bag.getPath() + bag.getFilename()), Bag::getId));

        var request = ListObjectsV2Request.builder().bucket(myConfig.bucket).build();
        var response = myS3Client.listObjectsV2(request);
        for (var object : response.contents()) {
            String filename = object.key();
            if (!filename.endsWith(".bag")) {
                // Object isn't a bag
                continue;
            }

            if (existingBagPaths.get(filename) != null && !forceUpdate) {
                // Bag already exists in DB
                continue;
            }

            myLogger.info("Processing bag file: " + filename);

            try (S3BagWrapperImpl wrapper = new S3BagWrapperImpl(myS3Client, filename, this)) {
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

        if (configService.getConfiguration().getRemoveOnDeletion()) {
            bagService.removeMissingBags();
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
        myLogger.info("getTreeNodes: " + targetPath);
        List<BagTreeNode> nodes = new ArrayList<>();

        var builder = ListObjectsV2Request.builder().bucket(myConfig.bucket);
        if (!targetPath.equals("root")) {
            builder = builder.prefix(targetPath);
        }
        var response = myS3Client.listObjectsV2(builder.build());
        for (var object : response.contents()) {
            if (object.key().contains("/")) {
                myLogger.info("Skipping " + object.key());
                // Skip keys that are in subdirectories
                continue;
            }

            if (object.size() == 0) {
                myLogger.info("Adding directory: " + object.key());
                BagTreeNode childNode = new BagTreeNode();
                var parts = Splitter.on('/').trimResults().omitEmptyStrings().splitToList(object.key());
                childNode.filename = parts.get(parts.size()-1);
                childNode.parentId = targetPath.equals("root") ? "" : targetPath;
                childNode.leaf = false;
                childNode.storageId = getStorageId();
                childNode.id = object.key();
                java.nio.file.Path subdirPath = FileSystems.getDefault().getPath(childNode.id);
                try (DirectoryStream<Path> subdirStream = Files.newDirectoryStream(subdirPath)) {
                    Iterator<Path> iter = subdirStream.iterator();
                    childNode.expanded = !iter.hasNext();
                }
                childNode.bagCount = bagRepository.countByPathStartsWithAndStorageId(childNode.id, getStorageId());
                nodes.add(childNode);
            }
            else {
                myLogger.info("Adding " + object.key());
            }
        }

        // TODO Populate tree nodes
        return nodes;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    @Transactional
    public BagWrapper getBagWrapper(long bagId) throws NonexistentBagException {
        Bag bag = bagRepository.findById(bagId).orElseThrow(() ->
            new NonexistentBagException("Could not find bag: " + bagId));
        return getBagWrapper(bag);
    }

    @Override
    @Transactional
    public BagWrapper getBagWrapper(Bag bag) {
        String key = normalizePath(bag.getPath() + bag.getFilename());
        return new S3BagWrapperImpl(myS3Client, key, this);
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
        return CharMatcher.is('/').collapseFrom(path, '/').replaceFirst("^/", "");
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

        // TODO Figure out how to register for change notifications
    }

    @Override
    public void stop() {
        myS3Client.close();
    }

    public void updateListeners() {
        myLogger.info("Storage[" + myConfig.storageId + "]: updateListeners");
        BagStorageChangeEvent event = new BagStorageChangeEvent(this);
        for (BagStorageChangeListener listener : myChangeListeners) {
            listener.bagStorageChanged(event);
        }
    }

    @Override
    public void uploadBag(MultipartFile file, String targetDirectory) throws IOException {
        myLogger.info("Storage[" + myConfig.storageId + "]: uploadBag");
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
