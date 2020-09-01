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
import com.github.swrirobotics.support.web.ScriptList;
import com.github.swrirobotics.support.web.ScriptResultList;
import com.github.swrirobotics.support.web.ScriptRunResult;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("scripts")
public class ScriptController {
    @Autowired
    private ScriptService myScriptService;

    private final Logger myLogger = LoggerFactory.getLogger(ScriptController.class);

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public void deleteScript(Long scriptId) {
        myLogger.info("deleteScript: " + scriptId);
        myScriptService.removeScript(scriptId);
    }

    @RequestMapping("/list")
    public ScriptList getScripts() {
        myLogger.debug("getScripts");
        ScriptList list = myScriptService.getScripts();
        myLogger.debug("returning scripts");
        return list;
    }

    @RequestMapping("/list_results")
    public ScriptResultList getScriptResults() {
        myLogger.debug("getScriptResults");
        return myScriptService.getScriptResults();
    }

    @RequestMapping("/get")
    @ResponseBody
    public Map<String, Object> getScript(Long scriptId) {
        myLogger.info("getScript: " + scriptId);

        Map<String, Object> response = Maps.newHashMap();
        response.put("success", true);
        response.put("data", myScriptService.getScript(scriptId));

        return response;
    }

    @RequestMapping(value = "/run", method = RequestMethod.POST)
    public ScriptRunResult runScript(@RequestParam Long scriptId,
                                     @RequestParam Long[] bagIds) {
        myLogger.info("runScript: " + scriptId);
        ScriptRunResult result = new ScriptRunResult();
        try {
            UUID runUuid = myScriptService.runScript(scriptId, Lists.newArrayList(bagIds));
            result.success = true;
            result.uuid = runUuid.toString();
        }
        catch (ScriptRunException e) {
            result.message = e.getLocalizedMessage();
        }
        return result;
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> saveScript(@RequestParam Long id,
                                          @RequestParam String name,
                                          @RequestParam Boolean allowNetworkAccess,
                                          @RequestParam Optional<String> description,
                                          @RequestParam Optional<Long> memoryLimitBytes,
                                          @RequestParam String dockerImage,
                                          @RequestParam Boolean runAutomatically,
                                          @RequestParam Optional<Double> timeoutSecs,
                                          @RequestParam("script") String scriptText) {
        myLogger.info("saveScript");

        Script script;
        if (id > 0) {
            script = myScriptService.getScript(id);
        }
        else {
            script = new Script();
        }
        script.setId(id > 0 ? id : null);
        script.setName(name);
        script.setAllowNetworkAccess(allowNetworkAccess);
        script.setDescription(description.orElse(null));
        script.setMemoryLimitBytes(memoryLimitBytes.orElse(null));
        script.setDockerImage(dockerImage);
        script.setRunAutomatically(runAutomatically);
        script.setTimeoutSecs(timeoutSecs.orElse(null));
        script.setScript(scriptText);

        if (id > 0) {
            myScriptService.updateScript(script);
        }
        else {
            myScriptService.addScript(script);
        }

        Map<String, Object> response = Maps.newHashMap();
        response.put("success", true);
        response.put("data", script);

        return response;
    }

    @RequestMapping(value = "/get_result_by_uuid",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ScriptResult getScriptResultByUuid(@RequestParam UUID runUuid) {
        myLogger.info("getScriptResultByUuid: " + runUuid.toString());
        return myScriptService.getScriptResultByUuid(runUuid);
    }
}
