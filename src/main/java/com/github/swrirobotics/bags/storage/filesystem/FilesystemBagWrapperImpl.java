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

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.BagReader;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.storage.BagWrapper;
import com.github.swrirobotics.bags.storage.GpsPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FilesystemBagWrapperImpl implements BagWrapper {
    private static final Logger myLogger = LoggerFactory.getLogger(FilesystemBagWrapperImpl.class);

    private final String myPath;

    private final FilesystemBagStorageConfigImpl myConfig;

    public FilesystemBagWrapperImpl(String path, FilesystemBagStorageConfigImpl config) {
        myPath = path;
        myConfig = config;
    }

    @Override
    public String getType() {
        return FilesystemBagStorageImpl.type;
    }

    @Override
    public BagFile getBagFile() throws BagReaderException {
        return BagReader.readFile(myPath);
    }

    @Override
    public List<GpsPosition> getGpsMessages() {
        return null;
    }
}
