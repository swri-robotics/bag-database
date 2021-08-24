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
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.storage.BagWrapper;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.BagCount;
import com.github.swrirobotics.persistence.Tag;
import com.github.swrirobotics.support.web.*;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("bags")
@ControllerAdvice
public class BagController {
    private final BagService myBagService;

    private final Logger myLogger = LoggerFactory.getLogger(BagController.class);

    private static final long CHUNK_SIZE = 1000000000L;

    public BagController(BagService myBagService) {
        this.myBagService = myBagService;
    }

    @GetMapping(value="/download", produces="application/x-bag")
    @CrossOrigin(methods = {RequestMethod.GET, RequestMethod.HEAD},
                 exposedHeaders = {"ETag", "Content-Type", "Content-Length", "Accept-Ranges"},
                 maxAge = 3000)
    public ResponseEntity<ResourceRegion> downloadBag(
            @RequestHeader(value="Range", required=false) String rangeHeader,
            @RequestParam String bagId) throws IOException {
        long id = Long.parseLong(bagId);
        myLogger.info("downloadBag: " + id + "; range: " + rangeHeader);

        try (BagWrapper bag = myBagService.getBagWrapper(id)){
            myLogger.info("Found bag: " + bag.getFilename());
            Resource resource = bag.getResource();
            ResourceRegion region = getResourceRegion(resource, bag.getSize(), rangeHeader);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Accept-Ranges", "bytes");
            headers.add("Content-Disposition", "attachment; filename=" + bag.getFilename());
            headers.add("Content-Type", MediaType.APPLICATION_OCTET_STREAM.toString());
            headers.add("ETag", myBagService.getBagMd5Sum(id));

            return ResponseEntity
                .status(StringUtils.isBlank(rangeHeader) ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().noTransform())
                .body(region);
        }
        catch (NonexistentBagException e) {
            myLogger.warn("Bag not found.");
            return ResponseEntity.notFound().build();
        }
    }


    private ResourceRegion getResourceRegion(Resource resource, long contentLength, String httpHeaders) {
        ResourceRegion resourceRegion;

        if (StringUtils.isBlank(httpHeaders)) {
            return new ResourceRegion(resource, 0, contentLength);
        }

        long fromRange;
        long toRange;

        String[] ranges = httpHeaders.substring("bytes=".length()).split("-");
        fromRange = Long.parseLong(ranges[0]);
        if (ranges.length > 1) {
            toRange = Long.parseLong(ranges[1]);
        }
        else {
            toRange = contentLength - 1;
        }

        if (fromRange > 0) {
            long rangeLength = Math.min(CHUNK_SIZE, toRange - fromRange + 1);
            myLogger.debug("Returning range from " + fromRange + " to " + toRange);
            resourceRegion = new ResourceRegion(resource, fromRange, rangeLength);
        }
        else {
            long rangeLength = Math.min(CHUNK_SIZE, contentLength);
            myLogger.debug("Returning range from 0 to " + rangeLength);
            resourceRegion = new ResourceRegion(resource, 0, rangeLength);
        }

        return resourceRegion;
    }

