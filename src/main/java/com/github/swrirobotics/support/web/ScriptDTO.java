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

package com.github.swrirobotics.support.web;

import com.github.swrirobotics.persistence.Script;
import com.github.swrirobotics.persistence.ScriptCriteria;

import java.sql.Timestamp;

public class ScriptDTO {
    public Long id = 0L;
    public Boolean allowNetworkAccess = false;
    public String description = "";
    public String dockerImage = "";
    public Long memoryLimitBytes;
    public String name = "";
    public Boolean runAutomatically = false;
    public ScriptCriteriaDTO[] criteria = new ScriptCriteriaDTO[0];
    public String script = "";
    public Double timeoutSecs;
    public Timestamp createdOn;
    public Timestamp updatedOn;

    public ScriptDTO() {}

    public ScriptDTO(Script script) {
        this.id = script.getId();
        this.allowNetworkAccess = script.getAllowNetworkAccess();
        this.description = script.getDescription();
        this.dockerImage = script.getDockerImage();
        this.memoryLimitBytes = script.getMemoryLimitBytes();
        this.name = script.getName();
        this.runAutomatically = script.getRunAutomatically();
        criteria = script.getCriteria().stream().map(ScriptCriteriaDTO::new).toArray(ScriptCriteriaDTO[]::new);
        this.script = script.getScript();
        this.timeoutSecs = script.getTimeoutSecs();
        this.createdOn = script.getCreatedOn();
        this.updatedOn = script.getUpdatedOn();
    }

    public Script toScript(Script script) {
        if (script == null) {
            script = new Script();
        }
        if (id != null) {
            script.setId(id);
        }
        script.setAllowNetworkAccess(allowNetworkAccess);
        script.setDescription(description);
        script.setDockerImage(dockerImage);
        script.setMemoryLimitBytes(memoryLimitBytes);
        script.setName(name);
        script.setRunAutomatically(runAutomatically);
        script.setScript(this.script);
        script.setTimeoutSecs(timeoutSecs);
        script.setCreatedOn(createdOn);
        script.setUpdatedOn(updatedOn);
        script.getCriteria().clear();

        for (ScriptCriteriaDTO scdto : criteria) {
            ScriptCriteria sc = scdto.toScriptCriteria(null);
            sc.setScript(script);
            script.getCriteria().add(sc);
        }

        return script;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getAllowNetworkAccess() {
        return allowNetworkAccess;
    }

    public void setAllowNetworkAccess(Boolean allowNetworkAccess) {
        this.allowNetworkAccess = allowNetworkAccess;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public Long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    public void setMemoryLimitBytes(Long memoryLimitBytes) {
        this.memoryLimitBytes = memoryLimitBytes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getRunAutomatically() {
        return runAutomatically;
    }

    public void setRunAutomatically(Boolean runAutomatically) {
        this.runAutomatically = runAutomatically;
    }

    public ScriptCriteriaDTO[] getCriteria() {
        return criteria;
    }

    public void setCriteria(ScriptCriteriaDTO[] criteria) {
        this.criteria = criteria;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public Double getTimeoutSecs() {
        return timeoutSecs;
    }

    public void setTimeoutSecs(Double timeoutSecs) {
        this.timeoutSecs = timeoutSecs;
    }

    public Timestamp getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Timestamp createdOn) {
        this.createdOn = createdOn;
    }

    public Timestamp getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Timestamp updatedOn) {
        this.updatedOn = updatedOn;
    }
}
