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

package com.github.swrirobotics.remote;

import com.github.swrirobotics.config.ConfigService;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {
    @Autowired
    private ConfigService myConfigService;

    private static Logger myLogger = LoggerFactory.getLogger(GeocodingService.class);

    public String getLocationName(double latitudeDeg, double longitudeDeg) {
        myLogger.trace("Reverse geocoding lat/long (" + latitudeDeg + ", " + longitudeDeg + ")");
        String key = myConfigService.getConfiguration().getGoogleApiKey();
        if (key == null || key.isEmpty()) {
            myLogger.warn("Google API Key has not been set.");
            return null;
        }

        GeoApiContext context = new GeoApiContext().setApiKey(key);
        String name = "(Unknown)";
        try {
            GeocodingResult[] results = GeocodingApi
                    .reverseGeocode(context, new LatLng(latitudeDeg, longitudeDeg)).await();
            myLogger.trace("Number of results: " + results.length);
            // There may be multiple results with sequentially less data in each;
            // just return the first one, since it should have the most info.
            for (GeocodingResult result : results) {
                myLogger.debug("Location for (" + latitudeDeg + ", " +
                               longitudeDeg + "): " + result.formattedAddress);
                return result.formattedAddress;
            }
        }
        catch (Exception e) {
            myLogger.error("Reverse geocoding failed for (" + latitudeDeg +
                           ", " + longitudeDeg + "):", e);
        }

        return name;
    }
}
