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
import com.github.swrirobotics.support.web.BagTreeNode;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class S3BagStorageImpl implements BagStorage {
    public static final String type = "s3";

    private static final Logger myLogger = LoggerFactory.getLogger(S3BagStorageImpl.class);

    private final ApplicationContext applicationContext;
    private final BagRepository bagRepository;
    private final ConfigService configService;
    private BagService bagService;

    private final Set<BagStorageChangeListener> myChangeListeners = Sets.newHashSet();
    private S3BagStorageConfigImpl myConfig = null;

    private S3Client myS3Client = null;

    public S3BagStorageImpl(ApplicationContext applicationContext, BagRepository bagRepository, ConfigService configService) {
        this.applicationContext = applicationContext;
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

            if (existingBagPaths.get(object.key()) != null) {
                // Bag already exists in DB
                continue;
            }

            myLogger.info("Processing bag file: " + object.key());
            // TODO Download bag into temporary file, parse it, add it to database, remove temporary file
        }
    }

    @Override
    public BagStorageConfiguration getConfig() {
        return myConfig;
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
        // TODO Populate tree nodes
        return new ArrayList<>();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public BagWrapper getBagWrapper(long bagId) {
        // TODO Make an S3BagWrapper
        return null;
    }

    @Override
    public BagWrapper getBagWrapper(Bag bag) {
        return null;
    }

    @Override
    public void loadConfig(BagStorageConfiguration config) throws BagStorageConfigException {
        if (!(config instanceof S3BagStorageConfigImpl)) {
            throw new BagStorageConfigException("Unexpected configuration object class: " + config.getClass().toString());
        }

        if (myConfig.accessKey == null || myConfig.accessKey.isEmpty() ||
            myConfig.secretKey == null || myConfig.secretKey.isEmpty() ||
            myConfig.bucket == null || myConfig.bucket.isEmpty()) {
            throw new BagStorageConfigException("Missing configuration options.");
        }

        myConfig = (S3BagStorageConfigImpl) config;
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
    }
}
