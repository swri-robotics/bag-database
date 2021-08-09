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

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.github.swrirobotics.bags.filesystem.watcher.RecursiveWatcher;
import com.github.swrirobotics.bags.storage.*;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilesystemBagStorageImpl extends StatusProvider implements BagStorage, RecursiveWatcher.WatchListener {
    @Override
    protected String getStatusProviderName() {
        return "FilesystemBagStorage";
    }

    @Override
    public void watchEventsOccurred() {
        BagStorageChangeEvent event = new BagStorageChangeEvent(this);
        for (BagStorageChangeListener listener : myChangeListeners) {
            listener.bagStorageChanged(event);
        }
    }

    private FilesystemBagStorageConfigImpl myConfig = null;

    private static final Logger myLogger = LoggerFactory.getLogger(FilesystemBagWrapperImpl.class);

    public static final String type = "filesystem";

    private final DirectoryStream.Filter<Path> myDirFilter = path -> path.toFile().isDirectory();

    private RecursiveWatcher myWatcher = null;

    private final Set<BagStorageChangeListener> myChangeListeners = Sets.newHashSet();

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
    public String getName() {
        return myConfig.name;
    }

    @Override
    public String getStorageId() {
        return myConfig.storageId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public BagWrapper getBagWrapper(String path) {
        return new FilesystemBagWrapperImpl(path, myConfig);
    }

    @Override
    public List<BagWrapper> listBags() {
        return getBagFiles(FileSystems.getDefault().getPath(myConfig.basePath)).stream().map(path ->
            new FilesystemBagWrapperImpl(path.getPath(), myConfig)).collect(Collectors.toList());
    }

    @Override
    public void loadConfig(BagStorageConfiguration config) throws BagStorageConfigException {
        if (!(config instanceof FilesystemBagStorageConfigImpl)) {
            throw new BagStorageConfigException("Unexpected configuration object class: " + config.getClass().toString());
        }

        myConfig = (FilesystemBagStorageConfigImpl) config;
    }


    @Override
    public void loadConfig(String config) throws BagStorageConfigException {
        YamlReader reader = null;
        try {
            reader = new YamlReader(new StringReader(config));

            myConfig = reader.read(FilesystemBagStorageConfigImpl.class);
        }
        catch (YamlException e) {
            myLogger.error("Error loading FilesystemBagWrapperConfig:", e);
            throw new BagStorageConfigException(e);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    // No need to do anything
                }
            }
        }
    }

    @Override
    public void removeChangeListener(BagStorageChangeListener listener) {
        myChangeListeners.remove(listener);
    }

    @Override
    public void start() {
        if (myConfig != null && myConfig.basePath != null && !myConfig.basePath.isEmpty()) {
            Path path = FileSystems.getDefault().getPath(myConfig.basePath);
            myWatcher = RecursiveWatcher.createRecursiveWatcher(
                path, new ArrayList<>(), 3000, this);
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

    private boolean presentSpecialCharacters(Path dir){
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
            } catch (IOException e) {
                myLogger.error("Error parsing directory:", e);
                reportStatus(Status.State.ERROR, "Unable to read directory: " + dir.toString());
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, myDirFilter)) {
                for (Path subdir : stream) {
                    myLogger.trace("  Checking subdir: " + subdir.toString());
                    bagFiles.addAll(getBagFiles(subdir));
                }
            } catch (IOException e) {
                myLogger.error("Error parsing subdirectory:", e);
            }
        }
        return bagFiles;
    }

}
