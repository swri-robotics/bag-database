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

import com.github.swrirobotics.bags.persistence.Script;
import com.github.swrirobotics.bags.persistence.ScriptResult;
import com.github.swrirobotics.config.WebAppConfigurationAware;
import com.github.swrirobotics.support.web.ScriptList;
import com.github.swrirobotics.support.web.ScriptResultList;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ScriptControllerTest extends WebAppConfigurationAware {
    @MockBean
    ScriptService scriptService;

    public Script makeScript() {
        Script script = new Script();
        script.setId(0L);
        script.setName("Test Script");
        script.setAllowNetworkAccess(false);
        script.setDescription("Optional Description");
        script.setMemoryLimitBytes(4000000000L);
        script.setDockerImage("ros:melodic");
        script.setRunAutomatically(false);
        script.setTimeoutSecs(300.0);
        script.setScript("#!/usr/bin/env python\nimport time\ntime.sleep(5)\nprint(\"Hello, world!\");\n");
        return script;
    }

    public ScriptResult makeResult() {
        UUID uuid = UUID.randomUUID();
        ScriptResult result = new ScriptResult();
        result.setRunUuid(uuid);
        result.setSuccess(true);
        result.setId(1L);
        result.setScriptId(1L);
        result.setStdout("{example_stat: 1, other_example: 2}");
        result.setStartTime(new Timestamp(System.currentTimeMillis()));
        result.setDurationSecs(5.0);
        return result;
    }

    @Test
    public void getScriptResult() throws Exception {
        ScriptResult result = makeResult();
        UUID uuid = result.getRunUuid();
        when(scriptService.getScriptResultByUuid(uuid)).thenReturn(result);

        mockMvc.perform(get("/scripts/get_result_by_uuid")
                    .param("runUuid", uuid.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.runUuid").value(uuid.toString()));
    }

    @Test
    public void listScripts() throws Exception {
        ScriptList list = new ScriptList();
        list.getScripts().add(new Script());
        list.getScripts().get(0).setName("Test Script");
        list.setTotalCount(1);
        when(scriptService.getScripts()).thenReturn(list);
        mockMvc.perform(get("/scripts/list"))
                .andExpect(status().isOk());
    }

    @Test
    public void runScript() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(scriptService.runScript(1L, new ArrayList<>(){
            {add(1L); add(2L); add(3L);}
        })).thenReturn(uuid);

        mockMvc.perform(post("/scripts/run")
                .param("scriptId", "1")
                .param("bagIds", "1,2,3")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value(uuid.toString()));
    }

    @Test
    public void saveScript() throws Exception {
        Script script = makeScript();
        when(scriptService.addScript(script)).thenReturn(1L);

        mockMvc.perform(post("/scripts/save")
                .param("id", script.getId().toString())
                .param("name", script.getName())
                .param("allowNetworkAccess", script.getAllowNetworkAccess().toString())
                .param("description", script.getDescription())
                .param("memoryLimitBytes", script.getMemoryLimitBytes().toString())
                .param("dockerImage", script.getDockerImage())
                .param("runAutomatically", script.getRunAutomatically().toString())
                .param("timeoutSecs", script.getTimeoutSecs().toString())
                .param("script", script.getScript()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.success").value("true"));
    }

    @Test
    public void listScriptResults() throws Exception {
        ScriptResultList results = new ScriptResultList();
        results.setTotalCount(1);
        results.getResults().add(makeResult());

        when(scriptService.getScriptResults()).thenReturn(results);
        mockMvc.perform(get("/scripts/list_results"))
                .andExpect(status().isOk());
    }
}
