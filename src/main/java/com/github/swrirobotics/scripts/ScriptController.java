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

import com.github.swrirobotics.support.web.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
    public ScriptListDTO getScripts() {
        myLogger.debug("getScripts");
        ScriptListDTO list = myScriptService.getScripts();
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

        try {
            ScriptDTO script = myScriptService.getScript(scriptId);
            response.put("data", script);
            response.put("success", true);
        }
        catch (NonexistentScriptException e) {
            response.put("success", false);
            response.put("data", e.getLocalizedMessage());
        }

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

    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> saveScript(@RequestBody ScriptDTO scriptdto) {
        myLogger.info("saveScript");

        Long id = scriptdto.id;
        Map<String, Object> response = Maps.newHashMap();
        try {
            if (scriptdto.id > 0) {
                myLogger.info("Updating script " + scriptdto.id);
                myScriptService.updateScript(scriptdto);
            }
            else {
                myLogger.info("Saving new script");
                id = myScriptService.addScript(scriptdto);
            }

            response.put("success", true);
        }
        catch (NonexistentScriptException e) {
            response.put("success", false);
            myLogger.error("Try to save a script that doesn't exist.", e);
        }
        response.put("scriptId", id);

        return response;
    }

    @RequestMapping(value = "/get_result_by_uuid",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ScriptResultDTO getScriptResultByUuid(@RequestParam UUID runUuid) {
        myLogger.info("getScriptResultByUuid: " + runUuid.toString());
        return new ScriptResultDTO(myScriptService.getScriptResultByUuid(runUuid));
    }
}
