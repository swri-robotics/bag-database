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

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * This is a wrapper that provides a layer of abstraction around performing operations on single bags.
 * It is used so that different storage backends can do whatever operations are necessary for them to read
 * bags from their storage and then clean up their resources when finished.
 * Right now, BagWrapper#getBagFile() is used as a convenient way to read data from the underlying bag file,
 * but in the future it may make sense to expand this interface's methods so that accessing the BagFile isn't
 * necessary; that may also be a reasonable way to handle integrating ROS2 bags.
 */
public interface BagWrapper extends Closeable {
    /**
     * Gets a BagFile object that can be used to read data from this bag.  For remotely-stored bags, this
     * may involve copying the bag file to a local filesystem.  If so, that file will be cached until the
     * BagWrapper is closed.
     * @return A BagFile object representing this bag.
     * @throws BagReaderException If the bag can't be read.
     */
    BagFile getBagFile() throws BagReaderException;

    /**
     * Returns the BagStorage backend used to store this bag.
     * @return The BagStorage backend used to store this bag.
     */
    BagStorage getBagStorage();

    /**
     * Returns the absolute path to the directory where this file is stored.  This does not include the bag's
     * filename.
     * For non-filesystem storage backends, this may not be the actual path on disk
     * to the file.
     * @return The absolute path to the directory where this file is stored.
     */
    String getPath();

    /**
     * Returns the filename of the bag.
     * @return The filename of the bag.
     */
    String getFilename();

    /**
     * The size of the bag in bytes.
     * @return The size of the bag in bytes.
     */
    Long getSize() throws IOException;

    /**
     * Gets an input stream for reading the bag file.  This may not necessarily use the file cached
     * by a call to BagWrapper#getBagFile().
     * @return A Spring Resource for reading the bag file.
     * @throws FileNotFoundException If the bag does not exist.
     */
    Resource getResource() throws FileNotFoundException;
}
