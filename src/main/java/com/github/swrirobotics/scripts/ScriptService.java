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

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.swrirobotics.bags.persistence.Script;
import com.github.swrirobotics.bags.persistence.ScriptRepository;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.ScriptList;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.List;

@Service
public class ScriptService extends StatusProvider {
    @Autowired
    private ScriptRepository scriptRepository;

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
    public void runScript(Long scriptId, List<Long> bagIds) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("localhost")
                .withDockerTlsVerify(false)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        DockerHttpClient.Request req = DockerHttpClient.Request.builder()
                .method(DockerHttpClient.Request.Method.GET)
                .path("/_ping").build();
        try (DockerHttpClient.Response response = httpClient.execute(req)) {
            myLogger.info(IOUtils.toString(response.getBody(), Charset.defaultCharset()));
        }
        catch (IOException e) {
            myLogger.warn(e.getLocalizedMessage());
        }
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
