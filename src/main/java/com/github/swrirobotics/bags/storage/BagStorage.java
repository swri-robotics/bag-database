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

package com.github.swrirobotics.bags.storage;

import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.support.web.BagTreeNode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Represents a mechanism for storing and retrieving information about bag files.
 */
public interface BagStorage {
    /**
     * Registers a listener that will be notified whenever bag files are changed.
     * @param listener The listener that will be notified.
     */
    void addChangeListener(BagStorageChangeListener listener);

    /**
     * Returns true if a bag exists at the given path within this storage system.
     * @param path The path to test.
     * @return True if the bag exists, false otherwise.
     */
    boolean bagExists(String path);

    /**
     * Checks whether all of this backend's bags exist in storage and updates their
     * "Missing" column appropriately.
     */
    void updateBagExistence();

    /**
     * Scans for new bags and adds them to the database.
     * @param forceUpdate True to re-examine existing bags and update their details in the database.
     */
    void updateBags(boolean forceUpdate);

    /**
     * Returns the configuration for this storage backend.
     * @return The configuration for this storage backend.
     */
    BagStorageConfiguration getConfig();

    /**
     * Returns the path to the root directory of this storage.  This should have a trailing slash.
     * For many storage systems this may just be "/", but for filesystem storage it could be a subdirectory.
     * @return The path to the root directory of this storage.
     */
    String getRootPath();

    /**
     * Returns a unique identifier that represents this storage backend.
     * @return This backend's unique identifier.
     */
    String getStorageId();

    /**
     * Returns a list of tree nodes representing the contents of this storage backend at the specified path.
     * This should include both leaves (bag files) and branches (directories) at the target path; note that it is
     * not recursive, and only elements directly at the specified path should be returned.
     * @param targetPath The target path, or "root", which means the base path of this storage.
     * @return All the tree nodes at that path.
     */
    List<BagTreeNode> getTreeNodes(String targetPath) throws IOException;

    /**
     * Creates a wrapper for performing operations on a bag file.
     * @param bag A bag persistence object.
     * @return A wrapper representing that bag file.
     */
    BagWrapper getBagWrapper(Bag bag);

    /**
     * Loads the backend's configuration.  This will be parsed from the bag database's YAML configuration.
     * @param config The config object.
     * @throws BagStorageConfigException If the config object is invalid.
     */
    void loadConfig(BagStorageConfiguration config) throws BagStorageConfigException;

    /**
     * Removes a previous-registered change listener.
     * @param listener The listener to remove.
     */
    void removeChangeListener(BagStorageChangeListener listener);

    /**
     * Provides the storage backend with a reference to the BagService so that it can use it to perform operations
     * on the database.  This is not passed in through the constructor in order to avoid a circular dependency.
     * @param bagService The BagService service.
     */
    void setBagService(BagService bagService);

    /**
     * Starts any background threads or connections required by this storage backend.
     */
    void start();

    /**
     * Stops any background threads or connections required by this storage backend.
     */
    void stop();

    /**
     * Uploads a new file to the storage backend.
     * @param file The file to upload.
     * @param targetDirectory The path to store the file.
     * @throws IOException If there is an error uploading the file.
     */
    void uploadBag(MultipartFile file, String targetDirectory) throws IOException;
}
