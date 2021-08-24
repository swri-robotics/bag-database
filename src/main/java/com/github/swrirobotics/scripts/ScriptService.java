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

import com.amihaiemil.docker.Docker;
import com.amihaiemil.docker.TcpDocker;
import com.github.swrirobotics.bags.NonexistentBagException;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.*;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.github.swrirobotics.support.web.ScriptDTO;
import com.github.swrirobotics.support.web.ScriptListDTO;
import com.github.swrirobotics.support.web.ScriptResultDTO;
import com.github.swrirobotics.support.web.ScriptResultList;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import org.apache.commons.compress.utils.Lists;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.json.*;
import java.io.StringReader;
import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
public class ScriptService extends StatusProvider {
    @Autowired
    private ConfigService configService;
    @Autowired
    private ScriptCriteriaRepository criteriaRepository;
    @Autowired
    private ScriptRepository scriptRepository;
    @Autowired
    private ScriptResultRepository resultRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private BagRepository bagRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private ApplicationContext myAC;

    private final GeometryFactory myGeometryFactory =
        new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    private ThreadPoolTaskExecutor taskExecutor;

    private static final int MINIMUM_THREAD_POOL_SIZE = 4;

    private static final Logger myLogger = LoggerFactory.getLogger(ScriptService.class);

    final private List<RunnableScript> runningScripts = Lists.newArrayList();

    @Scheduled(fixedRate = 500)
    public void checkRunningScripts() {
        synchronized (runningScripts) {
            if (runningScripts.isEmpty()) {
                return;
            }

            String info = "Before check: " + runningScripts.size() + " scripts currently running.";
            myLogger.debug(info);

            long now = System.currentTimeMillis();
            List<RunnableScript> finishedScripts = Lists.newArrayList();
            for (RunnableScript script : runningScripts) {
                Future<?> future = script.getFuture();
                if (future.isCancelled() || future.isDone()) {
                    if (script.getEndStatus() != null) {
                        reportStatus(script.getEndStatus());
                    }
                    finishedScripts.add(script);
                    messagingTemplate.convertAndSend("/topic/script_finished", script.getRunUuid());
                    continue;
                }

                Double timeout = script.getScript().getTimeoutSecs();
                if (timeout != null && timeout > 0.0) {
                    double elapsed = (now - script.getStartTime()) / 1000.0;
                    if (elapsed > timeout) {
                        myLogger.warn("Cancelling run for script ["
                            + script.getScript().getName() + "] due to timeout.");
                        future.cancel(true);
                    }
                }
            }

            runningScripts.removeAll(finishedScripts);
            if (taskExecutor.getActiveCount() != runningScripts.size()) {
                myLogger.warn("Number of running scripts doesn't match active task threads!  ("
                    + runningScripts.size() + " vs . " + taskExecutor.getActiveCount() + ")");
            }

            if (runningScripts.isEmpty()) {
                info = "All scripts finished.";
                myLogger.debug(info);
                reportStatus(Status.State.IDLE, info);
            }
            else {
                info = runningScripts.size() + " scripts currently running.";
                myLogger.debug(info);
                reportStatus(Status.State.WORKING, info);
            }
        }
    }

    @Transactional(readOnly = true)
    public ScriptListDTO getScripts() {
        ScriptListDTO list = new ScriptListDTO();
        list.setScripts(scriptRepository.findAll());
        list.setTotalCount(list.getScripts().size());
        return list;
    }

    @Transactional(readOnly = true)
    public ScriptResultList getScriptResults() {
        ScriptResultList list = new ScriptResultList();
        list.setResults(resultRepository.findAll().stream().map(ScriptResultDTO::new).collect(Collectors.toList()));
        list.setTotalCount(list.getResults().size());
        return list;
    }

