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

package com.github.swrirobotics.support.web;

import java.io.Serializable;

public class Configuration implements Serializable {
    private static final long serialVersionUID = 7088819138941988062L;

    private String bagPath = "/bags";
    private String driver = "org.h2.Driver";
    private String jdbcUsername = "sa";
    private String jdbcUrl = "jdbc:h2:mem:testdb";
    private String jdbcPassword = "";
    private String googleApiKey = "";
    private Boolean useBing = false;
    private String bingKey = "";
    private String adminPassword = "";
    private String[] vehicleNameTopics = new String[0];
    private String[] metadataTopics = new String[0];
    private String[] gpsTopics = new String[0];
    private Boolean debugJavascript = false;
    private Boolean removeOnDeletion = true;
    private Boolean fasterCodec = false;

    // Named "useMapQuest" for legacy support with older configs;
    // MapQuest is actually unsupported now and this will enable/disable
    // generic tile map support.
    private Boolean useMapQuest = true;
    private String tileMapUrl = "http://{a-d}.tile.stamen.com/terrain/{z}/{x}/{y}.jpg";
    private Integer tileWidthPx = 256;
    private Integer tileHeightPx = 256;

    public String getBagPath() {
        return bagPath;
    }

    public void setBagPath(String bagPath) {
        this.bagPath = bagPath;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getJdbcUsername() {
        return jdbcUsername;
    }

    public void setJdbcUsername(String jdbcUsername) {
        this.jdbcUsername = jdbcUsername;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public void setJdbcPassword(String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
    }

    public String getGoogleApiKey() {
        return googleApiKey;
    }

    public void setGoogleApiKey(String googleApiKey) {
        this.googleApiKey = googleApiKey;
    }

    public Boolean getUseMapQuest() {
        return useMapQuest;
    }

    public void setUseMapQuest(Boolean useMapQuest) {
        this.useMapQuest = useMapQuest;
    }

    public Boolean getUseBing() {
        return useBing;
    }

    public void setUseBing(Boolean useBing) {
        this.useBing = useBing;
    }

    public String getBingKey() {
        return bingKey;
    }

    public void setBingKey(String bingKey) {
        this.bingKey = bingKey;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String[] getVehicleNameTopics() {
        return vehicleNameTopics;
    }

    public void setVehicleNameTopics(String[] vehicleNameTopics) {
        this.vehicleNameTopics = vehicleNameTopics;
    }

    public String[] getGpsTopics() {
        return gpsTopics;
    }

    public void setGpsTopics(String[] gpsTopics) {
        this.gpsTopics = gpsTopics;
    }

    public Boolean getDebugJavascript() {
        return debugJavascript;
    }

    public void setDebugJavascript(Boolean debugJavascript) {
        this.debugJavascript = debugJavascript;
    }

    public Boolean getRemoveOnDeletion() {
        return removeOnDeletion;
    }

    public void setRemoveOnDeletion(Boolean removeOnDeletion) {
        this.removeOnDeletion = removeOnDeletion;
    }

    public String getTileMapUrl() {
        return tileMapUrl;
    }

    public void setTileMapUrl(String tileMapUrl) {
        this.tileMapUrl = tileMapUrl;
    }

    public Integer getTileWidthPx() {
        return tileWidthPx;
    }

    public void setTileWidthPx(Integer tileWidthPx) {
        this.tileWidthPx = tileWidthPx;
    }

    public Integer getTileHeightPx() {
        return tileHeightPx;
    }

    public void setTileHeightPx(Integer tileHeightPx) {
        this.tileHeightPx = tileHeightPx;
    }

    public String[] getMetadataTopics() {
        return metadataTopics;
    }

    public void setMetadataTopics(String[] metadataTopics) {
        this.metadataTopics = metadataTopics;
    }

    public Boolean getFasterCodec() {
    	return fasterCodec;
    }

    public void setFasterCodec(Boolean fasterCodec) {
    	this.fasterCodec = fasterCodec;
    }
}
