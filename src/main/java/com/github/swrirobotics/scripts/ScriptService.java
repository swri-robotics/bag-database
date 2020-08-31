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
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.swrirobotics.bags.persistence.*;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.ScriptList;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Future;

@Service
public class ScriptService extends StatusProvider {
    @Autowired
    private ScriptRepository scriptRepository;
    @Autowired
    private ScriptResultRepository resultRepository;
    @Autowired
    private BagRepository bagRepository;

    private ThreadPoolTaskExecutor taskExecutor;

    private static final String CLIENT_CERT_PATH = "/certs/client";
    private static final String SCRIPTS_PATH = "/scripts";
    private static final String SCRIPT_TMP_NAME = "/script.py";
    private static final int MINIMUM_THREAD_POOL_SIZE = 4;

    private static final Logger myLogger = LoggerFactory.getLogger(ScriptService.class);

    final private List<RunnableScript> runningScripts = Lists.newArrayList();

    class RunnableScript implements Runnable {
        final private Script script;
        final private List<Bag> bags;
        final private DockerClient client;
        final private long startTime;
        private Future<?> future;

        public RunnableScript(Script script, List<Bag> bags, DockerClient client) {
            this.script = script;
            this.bags = bags;
            this.client = client;
            startTime = System.currentTimeMillis();
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

        @Override
        public void run() {
            try {
                myLogger.info("Starting RunnableScript task for [" + script.getName() + "]");
                String containerName = null;

                ScriptResult result = new ScriptResult();
                result.setStartTime(new Timestamp(startTime));
                result.setScriptId(script.getId());
                result.setSuccess(false);

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

                saveResult(result);
                String info = "Script [" + script.getName() + "] finished.";
                myLogger.info(info);
                reportStatus(Status.State.IDLE, info);
            }
            catch (Exception e) {
                myLogger.error("Unexpected exception", e);
            }
        }

        @Transactional
        public void saveResult(ScriptResult result) {
            resultRepository.save(result);
        }
    }

    @Scheduled(fixedRate = 500)
    public void checkRunningScripts() {
        synchronized (runningScripts) {
            if (!runningScripts.isEmpty()) {
                String info = runningScripts.size() + " scripts currently running.";
                myLogger.info(info);
                reportStatus(Status.State.WORKING, info);
            }

            long now = System.currentTimeMillis();
            List<RunnableScript> finishedScripts = Lists.newArrayList();
            for (RunnableScript script : runningScripts) {
                Future<?> future = script.getFuture();
                if (future.isCancelled() || future.isDone()) {
                    finishedScripts.add(script);
                    continue;
                }

                Double timeout = script.getScript().getTimeoutSecs();
                if (timeout != null && timeout > 0.0) {
                    double elapsed = (now - script.startTime) / 1000.0;
                    if (elapsed > timeout) {
                        myLogger.warn("Cancelling run for script ["
                                + script.getScript().getName() + "] due to timeout.");
                        future.cancel(true);
                    }
                }
            }

            runningScripts.removeAll(finishedScripts);
            if (taskExecutor.getActiveCount() != runningScripts.size()) {
                myLogger.warn("Number of running scripts doesn't match active task threads!  ("
                        + runningScripts.size() + " vs . " + taskExecutor.getActiveCount() + ")");
            }
        }
    }

    @Transactional(readOnly = true)
    public ScriptList getScripts() {
        ScriptList list = new ScriptList();
        list.setScripts(scriptRepository.findAll());
        list.setTotalCount(list.getScripts().size());
        return list;
    }

    @PostConstruct
    public void initializeTaskExecutor() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(MINIMUM_THREAD_POOL_SIZE);
        taskExecutor.setMaxPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), MINIMUM_THREAD_POOL_SIZE));
        taskExecutor.initialize();
    }

    @Transactional
    public Long addScript(Script script) {
        script.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        script.setUpdatedOn(script.getCreatedOn());
        scriptRepository.save(script);
        return script.getId();
    }

    @Transactional(readOnly = true)
    public Script getScript(Long scriptId) {
        return scriptRepository.findById(scriptId).orElse(null);
    }

    @Transactional
    public void updateScript(Script script) {
        script.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
        scriptRepository.save(script);
    }

    @Transactional
    public void removeScript(Long scriptId) {
        scriptRepository.deleteById(scriptId);
    }

    @Transactional
    public void runScript(Long scriptId, List<Long> bagIds) throws ScriptRunException {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(System.getenv("DOCKER_HOST"))
                .withDockerCertPath(CLIENT_CERT_PATH)
                .withDockerTlsVerify(true)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        Script script = scriptRepository.findById(scriptId).orElseThrow(
                () -> new ScriptRunException("Script " + scriptId + " doesn't exist"));

        List<Bag> bags = bagRepository.findAllById(bagIds);
        if (bags.isEmpty()) {
            throw new ScriptRunException("No bag files found.");
        }

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);
        client.pingCmd().exec();

        myLogger.info("Dispatching script to executor.");
        RunnableScript runScript = new RunnableScript(script, bags, client);
        runScript.setFuture(taskExecutor.submit(runScript));
        runningScripts.add(runScript);
    }

    @Transactional(readOnly = true)
    public void getScriptResults(Long scriptResultId) {
        // TODO pjr Get results for a script run; need to think about what this will look like
    }

    public void getRunningScripts() {
        // TODO pjr Get info about currently running scripts
    }

    public void stopRunningScript() {
        // TODO pjr Interrupt a script that is currently running
    }

    @Override
    protected String getStatusProviderName() {
        return "Script Service";
    }
}