    @RequestMapping("/get")
    public Bag getBag(@RequestParam Long bagId,
                      HttpServletResponse response) throws IOException {
        try {
            myLogger.info("getBag: " + bagId);
            return myBagService.getBag(bagId);
        }
        catch (NonexistentBagException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        return null;
    }

    @RequestMapping("/get_storage_ids")
    public StorageIdList getBagStorageIds() {
        return new StorageIdList(myBagService.getBagStorageIds());
    }

    @RequestMapping("/image")
    public ModelAndView getImage(@RequestParam Long bagId,
                                 @RequestParam String topic,
                                 @RequestParam Integer index) {
        myLogger.info("getImage: " + bagId + " / " + topic + " / " + index);
        ModelAndView mav = new ModelAndView("image/image");
        try {
            byte[] imageData = myBagService.getImage(bagId, topic, index);
            String imageString = "data:image/jpeg;base64," + Base64.getMimeEncoder().encodeToString(imageData);
            mav.getModel().put("imageData", imageString);
        }
        catch (BagReaderException | NonexistentBagException e) {
            mav.getModel().put("errorMessage", "Error retrieving image:<br>" + e.getLocalizedMessage());
        }
        return mav;
    }

    @RequestMapping("/video")
    public ResponseEntity<StreamingResponseBody> getVideo(@RequestParam Long bagId,
                                                          @RequestParam String topic,
                                                          @RequestParam Long frameSkip,
                                                          HttpServletResponse response) {
        myLogger.info("getVideo: " + bagId + ":" + topic);
        try {
            OutputStream output = response.getOutputStream();
            response.setContentType("video/webm;codecs=\"vp8\"");
            StreamingResponseBody stream = out -> {
                try {
                    myBagService.writeVideoStream(bagId, topic, frameSkip, output);
                }
                catch (BagReaderException | NonexistentBagException e) {
                    myLogger.error("Error reading bag file:", e);
                }
            };
            return new ResponseEntity<>(stream, HttpStatus.OK);
        }
        catch (IOException e) {
            myLogger.error("Error getting video stream:", e);
        }
        finally {
            myLogger.info("Finished getVideo()");
        }
        return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RequestMapping(value = "/update",
        method = RequestMethod.POST)
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

    @RequestMapping(value = "/upload",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> uploadBag(@RequestParam("file") MultipartFile file,
                                         @RequestParam String targetDirectory,
                                         @RequestParam(required=false, defaultValue="default") String storageId) {
        myLogger.info("uploadBag: " + file.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "");
        try {
            myBagService.uploadBag(file, targetDirectory, storageId);
            response.put("success", true);
        }
        catch (Exception e) {
            myLogger.error("Error uploading bag", e);
            response.put("message", e.getLocalizedMessage());
        }
        myLogger.info("uploadBag finished.");
        return response;
    }

    @RequestMapping("/paths")
    @ResponseBody
    public Map<String, Object> getPaths() {
        Map<String, Object> result = new HashMap<>();
        List<String> paths = myBagService.getPaths();
        result.put("totalCount", paths.size());
        List<Map<String, String>> pathList = new ArrayList<>();
        result.put("paths", pathList);
        for (String path : paths) {
            Map<String, String> pathEntry = new HashMap<>();
            pathEntry.put("path", path);
            pathList.add(pathEntry);
        }
        return result;
    }

    @RequestMapping("/treenode")
    public BagTreeNode[] getTreeNode(@RequestParam String node) throws IOException {
        myLogger.info("getTreeNode: " + node);

        List<BagTreeNode> nodes = myBagService.getTreePath(node);

        return nodes.toArray(new BagTreeNode[0]);
    }

    @RequestMapping("/filteredcount")
    public BagCount[] checkFilteredBagCounts(@RequestParam String text) {
        myLogger.info("checkFilteredBagCounts: " + text);

        return myBagService.checkFilteredBagCounts(text);
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
     * @param fillTopics Whether to fill the topics field of the bag objects
     * @param fillTypes Whether to fill the typcs field of the bag objects
     * @return All bag files that match the given search terms.
     */
    @RequestMapping("/search")
    public BagList getBags(@RequestParam String text,
                           @RequestParam(required = false) String[] fields,
                           @RequestParam Integer page,
                           @RequestParam Integer limit,
                           @RequestParam String sort,
                           @RequestParam String dir,
                           @RequestParam(required = false) ExtJsFilter[] filter,
                           @RequestParam(required = false) Boolean fillTopics,
                           @RequestParam(required = false) Boolean fillTypes) {
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
            if (!Boolean.TRUE.equals(fillTypes)) {
                bag.getMessageTypes().clear();
            }
            if (!Boolean.TRUE.equals(fillTopics)) {
                bag.getTopics().clear();
            }
        }

        return results;
    }

    @RequestMapping("/getTagsForBag")
    public Collection<Tag> getTagsForBag(@RequestParam Long bagId) throws NonexistentBagException {
        myLogger.info("getTagsForBag: " + bagId);
        Bag bag = myBagService.getBag(bagId);

        if (bag == null) {
            throw new NonexistentBagException("Bag not found: " + bagId);
        }

        Collection<Tag> tags = bag.getTags();
        myLogger.info("Returning " + tags.size() + " tags.");

        return tags;
    }

    /**
     * Sets a tag on a bag file.  If no tag with the given name exists, it will
     * create one; if one does exist, it will overwrite the current value.
     * @param tagName The name of the tag.
     * @param value The tag's value.  May be null, which is the same as an empty string.
     * @param bagId The ID of the bag to set the tag for.
     * @throws NonexistentBagException If the specified bag doesn't exist.
     */
    @RequestMapping(value = "/setTag", method = RequestMethod.POST)
    public void setTagForBag(@RequestParam String tagName,
                             @RequestParam(required = false) String value,
                             @RequestParam Long bagId) throws NonexistentBagException {
        myLogger.info("setTagForBag: " + tagName + ":" + value + " for bag " + bagId);
        myBagService.setTagForBag(tagName, value, bagId);
        myLogger.info("Set tag.");
    }

    @RequestMapping(value = "/setTagForBags", method = RequestMethod.POST)
    public void setTagForBags(@RequestParam String tagName,
                              @RequestParam(required = false) String value,
                              @RequestParam Long[] bagIds) throws NonexistentBagException {
        myLogger.info("setTagForBag: " + tagName + ":" + value +
                      " for bags " + Joiner.on(',').join(bagIds));
        for (Long bagId : bagIds) {
            myBagService.setTagForBag(tagName, value, bagId);
        }
        myLogger.info("Set tags.");
    }

    /**
     * Removes tags with given names from a bag file.
     * @param tagNames The names of the tags to remove.
     * @param bagId The ID of the bag to remove the tags from.
     * @throws NonexistentBagException If the specified bag doesn't exist.
     */
    @RequestMapping(value = "/removeTags", method = RequestMethod.POST)
    public void removeTagsForBag(@RequestParam String[] tagNames,
                                 @RequestParam Long bagId) throws NonexistentBagException {
        myLogger.info("removeTagsForBag: " + Joiner.on(',').join(tagNames) + " for bag " + bagId);
        myBagService.removeTagForBag(Lists.newArrayList(tagNames), bagId);
        myLogger.info("Removed tags.");
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