    @PostConstruct
    public void initializeTaskExecutor() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(MINIMUM_THREAD_POOL_SIZE);
        taskExecutor.setMaxPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), MINIMUM_THREAD_POOL_SIZE));
        taskExecutor.initialize();
    }

    @PreDestroy
    public void destroyTaskExecutor() {
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }
    }

    @Transactional
    public Long addScript(ScriptDTO scriptDto) {
        Script script = scriptDto.toScript(null);
        script.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        script.setUpdatedOn(script.getCreatedOn());
        scriptRepository.save(script);
        //criteriaRepository.saveAll(script.getCriteria());
        return script.getId();
    }

    @Transactional(readOnly = true)
    public List<Script> getAutomaticScripts() {
        return scriptRepository.findByRunAutomatically(true);
    }

    @Transactional(readOnly = true)
    public ScriptDTO getScript(Long scriptId) throws NonexistentScriptException {
        return new ScriptDTO(scriptRepository.findById(scriptId).orElseThrow(() ->
            new NonexistentScriptException("Script " + scriptId + " does not exist")));
    }

    @Transactional
    public void updateScript(ScriptDTO scriptDto) throws NonexistentScriptException {
        Script script = scriptRepository.findById(scriptDto.id).orElseThrow(() ->
            new NonexistentScriptException("Script " + scriptDto.id + " does not exist."));
        // Trying to match new criteria to old ones is hard, just wipe them all out every time
        criteriaRepository.deleteAll(script.getCriteria());
        scriptDto.toScript(script);
        script.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
        scriptRepository.save(script);
        criteriaRepository.saveAll(script.getCriteria());
    }

    /**
     * Processes the output from a script and updates tags for bags in the database if formatted correctly.
     * If any of the supported attributes are supplied, every indicated bag will be updated in the database.
     * Correctly-formatted output is a JSON object that contains any of these elements:
     * addTags, removeTags, setGpsCoordinates, setDescription, setLocation, setVehicle
     *
     * Example output:
     * {
     *   "addTags" : {
     *      "message_count" : 602
     *   },
     *   "removeTags" : [ "old_tag" ],
     *   "setDescription" : "Processed",
     *   "setGpsCoordinates" : {
     *      "latitude" : 30,
     *      "longitude" : 60
     *   },
     *   "setLocation" : "Here",
     *   "setVehicle" : "Something"
     * }
     * @param stdout JSON output from the script
     * @param bagIds IDs of bags that should be updated
     */
    @Transactional
    public void processScriptOutput(String stdout, List<Long> bagIds) {
        myLogger.debug("processScriptOutput: for bags [" + Joiner.on(',').join(bagIds) + "]");

        try (JsonReader reader = Json.createReader(new StringReader(stdout))) {
            JsonObject object = reader.readObject();

            boolean saveBags = false;
            List<Bag> bags = bagRepository.findAllById(bagIds);

            // Add new tags
            JsonObject addTags = object.getJsonObject("addTags");
            List<Tag> newTags = new ArrayList<>();
            if (addTags != null)
            {
                myLogger.debug("Adding tags");
                saveBags = true;
                addTags.forEach((s, jsonValue) -> {
                    for (Bag bag : bags) {
                        boolean needsNewTag = true;
                        // If a tag with a key already exists, update it
                        for (Tag tag : bag.getTags()) {
                            if (tag.getTag().equals(s)) {
                                tag.setValue(jsonValue.toString());
                                needsNewTag = false;
                                break;
                            }
                        }
                        if (needsNewTag) {
                            Tag tag = new Tag();
                            tag.setTag(s);
                            tag.setValue(jsonValue.toString());
                            tag.setBag(bag);
                            bag.getTags().add(tag);
                            newTags.add(tag);
                        }
                    }
                });
            }
            if (!newTags.isEmpty()) {
                tagRepository.saveAll(newTags);
            }

            // Remove old tags
            JsonArray removeTags = object.getJsonArray("removeTags");
            if (removeTags != null) {
                saveBags = true;
                List<String> tags = removeTags.stream().map(c -> ((JsonString)c).getString()).collect(Collectors.toList());
                List<Tag> tagsToRemove = new ArrayList<>();
                for (Bag bag : bags) {
                    bag.getTags().removeIf(t -> {
                        if (tags.contains(t.getTag())) {
                            tagsToRemove.add(t);
                            return true;
                        }
                        return false;
                    });
                }
                tagRepository.deleteAll(tagsToRemove);
            }

            // Update the GPS coordinates
            JsonObject coords = object.getJsonObject("setGpsCoordinates");
            if (coords != null &&
                (coords.getJsonNumber("latitude") != null && coords.getJsonNumber("longitude") != null)) {
                double latitude = coords.getJsonNumber("latitude").doubleValue();
                double longitude = coords.getJsonNumber("longitude").doubleValue();
                if (latitude > 90.0 || latitude < -90.0 || longitude > 180.0 || longitude < -180.0) {
                    myLogger.warn("Latitude/longitude were invalid.");
                }
                else {
                    saveBags = true;
                    for (Bag bag : bags) {
                        bag.setCoordinate(myGeometryFactory.createPoint(new Coordinate(longitude, latitude)));
                    }
                }
            }

            // Update the description
            String description = object.getString("setDescription", null);
            if (description != null) {
                saveBags = true;
                for (Bag bag : bags) {
                    bag.setDescription(description);
                }
            }

            // Update the location
            String location = object.getString("setLocation", null);
            if (location != null) {
                saveBags = true;
                for (Bag bag : bags) {
                    bag.setLocation(location);
                }
            }

            // Update the vehicle name
            String vehicle = object.getString("setVehicle", null);
            if (vehicle != null) {
                saveBags = true;
                for (Bag bag : bags) {
                    bag.setVehicle(vehicle);
                }
            }

            if (saveBags) {
                bagRepository.saveAll(bags);
            }
        }
        catch (JsonException | IllegalStateException e) {
            myLogger.warn("Unable to parse JSON in script output", e);
        }
    }

    @Transactional
    public void removeScript(Long scriptId) {
        scriptRepository.deleteById(scriptId);
    }

    /**
     * Returns true if the automatic run criteria for a script successfully match against a bag.
     *
     * A bag is considered to match a script's criteria if either the script has no criteria or if, for any set
     * of criteria on the script, all of that set's conditions (excluding any that are blank) match the bag.
     * For each set of script criteria "sc", you can think of that boolean logic as looking like:
     * (sc1.filename matches AND sc1.directory matches AND sc1.messagetypes matches AND sc1.topicnames matches) OR
     * (sc2.filename matches AND sc2.directory matches AND sc2.messagetypes matches AND sc2.topicnames matches) OR ...
     *
     * The Filename and Directory fields are both regular expressions that are matched against the bag; the Message
     * Types and Topic Names are comma-separated lists, and all of their values must be present in the bag in order
     * for them to match.
     * @param bagId The bag to check.
     * @param scriptId The script to check.
     * @return True if the bag matches the script's automatic run criteria.
     * @throws NonexistentScriptException If the script doesn't exist.
     * @throws NonexistentBagException If the bag doesn't exist.
     */
    @Transactional(readOnly = true)
    public boolean bagMatchesCriteria(Long bagId, Long scriptId) throws NonexistentScriptException, NonexistentBagException {
        Script script = scriptRepository.findById(scriptId).orElseThrow(() ->
            new NonexistentScriptException("No script exists with ID " + scriptId));

        if (script.getCriteria().isEmpty()) {
            return true;
        }

        Bag bag = bagRepository.findById(bagId).orElseThrow(() ->
            new NonexistentBagException("No bag exists with ID " + bagId));

        Splitter commaSplitter = Splitter.on(',').trimResults().omitEmptyStrings();
        Joiner commaJoiner = Joiner.on(',');

        for (ScriptCriteria sc : script.getCriteria()) {
            if (!sc.getFilename().isEmpty() && !bag.getFilename().matches(sc.getFilename())) {
                myLogger.debug("Filename [" + bag.getFilename() + "]  did not match regex /" + sc.getFilename() + "/");
                continue;
            }
            if (!sc.getDirectory().isEmpty() && !bag.getPath().matches(sc.getDirectory())) {
                myLogger.debug("Directory [" + bag.getPath() + "] did not match regex /" + sc.getDirectory() + "/");
                continue;
            }
            Set<String> bagMessages = bag.getMessageTypes().stream().map(MessageType::getName).collect(Collectors.toSet());
            Set<String> scMessages = Sets.newHashSet(commaSplitter.splitToList(sc.getMessageTypes()));
            if (!sc.getMessageTypes().isEmpty() && !bagMessages.containsAll(scMessages)) {
                myLogger.debug("Bag's message types [" + commaJoiner.join(bagMessages) + "] did not contain all of: ["
                    + commaJoiner.join(scMessages) + "]");
                continue;
            }
            Set<String> bagTopics = bag.getTopics().stream().map(Topic::getTopicName).collect(Collectors.toSet());
            Set<String> scTopics = Sets.newHashSet(commaSplitter.splitToList(sc.getTopicNames()));
            if (!sc.getTopicNames().isEmpty() && !bagTopics.containsAll(scTopics)) {
                myLogger.debug("Bag's topics [" + commaJoiner.join(bagTopics) + "] did not contain all of: ["
                    + commaJoiner.join(scTopics) + "]");
                continue;
            }
            return true;
        }

        return false;
    }

    /**
     * Runs a script on a set of bag files.  This function does a few basic checks and then dispatches the
     * script to a task executor that runs it in a separate thread.
     * @param scriptId The ID of the script to run.
     * @param bagIds A list of bags to run the script on.
     * @return A UUID that can be used to look up the results of the run after it finished.
     * @throws ScriptRunException If there was an error starting the task.
     */
    @Transactional
    public UUID runScript(Long scriptId, List<Long> bagIds) throws ScriptRunException {
        if (bagIds.isEmpty()) {
            throw new ScriptRunException("You must specify bag files.");
        }

        Docker docker = new TcpDocker(URI.create(configService.getConfiguration().getDockerHost()));

        Script script = scriptRepository.findById(scriptId).orElseThrow(
            () -> new ScriptRunException("Script " + scriptId + " doesn't exist"));

        List<Bag> bags = bagRepository.findAllById(bagIds);
        if (bags.isEmpty()) {
            throw new ScriptRunException("No bag files found.");
        }

        myLogger.debug("Dispatching script to executor.");
        var runScript = myAC.getBean(RunnableScript.class);
        runScript.initialize(script, bags, docker);
        runScript.setFuture(taskExecutor.submit(runScript));
        runningScripts.add(runScript);

        return runScript.getRunUuid();
    }

    @Transactional(readOnly = true)
    public ScriptResult getScriptResultByUuid(UUID runUuid) {
        return resultRepository.findByRunUuid(runUuid);
    }

    public void getRunningScripts() {
        // TODO pjr Get info about currently running scripts
    }

    public void stopRunningScript() {
        // TODO pjr Interrupt a script that is currently running
    }

    @Override
    protected String getStatusProviderName() {
        return "Script Service";
    }
}
