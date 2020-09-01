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
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.swrirobotics.bags.persistence.*;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.ScriptList;
import com.github.swrirobotics.support.web.ScriptResultList;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

@Service
public class ScriptService extends StatusProvider {
    @Autowired
    private ScriptRepository scriptRepository;
    @Autowired
    private ScriptResultRepository resultRepository;
    @Autowired
    private BagRepository bagRepository;
    @Autowired
    private ApplicationContext myAC;

    private ThreadPoolTaskExecutor taskExecutor;

    private static final String CLIENT_CERT_PATH = "/certs/client";
    private static final int MINIMUM_THREAD_POOL_SIZE = 4;

    private static final Logger myLogger = LoggerFactory.getLogger(ScriptService.class);

    final private List<RunnableScript> runningScripts = Lists.newArrayList();

    @Scheduled(fixedRate = 500)
    public void checkRunningScripts() {
        synchronized (runningScripts) {
            if (runningScripts.isEmpty()) {
                return;
            }

            String info = "Before check: " + runningScripts.size() + " scripts currently running.";
            myLogger.debug(info);

            long now = System.currentTimeMillis();
            List<RunnableScript> finishedScripts = Lists.newArrayList();
            for (RunnableScript script : runningScripts) {
                Future<?> future = script.getFuture();
                if (future.isCancelled() || future.isDone()) {
                    if (script.getEndStatus() != null) {
                        reportStatus(script.getEndStatus());
                    }
                    finishedScripts.add(script);
                    continue;
                }

                Double timeout = script.getScript().getTimeoutSecs();
                if (timeout != null && timeout > 0.0) {
                    double elapsed = (now - script.getStartTime()) / 1000.0;
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

            if (runningScripts.isEmpty()) {
                info = "All scripts finished.";
                myLogger.debug(info);
                reportStatus(Status.State.IDLE, info);
            }
            else {
                info = runningScripts.size() + " scripts currently running.";
                myLogger.debug(info);
                reportStatus(Status.State.WORKING, info);
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

    @Transactional(readOnly = true)
    public ScriptResultList getScriptResults() {
        ScriptResultList list = new ScriptResultList();
        list.setResults(resultRepository.findAll());
        list.setTotalCount(list.getResults().size());
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
    public UUID runScript(Long scriptId, List<Long> bagIds) throws ScriptRunException {
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
        var runScript = myAC.getBean(RunnableScript.class);
        runScript.initialize(script, bags, client);
        runScript.setFuture(taskExecutor.submit(runScript));
        runningScripts.add(runScript);

        return runScript.getRunUuid();
    }

    @Transactional(readOnly = true)
    public ScriptResult getScriptResultByUuid(UUID runUuid) {
        return resultRepository.findByRunUuid(runUuid);
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
