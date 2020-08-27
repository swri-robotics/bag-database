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
import com.github.swrirobotics.bags.persistence.Bag;
import com.github.swrirobotics.bags.persistence.BagRepository;
import com.github.swrirobotics.bags.persistence.Script;
import com.github.swrirobotics.bags.persistence.ScriptRepository;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.ScriptList;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

@Service
public class ScriptService extends StatusProvider {
    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private BagRepository bagRepository;

    private static final String CLIENT_CERT_PATH = "/certs/client";
    private static final String SCRIPTS_PATH = "/scripts";
    private static final String SCRIPT_TMP_NAME = "/script.py";

    private static final Logger myLogger = LoggerFactory.getLogger(ScriptService.class);

    @Transactional(readOnly = true)
    public ScriptList getScripts() {
        ScriptList list = new ScriptList();
        list.setScripts(scriptRepository.findAll());
        list.setTotalCount(list.getScripts().size());
        return list;
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

        String containerName = null;

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

            var logContainerCmd= client.logContainerCmd(containerName)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTailAll();

            client.startContainerCmd(containerName).exec();

            myLogger.info("Started container");
            StringBuffer stdoutBuffer = new StringBuffer();
            StringBuffer stderrBuffer = new StringBuffer();
            logContainerCmd.exec(new ResultCallback.Adapter<>(){
                @Override
                public void onNext(Frame item) {
                    switch (item.getStreamType())
                    {
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
            }).awaitCompletion();

            String stdout = stdoutBuffer.toString().trim();
            myLogger.info("Output:\n" + stdout);
            String stderr = stderrBuffer.toString().trim();
            if (!stderr.isEmpty()) {
                myLogger.info("Stderr:\n" + stderr);
            }
        }
        catch (InterruptedException | IOException e) {
            throw new ScriptRunException(e.getLocalizedMessage());
        }
        finally {
            if (containerName != null) {
                myLogger.info("Removing container: " + containerName);
                client.removeContainerCmd(containerName).exec();
            }
        }



//        try (DockerHttpClient.Response response = httpClient.execute(req)) {
//            myLogger.info(IOUtils.toString(response.getBody(), Charset.defaultCharset()));
//        }
//        catch (IOException e) {
//            myLogger.warn(e.getLocalizedMessage());
//            throw new ScriptRunException("Error running Docker: " + e.getLocalizedMessage());
//        }
        // TODO pjr Start a job to run a script on a bag file
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
