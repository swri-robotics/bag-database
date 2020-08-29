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

package com.github.swrirobotics.bags.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Set;

@Entity
@Table(name="scripts")
public class Script implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Boolean allowNetworkAccess;
    private String description;
    @Column(length = 128)
    private String dockerImage;
    private Long memoryLimitBytes;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private Boolean runAutomatically;
    @Column(nullable = false)
    private String script;
    private Double timeoutSecs;
    private Timestamp createdOn;
    private Timestamp updatedOn;

    @OneToMany(mappedBy = "script",
            cascade={CascadeType.REFRESH, CascadeType.MERGE},
            fetch = FetchType.EAGER)
    private Set<ScriptCriteria> criteria = Sets.newHashSet();

    @OneToMany(mappedBy = "script",
            cascade={CascadeType.REFRESH, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<ScriptResult> results = Sets.newHashSet();

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

    public Set<ScriptCriteria> getCriteria() {
        return criteria;
    }

    private void setCriteria(Set<ScriptCriteria> criteria) {
        this.criteria = criteria;
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

    public Set<ScriptResult> getResults() {
        return results;
    }

    private void setResults(Set<ScriptResult> results) {
        this.results = results;
    }
}
