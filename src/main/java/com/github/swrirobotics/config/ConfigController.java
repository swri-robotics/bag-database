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

import com.github.swrirobotics.support.web.Configuration;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("config")
@Secured("ROLE_ADMIN")
public class ConfigController {
    @Autowired
    private ConfigService myConfigService;

    private final Logger myLogger = LoggerFactory.getLogger(ConfigController.class);

    @RequestMapping(value="", method=RequestMethod.GET)
    public String getConfig(Model model) {
        myLogger.info("getConfig");
        Configuration config = myConfigService.getConfiguration();
        myLogger.info("JDBC URL: " + config.getJdbcUrl());
        model.addAttribute("config", config);
        return "config/config";
    }

    @RequestMapping(value="/get")
    @ResponseBody
    public Map<String, Object> formPost(@ModelAttribute Configuration config,
                                        Model model,
                                        HttpServletRequest req,
                                        HttpServletResponse resp) throws IOException {
        boolean isSettingConfig = req.getParameter("bagPath") != null;
        if (!isSettingConfig) {
            myLogger.info("getConfigValues");
            Map<String, Object> response = Maps.newHashMap();
            response.put("success", true);
            response.put("data", myConfigService.getConfiguration());
            myLogger.info("JDBC URL: " + ((Configuration)response.get("data")).getJdbcUrl());
            return response;
        }
        else {
            myLogger.info("setConfigValues");
            Map<String, Object> response = Maps.newHashMap();
            try {
                myConfigService.setConfiguration(config);
                response.put("success", true);
            }
            catch (IOException e) {
                myLogger.error("Error updating config", e);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.put("success", false);
            }
            return response;
        }
    }
}
