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

package com.github.swrirobotics.bags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swrirobotics.bags.persistence.Bag;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.support.web.BagList;
import com.github.swrirobotics.support.web.BagUpdateStatus;
import com.github.swrirobotics.support.web.ExtJsFilter;
import com.github.swrirobotics.support.web.ExtJsFilterFormatter;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("bags")
@ControllerAdvice
public class BagController {
    @Autowired
    private BagService myBagService;

    private Logger myLogger = LoggerFactory.getLogger(BagController.class);

    @RequestMapping("/download")
    public FileSystemResource downloadBag(
            @RequestParam String bagId,
            HttpServletResponse response) throws IOException {
        Long id = Long.valueOf(bagId);
        myLogger.info("downloadBag: " + id);

        Bag bag = myBagService.getBag(id);

        if (bag != null) {
            response.setContentType("application/x-bag");
            response.setHeader("Content-Disposition", "attachment; filename=" + bag.getFilename());
            response.setHeader("Content-Transfer-Encoding", "application/octet-stream");
            myLogger.info("Found bag: " + bag.getPath() + bag.getFilename());
            return new FileSystemResource(bag.getPath() + bag.getFilename());
        }
        else {
            myLogger.warn("Bag not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
    }

    @RequestMapping("/get")
    public Bag getBag(@RequestParam Long bagId) {
        myLogger.info("getBag: " + bagId);
        return myBagService.getBag(bagId);
    }

    @RequestMapping("/image")
    public ModelAndView getImage(@RequestParam Long bagId,
                                 @RequestParam String topic,
                                 @RequestParam Integer index) throws FileNotFoundException {
        myLogger.info("getImage: " + bagId + " / " + topic + " / " + index);
        ModelAndView mav = new ModelAndView("image/image");
        try {
            byte[] imageData = myBagService.getImage(bagId, topic, index);
            String imageString = "data:image/jpeg;base64," + Base64.getMimeEncoder().encodeToString(imageData);
            mav.getModel().put("imageData", imageString);
        }
        catch (BagReaderException e) {
            mav.getModel().put("errorMessage", "Error retrieving image:<br>" + e.getLocalizedMessage());
        }
        return mav;
    }

    @RequestMapping("/update")
    public BagUpdateStatus updateBags(@RequestBody String req) {
        myLogger.info("updateBags");
        ObjectMapper mapper = new ObjectMapper();
        BagUpdateStatus status = new BagUpdateStatus();

        try {
            BagList bags = mapper.readValue(req, BagList.class);
            myLogger.info("Processing " + bags.getBags().size() + " bags.");

            for (Bag bag : bags.getBags()) {
                myBagService.updateBag(bag);
            }
            status.success = true;
        }
        catch (IOException e) {
            myLogger.warn("Error deserializing bags:", e);
        }
        catch (RuntimeException e) {
            myLogger.warn("Unexpected error updating bags:", e);
        }

        return status;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.addCustomFormatter(new ExtJsFilterFormatter());
    }

    /**
     * Searches for bag files that match the given criteria.
     *
     * If any of the indicated fields contain the provided text, a bag
     * will be considered a match.  If filters are provided, all filtered
     * fields must match in order for the bag to be considered a match
     *
     * @param text Text to search for in the provided fields
     * @param fields Fields to search
     * @param page The page of results to return (indexes start at 1)
     * @param limit The number of results per page
     * @param sort The column on which to sort the results
     * @param dir The direction of the sort
     * @param filter ExtJS grid filters to apply to the results
     * @return All bag files that match the given search terms.
     */
    @RequestMapping("/search")
    public BagList getBags(@RequestParam String text,
                           @RequestParam(required = false) String[] fields,
                           @RequestParam Integer page,
                           @RequestParam Integer limit,
                           @RequestParam String sort,
                           @RequestParam String dir,
                           @RequestParam(required = false) ExtJsFilter[] filter) {
        myLogger.info("getBags: " + text + " / page: " + page +
                      " / limit: " + limit + " / sort: " + sort +
                      " / dir: " + dir);
        if (fields != null) {
            myLogger.info("  Searching text fields: " + Joiner.on(", ").skipNulls().join(fields));
        }

        if (filter != null) {
            for (ExtJsFilter f : filter) {
                myLogger.info("  Grid Filter: " + f.getProperty() +
                              " " + f.getOperator() + " " + f.getValue());
            }
        }

        BagList results;

        try {
            results = myBagService.findBagsContainingText(text,
                                                          fields,
                                                          filter,
                                                          page,
                                                          limit,
                                                          dir,
                                                          sort);
        }
        catch (RuntimeException e) {
            myLogger.error("Error searching bags", e);
            throw e;
        }

        for (Bag bag : results.getBags()) {
            // The big grid doesn't need this information, and serializing it for
            // every bag will slow things down by a lot.
            bag.getMessageTypes().clear();
            bag.getTopics().clear();
        }

        return results;
    }

    /**
     * Returns all of the GPS coordinates for the given set of bags.  The
     * returned coordinates are a flat list of longitude/latitude pairs that
     * are sorted in ascending order of their timestamp in the database.
     * @param bagIds All of the bags to get coordinates for.
     * @return All of the GPS coordinates for those bags.
     */
    @RequestMapping("/coords")
    public List<Double[]> getGpsCoordsForBags(@RequestParam Long[] bagIds) {
        return myBagService.getGpsCoordsForBags(Lists.newArrayList(bagIds));
    }
}
