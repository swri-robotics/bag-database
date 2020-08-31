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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Volume;
import com.github.swrirobotics.bags.persistence.*;
import com.github.swrirobotics.status.Status;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Future;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunnableScript implements Runnable {
    @Autowired
    private BagRepository bagRepository;
    @Autowired
    private ScriptResultRepository resultRepository;
    @Autowired
    private ScriptService scriptService;

    private Script script;
    private List<Bag> bags;
    private DockerClient client;
    private long startTime;
    private Future<?> future;
    private Status endStatus;

    private final Logger myLogger = LoggerFactory.getLogger(RunnableScript.class);
    private static final String SCRIPTS_PATH = "/scripts";
    private static final String SCRIPT_TMP_NAME = "/script.py";

    public RunnableScript() {
    }

    public void initialize(Script script, List<Bag> bags, DockerClient client) {
        this.script = script;
        this.bags = bags;
        this.client = client;
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

    public Script getScript() {
        return script;
    }

    public long getStartTime() {
        return startTime;
    }

    @Override
    @Transactional
    public void run() {
        try {
            myLogger.info("Starting RunnableScript task for [" + script.getName() + "]");
            String containerName = null;

            ScriptResult result = new ScriptResult();
            result.setStartTime(new Timestamp(startTime));
            result.setScriptId(script.getId());
            result.setSuccess(false);

            List<Long> bagIds = Lists.newArrayList();
            for (Bag bag : bags) {
                bagIds.add(bag.getId());
            }

            try {
                File scriptDir = new File(SCRIPTS_PATH);
                File scriptFile = File.createTempFile("bagdb", "py", scriptDir);
                scriptFile.setExecutable(true);
                FileWriter writer = new FileWriter(scriptFile);
                writer.write(script.getScript());
                writer.close();

                List<Bind> volumes = Lists.newArrayList();
                volumes.add(new Bind(scriptFile.getAbsolutePath(), new Volume(SCRIPT_TMP_NAME)));
                for (Bag bag : bags) {
                    volumes.add(new Bind(bag.getPath() + "/" + bag.getFilename(),
                            new Volume("/" + bag.getFilename()),
                            AccessMode.ro));
                }
                List<String> command = Lists.newArrayList();
                for (Bind vol : volumes) {
                    command.add(vol.getVolume().toString());
                }

                myLogger.info("Pulling Docker image: " + script.getDockerImage());
                client.pullImageCmd(script.getDockerImage()).start().awaitCompletion();

                var createCmd = client.createContainerCmd(script.getDockerImage())
                        .withNetworkDisabled(!script.getAllowNetworkAccess())
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .withBinds(volumes)
                        .withCmd(command);
                if (script.getMemoryLimitBytes() != null && script.getMemoryLimitBytes() > 0) {
                    createCmd.withMemory(script.getMemoryLimitBytes());
                }
                var createResponse = createCmd.exec();
                containerName = createResponse.getId();
                myLogger.info("Created container: " + containerName);

                var logContainerCmd = client.logContainerCmd(containerName)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .withTailAll();

                myLogger.info("Started container");
                StringBuffer stdoutBuffer = new StringBuffer();
                StringBuffer stderrBuffer = new StringBuffer();

                var logCallback = new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame item) {
                        switch (item.getStreamType()) {
                            case STDOUT:
                                stdoutBuffer.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                                break;
                            case STDERR:
                                stderrBuffer.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                                break;
                            case STDIN:
                            case RAW:
                                break;
                        }
                    }
                };

                // We can't run the log command until after the start command, but we want it to happen
                // as soon as possible afterward to ensure that we don't miss any of the output.
                client.startContainerCmd(containerName).exec();
                logContainerCmd.exec(logCallback).awaitCompletion();

                String stdout = stdoutBuffer.toString().trim();
                myLogger.debug("Output:\n" + stdout);
                String stderr = stderrBuffer.toString().trim();
                if (!stderr.isEmpty()) {
                    result.setStderr(stderr);
                    myLogger.warn("Stderr:\n" + stderr);
                }
                else {
                    result.setSuccess(true);
                }

                result.setStdout(stdout);
            }
            catch (InterruptedException e) {
                myLogger.warn("Script was interrupted.");
                result.setErrorMessage("Script was interrupted.");
            }
            catch (IOException e) {
                myLogger.info("IO Exception: " + e.getLocalizedMessage());
                result.setErrorMessage(e.getLocalizedMessage());
            }
            finally {
                if (containerName != null) {
                    myLogger.info("Removing container: " + containerName);
                    client.removeContainerCmd(containerName).withForce(true).exec();
                }
            }

            long stopTime = System.currentTimeMillis();
            result.setDurationSecs((stopTime - startTime) / 1000.0);

            saveResult(result, bagIds);
            String info = "Script [" + script.getName() + "] finished.";
            myLogger.info(info);
            endStatus = new Status(Status.State.IDLE, info);
        }
        catch (Exception e) {
            myLogger.error("Unexpected exception", e);
            endStatus = new Status(Status.State.ERROR,
                    "Error when processing script: " + e.getLocalizedMessage());
        }
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
