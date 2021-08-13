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

package com.github.swrirobotics.bags.storage.filesystem;

import com.esotericsoftware.yamlbeans.YamlWriter;
import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.storage.*;
import com.github.swrirobotics.bags.storage.filesystem.watcher.DefaultRecursiveWatcher;
import com.github.swrirobotics.bags.storage.filesystem.watcher.RecursiveWatcher;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.BagRepository;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.BagTreeNode;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FilesystemBagStorageImpl extends StatusProvider implements BagStorage, RecursiveWatcher.WatchListener {
    private static final Logger myLogger = LoggerFactory.getLogger(FilesystemBagStorageImpl.class);

    private final ApplicationContext applicationContext;
    private final BagRepository bagRepository;
    private final ConfigService configService;
    private BagService bagService;

    private final DirectoryStream.Filter<Path> myDirFilter = path -> path.toFile().isDirectory();
    private final Set<BagStorageChangeListener> myChangeListeners = Sets.newHashSet();
    private FilesystemBagStorageConfigImpl myConfig = null;
    private RecursiveWatcher myWatcher = null;


    public FilesystemBagStorageImpl(ApplicationContext applicationContext, BagRepository bagRepository,
                                    ConfigService configService) {
        this.applicationContext = applicationContext;
        this.bagRepository = bagRepository;
        this.configService = configService;
    }

    @Override
    public void setBagService(BagService bagService) {
        // Need to set this after creation because otherwise there will be a circular dependency between beans
        this.bagService = bagService;
    }

    @Override
    protected String getStatusProviderName() {
        return "FilesystemBagStorage[" + myConfig.storageId + "]";
    }

    @Override
    public void watchEventsOccurred() {
        myLogger.info("Storage[" + myConfig.storageId + "]: watchEventsOccurred");
        BagStorageChangeEvent event = new BagStorageChangeEvent(this);
        for (BagStorageChangeListener listener : myChangeListeners) {
            listener.bagStorageChanged(event);
        }
    }

    @Override
    public void addChangeListener(BagStorageChangeListener listener) {
        myChangeListeners.add(listener);
    }

    @Override
    public boolean bagExists(String path) {
        File testFile = new File(path);
        return testFile.exists();
    }

    @Override
    @Transactional
    public void updateBagExistence() {
        myLogger.info("Storage[" + getStorageId() + "]: updateBagExistence");
        Stream<Bag> bags = bagRepository.findByStorageId(myConfig.storageId);

        bags.forEach(bag -> {
            String fullPath = bag.getPath() + bag.getFilename();
            boolean isMissing = !bagExists(fullPath);
            myLogger.info("Is " + fullPath + " missing? " + isMissing);
            if (isMissing ^ bag.getMissing()) {
                myLogger.info("Bag at " + fullPath + " has " + (isMissing ? "gone missing." : "been found!"));
                bag.setMissing(isMissing);
                bagRepository.save(bag);
            }
        });
    }

    @Override
    @Transactional
    public void updateBags(boolean forceUpdate) {
        myLogger.info("Storage[" + myConfig.storageId + "]: updateBags");
        final Stream<Bag> missingBags = bagRepository.findByStorageIdAndMissing(myConfig.storageId, true);
        final Stream<Bag> existingBags = bagRepository.findByStorageIdAndMissing(myConfig.storageId, false);

        final Map<String, Long> missingBagMd5sums = missingBags.collect(Collectors.toMap(Bag::getMd5sum, Bag::getId));
        final Map<String, Long> existingBagPaths = existingBags
            .collect(Collectors.toMap(bag -> bag.getPath() + bag.getFilename(), Bag::getId));

        Set<File> fsBags = getBagFiles(FileSystems.getDefault().getPath(myConfig.basePath));
        fsBags.forEach(bag -> {
            if (existingBagPaths.get(bag.getPath()) != null) {
                if (forceUpdate) {
                    myLogger.debug("Bag already exists in database; update forced.");
                }
                else {
                    myLogger.trace("Bag exists in database; skipping.");
                    return;
                }
            }

            try {
                BagWrapper wrapper = new FilesystemBagWrapperImpl(bag.getAbsolutePath(), this);
                bagService.updateBagFile(wrapper, getStorageId(), missingBagMd5sums);
            }
            catch (ConstraintViolationException e) {
                // Constraint name is hard-coded in db.changelog-1.0.yaml
                if ("uk_a2r00kd2qd94dohkimsp5rdgn".equals(e.getConstraintName())) {
                    String message = "The data in " + bag.getName() + " seems to be a duplicate " +
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

        });

        if (configService.getConfiguration().getRemoveOnDeletion()) {
            bagService.removeMissingBags();
        }
    }

    @Override
    public BagStorageConfiguration getConfig() {
        return myConfig;
    }

    @Override
    public String getRootPath() {
        return myConfig.basePath;
    }

    @Override
    public String getStorageId() {
        return myConfig.storageId;
    }

    @Override
    public List<BagTreeNode> getTreeNodes(String targetPath) throws IOException {
        List<BagTreeNode> nodes = new ArrayList<>();
        String basePath = myConfig.basePath;
        String bagDir = targetPath;
        if (targetPath.equals("root")) {
            bagDir = basePath;
        }

        java.nio.file.Path path = FileSystems.getDefault().getPath(bagDir);
        String parentId = path.toFile().getCanonicalPath() + "/";

        if (!parentId.startsWith(basePath)) {
            // Don't allow somebody to list paths outside of the bag path.
            return nodes;
        }

        // First, add any child directories to the node list.
        try (DirectoryStream<java.nio.file.Path> dirStream = Files.newDirectoryStream(path)) {
            for (java.nio.file.Path child : dirStream) {
                File childFile = child.toFile();
                if (!childFile.isDirectory()) {
                    continue;
                }
                String filename = childFile.getName();
                Pattern p = Pattern.compile("@.*");
                Matcher m = p.matcher(filename);
                if(m.find()){
                    continue;
                }
                BagTreeNode childNode = new BagTreeNode();
                childNode.filename = filename;
                childNode.parentId = parentId;
                childNode.leaf = false;
                childNode.storageId = getStorageId();
                childNode.id = parentId + filename;
                java.nio.file.Path subdirPath = FileSystems.getDefault().getPath(childNode.id);
                try (DirectoryStream<java.nio.file.Path> subdirStream = Files.newDirectoryStream(subdirPath)) {
                    Iterator<Path> iter = subdirStream.iterator();
                    childNode.expanded = !iter.hasNext();
                }
                childNode.bagCount = bagRepository.countByPathStartsWithAndStorageId(childNode.id, getStorageId());
                nodes.add(childNode);
            }
        }

        // Next, get all the bags in that directory and add them.
        List<Bag> bags = bagRepository.findByPathAndStorageId(parentId, getStorageId());
        for (Bag bag : bags) {
            BagTreeNode childNode = new BagTreeNode();
            childNode.filename = bag.getFilename();
            childNode.parentId = parentId;
            childNode.storageId = getStorageId();
            childNode.leaf = true;
            childNode.id = parentId + bag.getFilename();
            childNode.bag = bag;
            nodes.add(childNode);
        }

        return nodes;
    }

    @Override
    @Transactional
    public BagWrapper getBagWrapper(Bag bag) {
        return new FilesystemBagWrapperImpl(bag.getPath() + bag.getFilename(), this);
    }

    @Override
    public void loadConfig(BagStorageConfiguration config) throws BagStorageConfigException {
        if (!(config instanceof FilesystemBagStorageConfigImpl)) {
            throw new BagStorageConfigException("Unexpected configuration object class: " + config.getClass().toString());
        }

        try {
            StringWriter stringWriter = new StringWriter();
            YamlWriter writer = new YamlWriter(stringWriter);
            writer.write(config);
            writer.close();
            stringWriter.close();
            myLogger.info("Loading configuration:\n" + stringWriter);
        }
        catch (IOException e) {
            throw new BagStorageConfigException(e);
        }

        myConfig = (FilesystemBagStorageConfigImpl) config;
    }

    @Override
    public void removeChangeListener(BagStorageChangeListener listener) {
        myChangeListeners.remove(listener);
    }

    @Override
    public void start() {
        if (myConfig != null && myConfig.basePath != null && !myConfig.basePath.isEmpty()) {
            Path path = FileSystems.getDefault().getPath(myConfig.basePath);
            myWatcher = applicationContext.getBean(DefaultRecursiveWatcher.class);
            myWatcher.setRoot(path);
            myWatcher.setSettleDelay(3000);
            myWatcher.setListener(this);

            try {
                myWatcher.start();
            }
            catch (Exception e) {
                myLogger.error("Unable to monitor bag directory for changes.", e);
            }
        }
    }

    @Override
    public void stop() {
        myWatcher.stop();
    }

    @Override
    public void uploadBag(MultipartFile file, String targetDirectory) throws IOException {
        java.nio.file.Path inputPath = Paths.get(targetDirectory).normalize();

        File path = new File(myConfig.basePath + "/" + inputPath);
        myLogger.debug("Checking path: " + path.getAbsolutePath());

        if (path.exists()) {
            if (path.isDirectory()) {
                if (!path.canWrite()) {
                    throw new IOException("Target path is not writable.");
                }
            }
            else {
                throw new IOException("Target is a file, not a directory.");
            }
        }
        else if (!path.mkdirs()) {
            throw new IOException("Failed to create target directory.  Is the destination writable?");
        }

        File targetFile = new File(path.getAbsolutePath() + "/" + file.getOriginalFilename());

        if (targetFile.exists()) {
            throw new IOException("Not overwriting existing file.");
        }

        myLogger.debug("Writing file to: " + targetFile.getAbsolutePath());
        FileUtils.copyInputStreamToFile(file.getInputStream(), targetFile);
    }

    private boolean presentSpecialCharacters(Path dir) {
        String dirName = dir.toString();
        Pattern p = Pattern.compile("@.*");
        Matcher m = p.matcher(dirName);
        return m.find();
    }

    private Set<File> getBagFiles(Path dir) {
        Set<File> bagFiles = Sets.newHashSet();
        if (!presentSpecialCharacters(dir.getFileName())) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.bag")) {
                for (Path bagFile : stream) {
                    myLogger.trace("  Adding: " + bagFile.toString());
                    bagFiles.add(bagFile.toAbsolutePath().toFile());
                }
            }
            catch (IOException e) {
                myLogger.error("Error parsing directory:", e);
                reportStatus(Status.State.ERROR, "Unable to read directory: " + dir);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, myDirFilter)) {
                for (Path subdir : stream) {
                    myLogger.trace("  Checking subdir: " + subdir.toString());
                    bagFiles.addAll(getBagFiles(subdir));
                }
            }
            catch (IOException e) {
                myLogger.error("Error parsing subdirectory:", e);
            }
        }
        return bagFiles;
    }

}
