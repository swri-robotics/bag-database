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

package com.github.swrirobotics.config;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import com.github.swrirobotics.bags.storage.BagScanner;
import com.github.swrirobotics.support.web.Configuration;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

@Service
public class ConfigService {
    private final ApplicationContext myAC;
    private final Environment myEnvironment;

    private BagScanner myBagScanner = null;

    @Value(ApplicationConfig.SETTINGS_LOCATION)
    private String filename;

    private final Logger myLogger = LoggerFactory.getLogger(ConfigService.class);

    public ConfigService(ApplicationContext myAC, Environment myEnvironment) {
        this.myAC = myAC;
        this.myEnvironment = myEnvironment;
    }

    public Configuration getConfiguration() {
        Configuration config = new Configuration();

        Set<String> profileSet = Sets.newHashSet(myEnvironment.getActiveProfiles());
        if (profileSet.contains("test")) {
            myLogger.warn("Running in testing mode; not loading configuration file.");
            // Don't actually read in the config file if we're in test mode.
            return config;
        }

        URL fileUrl;
        try {
            fileUrl = new URL(filename);
        }
        catch (MalformedURLException e) {
            myLogger.error("Unable to parse config file URL", e);
            return config;
            // This shouldn't happen...
        }
        FileSystemResource settingsFile = new FileSystemResource(fileUrl.getFile());

        if (!settingsFile.getFile().exists()) {
            myLogger.info("Config file does not exist; using default values.");
            return config;
        }

        YamlReader reader = null;
        try {
            reader = new YamlReader(new FileReader(settingsFile.getFile()));

            config = reader.read(Configuration.class);
        }
        catch (FileNotFoundException e) {
            // This shouldn't happen, but...
            myLogger.error("Unable to open config file:", e);
        }
        catch (YamlException e) {
            myLogger.error("Error parsing YAML:", e);
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

        return config;
    }

    @Secured("ROLE_ADMIN")
    public void setConfiguration(Configuration config) throws IOException {
        URL fileUrl;
        try {
            fileUrl = new URL(filename);
        } catch (MalformedURLException e) {
            myLogger.warn("Unable to access config file:", e);
            throw new IOException(e);
            // This shouldn't happen...
        }

        FileSystemResource settingsFile = new FileSystemResource(fileUrl.getFile());
        myLogger.info("Creating directories leading to " + fileUrl.getFile());
        myLogger.info("Parent: " + settingsFile.getFile().getParentFile().toString());
        File parentDir = settingsFile.getFile().getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Unable to create directory for config file.");
        }

        Configuration oldConfig = getConfiguration();

        YamlWriter writer = null;
        boolean bagPathChanged;
        try {
            // If the config is being set from the web interface, the user can
            // leave the JDBC password blank to indicate that it should not be
            // changed.
            writer = new YamlWriter(new FileWriter(settingsFile.getFile()));
            if (config.getJdbcPassword() == null || config.getJdbcPassword().isEmpty()) {
                config.setJdbcPassword(oldConfig.getJdbcPassword());
            }
            bagPathChanged = !oldConfig.getBagPath().equals(config.getBagPath());
            writer.write(config);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (YamlException e) {
                    myLogger.error("Unable to write config file:", e);
                }
            }
        }

        if (bagPathChanged) {
            myLogger.warn("Setting the bagPath config parameter is deprecated.");
            myLogger.warn("Please configure a storage backend instead.");
            // If the bag path has changed, we should tell the scanner to rescan.
            if (myBagScanner == null) {
                // We can't autowire this because it would create a circular
                // dependency.
                myBagScanner = myAC.getBean(BagScanner.class);
            }
            myBagScanner.reset();
        }
    }
}
