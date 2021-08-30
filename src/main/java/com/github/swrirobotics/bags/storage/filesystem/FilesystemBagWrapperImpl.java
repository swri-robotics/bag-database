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
import com.github.swrirobotics.bags.storage.BagStorage;
import com.github.swrirobotics.bags.storage.BagWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilesystemBagWrapperImpl implements BagWrapper {
    private final Logger myLogger = LoggerFactory.getLogger(FilesystemBagWrapperImpl.class);
    private final String myAbsPath;
    private final BagStorage myBagStorage;
    private final String myDirectory;
    private final String myFilename;

    public FilesystemBagWrapperImpl(String path, BagStorage storage) {
        myAbsPath = path;
        myBagStorage = storage;
        Pattern pathPattern = Pattern.compile("^(.*/)?(.*)$");
        Matcher keyMatcher = pathPattern.matcher(myAbsPath);
        if (!keyMatcher.find()) {
            myLogger.error("Unable to parse S3 bag key.");
        }
        myDirectory = keyMatcher.group(1);
        myFilename = keyMatcher.group(2);
    }

    @Override
    public BagFile getBagFile() throws BagReaderException {
        return BagReader.readFile(myAbsPath);
    }

    @Override
    public BagStorage getBagStorage() {
        return myBagStorage;
    }

    @Override
    public String getPath() {
        return myDirectory;
    }

    @Override
    public String getFilename() {
        return myFilename;
    }

    @Override
    public Long getSize() throws IOException {
        return Files.size(FileSystems.getDefault().getPath(myAbsPath));
    }

    @Override
    public Resource getResource() throws FileNotFoundException {
        return new FileSystemResource(myAbsPath);
    }

    @Override
    public void close() throws IOException {
        // Don't need to do anything for local files
    }
}
