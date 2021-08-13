// *****************************************************************************
//
// Copyright (c) 2020, Southwest Research Institute® (SwRI®)
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

package com.github.swrirobotics.scripts;

import com.amihaiemil.docker.Container;
import com.amihaiemil.docker.Docker;
import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.storage.BagStorage;
import com.github.swrirobotics.bags.storage.BagStorageConfiguration;
import com.github.swrirobotics.bags.storage.BagWrapper;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.*;
import com.github.swrirobotics.status.Status;
import com.google.common.base.Joiner;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.json.*;
import java.io.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunnableScript implements Runnable {
    private final BagRepository bagRepository;
    private final BagService bagService;
    private final ConfigService configService;
    private final ScriptResultRepository resultRepository;
    private final ScriptService scriptService;

    final private UUID runUuid;
    private Script script;
    private List<Bag> bags;
    private Docker docker;
    private long startTime;
    private Future<?> future;
    private Status endStatus;

    private final Logger myLogger = LoggerFactory.getLogger(RunnableScript.class);
    private static final String SCRIPTS_DIR = "/scripts/"; // Where scripts are mounted in the DinD container
    private static final String SCRIPT_TMP_NAME = "/script.py"; // The name of the script inside the script container

    public RunnableScript(BagRepository bagRepository, BagService bagService, ConfigService configService,
                          ScriptResultRepository resultRepository, ScriptService scriptService) {
        this.runUuid = UUID.randomUUID();
        this.bagRepository = bagRepository;
        this.bagService = bagService;
        this.configService = configService;
        this.resultRepository = resultRepository;
        this.scriptService = scriptService;
    }

    public void initialize(Script script, List<Bag> bags, Docker docker) {
        this.script = script;
        this.bags = bags;
        this.docker = docker;
        startTime = System.currentTimeMillis();
    }

    public Status getEndStatus() {
        return endStatus;
    }

    public Future<?> getFuture() {
        return future;
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public UUID getRunUuid() {
        return runUuid;
    }

    public Script getScript() {
        return script;
    }

    public long getStartTime() {
        return startTime;
    }

    @Transactional
    public void run() {
        try {
            myLogger.info("Starting RunnableScript task for [" + script.getName() + "]");
            String stdout = runScript();
            String info = "Script [" + script.getName() + "] finished.";
            myLogger.info(info);
            endStatus = new Status(Status.State.IDLE, info);

            List<Long> bagIds = bags.stream().map(Bag::getId).collect(Collectors.toList());
            if (stdout != null) {
                scriptService.processScriptOutput(stdout, bagIds);
            }
        }
        catch (Exception e) {
            myLogger.error("Unexpected exception", e);
            endStatus = new Status(Status.State.ERROR,
                    "Error when processing script: " + e.getLocalizedMessage());
        }
    }

    /**
     * Runs this thread's configured script.
     */
    @Transactional
    public String runScript() {
        String containerName = null;
        String stdout = null;

        ScriptResult result = new ScriptResult();
        result.setStartTime(new Timestamp(startTime));
        result.setScriptId(script.getId());
        result.setSuccess(false);
        result.setRunUuid(runUuid);

        List<Long> bagIds = Lists.newArrayList();
        for (Bag bag : bags) {
            bagIds.add(bag.getId());
        }

        Container container = null;

        File scriptFile = null;
        // A list of bag wrappers created for processing bags.  These may contain open filesystem or network
        // resources, so they must be closed afterward.
        List<BagWrapper> bagWrappers = new ArrayList<>();
        try {
            // Write out script out to a temporary file
            File scriptDir = new File(configService.getConfiguration().getScriptTmpPath());
            myLogger.debug("Writing script to temporary directory: " + scriptDir.getAbsolutePath());
            if (!scriptDir.exists()) {
                myLogger.debug("Script dir doesn't exist; creating it.");
                if (!scriptDir.mkdirs()) {
                    myLogger.error("Failed to create script directory.");
                }
            }
            else if (!scriptDir.canWrite()) {
                myLogger.error("Script dir exists but is not writable.");
            }
            else if (!scriptDir.isDirectory()) {
                myLogger.error("Script dir is not actually a directory.");
            }
            scriptFile = File.createTempFile("bagdb", ".py", scriptDir);
            try (FileWriter writer = new FileWriter(scriptFile)) {
                writer.write(script.getScript());
            }
            if (!scriptFile.setExecutable(true)) {
                myLogger.warn("Unable to mark script as executable.  Will try to run it anyway.");
            }

            // Assemble bind configurations for our script and all of the bags it uses
            JsonArrayBuilder bindBuilder = Json.createArrayBuilder();
            bindBuilder.add(Joiner.on(':').join(SCRIPTS_DIR + scriptFile.getName(), SCRIPT_TMP_NAME));
            List<String> command = new ArrayList<>();
            command.add(SCRIPT_TMP_NAME);
            for (Bag bag : bags) {
                BagWrapper wrapper = bagService.getBagWrapper(bag);
                bagWrappers.add(wrapper);
                BagStorageConfiguration config = wrapper.getBagStorage().getConfig();
                String relativeBagPath;
                if (config.isLocal) {
                    // For local bag files, the directory they're in should be mounted as a volume inside the
                    // docker-in-docker container.  We need to figure out the path inside that container
                    // and use that as the source for the mount point inside the script container.
                    BagStorage storage = wrapper.getBagStorage();
                    String baseBagPath = storage.getRootPath();
                    String dindBagPath = config.dockerPath;
                    String absolutePath = wrapper.getBagFile().getPath().toAbsolutePath().toString();
                    relativeBagPath = absolutePath.replaceFirst(baseBagPath, dindBagPath);
                    bindBuilder.add(Joiner.on(':').join(relativeBagPath, relativeBagPath, ""));
                    command.add("/" + relativeBagPath);
                }
                else {
                    // Bag files downloaded from remote storage backends to local storage should be in the same
                    // directory that we use for writing script files
                    String fileName = "/" + wrapper.getBagFile().getPath().toFile().getName();
                    bindBuilder.add(Joiner.on(':').join(SCRIPTS_DIR + fileName, fileName, ""));
                    command.add(fileName);
                }
            }

            // Pull the Docker image to make sure it's ready
            // Docker images on the official Docker hub might look like this:
            // ros
            // ros:melodic
            // osrf/ros:melodic-desktop
            // Ones in custom registries might look like:
            // localserver:5000/ros:melodic
            // The Docker API call to pull an image expects everything before the last :
            // as one argument and everything after the : as another, so we need to split
            // it on the last colon.  Additionally, if there is no colon, assume the tag
            // "latest".
            var tagSepIndex = script.getDockerImage().lastIndexOf(":");
            String label;
            String tag;
            if (tagSepIndex == -1) {
                label = script.getDockerImage();
                tag = "latest";
            }
            else {
                label = script.getDockerImage().substring(0, tagSepIndex);
                try {
                    tag = script.getDockerImage().substring(tagSepIndex + 1);
                }
                catch (IndexOutOfBoundsException e) {
                    myLogger.warn("Invalid Docker tag format.  Will try 'latest'.");
                    tag = "latest";
                }
            }
            myLogger.info("Pulling Docker image: [" + label + "] with tag: [" + tag + "]");
            docker.images().pull(label, tag);

            JsonObjectBuilder hostConfig =  Json.createObjectBuilder().add("Binds", bindBuilder);
            if (script.getMemoryLimitBytes() != null && script.getMemoryLimitBytes() > 0) {
                hostConfig = hostConfig.add("Memory", script.getMemoryLimitBytes());
            }

            // Assemble the final configuration
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder = builder
                .add("NetworkDisable", !script.getAllowNetworkAccess())
                .add("Image", script.getDockerImage())
                .add("HostConfig", hostConfig)
                .add("Cmd", Json.createArrayBuilder(command));
            if (script.getTimeoutSecs() != null && script.getTimeoutSecs().longValue() > 0) {
                builder = builder.add("StopTimeout", script.getTimeoutSecs().longValue());
            }
            JsonObject config = builder.build();
            StringWriter configWriter = new StringWriter();
            Json.createWriter(configWriter).writeObject(config);
            myLogger.debug("Container config:\n" + configWriter.getBuffer().toString());
            container = docker.containers().create(config);
            containerName = container.containerId();

            myLogger.debug("Created container: " + containerName);

            container.start();

            myLogger.debug("Started container: " + containerName);
            // Wait until the container stops, then collect its output
            container.waitOn("not-running");

            try {
                JsonObject status = container.inspect();
                if (status !=  null) {
                    int exitCode = status.getJsonObject("State").getInt("ExitCode");
                    myLogger.debug("Exit code: " + exitCode);
                    result.setExitCode(exitCode);
                }
            }
            catch (ClassCastException e) {
                myLogger.warn("Unable to get exit code from Docker container");
            }


            stdout = container.logs().stdout().fetch();
            myLogger.debug("Output:\n" + stdout);

            /*
             * The original behavior here is that the script would be considered unsuccessful if its stderr output
             * was not empty.  Sometimes scripts can be successful and print to stderr, though, so it would be better
             * to use the script's exit code to determine success instead.
             * For the sake of preserving backwards compatibility, this will check the output for a JSON object
             * containing a boolean value named "useExitCodeForSuccess"; if that is false or doesn't exist, the old
             * behavior will be preserved, but if it is true, the contents of stderr will be ignored and the script
             * will be marked as successful if the exit code is 0.
             */
            boolean useExitCodeForSuccess = false;
            try (StringReader strReader = new StringReader(stdout)) {
                JsonReader reader = Json.createReader(strReader);
                JsonObject obj = reader.readObject();
                useExitCodeForSuccess = obj.getBoolean("useExitCodeForSuccess");
            }
            catch (JsonException | IllegalStateException | ClassCastException | NullPointerException e) {
                // If we can't read that value, just continue
            }

            String stderr = container.logs().stderr().fetch();
            if (!stderr.isEmpty()) {
                result.setStderr(stderr);
                myLogger.warn("Stderr:\n" + stderr);
            }

            if (useExitCodeForSuccess) {
                if (result.getExitCode() == 0) {
                    result.setSuccess(true);
                }
            }
            else if (stderr.isEmpty()){
                result.setSuccess(true);
            }

            result.setStdout(stdout);

        }
        catch (IOException | BagReaderException e) {
            myLogger.error("IO Exception:", e);
            result.setErrorMessage(e.getLocalizedMessage());
        }
        finally {
            if (containerName != null) {
                myLogger.debug("Removing container: " + containerName);
                try {
                    container.remove();
                }
                catch (IOException e) {
                    myLogger.error("Failed to remove container", e);
                }
            }
            if (scriptFile != null && !scriptFile.delete()) {
                myLogger.warn("Failed to delete temporary script file: " + scriptFile.getAbsolutePath());
            }
            for (BagWrapper wrapper : bagWrappers) {
                IOUtils.closeQuietly(wrapper);
            }
        }

        long stopTime = System.currentTimeMillis();
        result.setDurationSecs((stopTime - startTime) / 1000.0);

        saveResult(result, bagIds);

        return stdout;
    }

    @Transactional
    public void saveResult(ScriptResult result, List<Long> bagIds) {
        List<Bag> bags = bagRepository.findAllById(bagIds);
        for (Bag bag : bags) {
            bag.getScriptResults().add(result);
            result.getBags().add(bag);
        }
        resultRepository.save(result);
    }
}
