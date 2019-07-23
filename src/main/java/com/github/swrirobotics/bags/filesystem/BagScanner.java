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

package com.github.swrirobotics.bags.filesystem;

import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.filesystem.watcher.RecursiveWatcher;
import com.github.swrirobotics.bags.persistence.*;
import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.BagReader;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.Float64Type;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.StringType;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.remote.GeocodingService;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Profile("default")
// This class doesn't directly access it, but we need to depend on the Liquibase
// bean to ensure the database is configured before our @PostContruct runs.
@DependsOn("liquibase")
public class BagScanner extends StatusProvider implements RecursiveWatcher.WatchListener {
    @Autowired
    private ConfigService myConfigService;
    @Autowired
    private BagRepository myBagRepo;
    @Autowired
    private MessageTypeRepository myMTRepo;
    @Autowired
    private TopicRepository myTopicRepo;
    @Autowired
    private TagRepository myTagRepository;
    @Autowired
    private BagService myBagService;
    @Autowired
    private GeocodingService myGeocodingService;

    @PersistenceContext
    private EntityManager myEM;

    private final ExecutorService myExecutor = Executors.newSingleThreadExecutor();

    private RecursiveWatcher myWatcher = null;

    private final Logger myLogger = LoggerFactory.getLogger(BagScanner.class);

    private final DirectoryStream.Filter<Path> myDirFilter = path -> path.toFile().isDirectory();

    public String getBagDirectory() {
        return myConfigService.getConfiguration().getBagPath();
    }

    @PostConstruct
    public void initialize() {
        String updateMsg = "Bag scanner is initializing.";
        reportStatus(Status.State.WORKING, updateMsg);
        myLogger.info(updateMsg);

        // All of these update tasks can be manually activated
        // through the admin page, but uncomment them here if you
        // want them to be run on startup.

        // Rebuilds the Lucene search database from the SQL database
        //rebuildLuceneDatabase();
        // Updates the lat/lon coordinates in bags from the bag files
        //updateAllLatLons();
        // Updates the "location" string via reverse Geocoding the lat/lon coordinates
        //updateAllLocations();
        // Updates the paths for the bags from their GPS coordinates
        //updateAllGpsPaths();
        // Updates the vehicle names from the bag files
        //updateAllVehicleNames();

        String bagDir = getBagDirectory();
        if (bagDir != null && !bagDir.isEmpty()) {
            Path path = FileSystems.getDefault().getPath(bagDir);
            myWatcher = RecursiveWatcher.createRecursiveWatcher(
                    path, new ArrayList<Path>(), 3000, this);
            try {
                myWatcher.start();
            }
            catch (Exception e) {
                myLogger.error("Unable to monitor bag directory for changes.", e);
            }
        }

        scanDirectory(false);
    }

    /**
     * Stops the file watcher that is watching the current bag directory,
     * then reinitializes everything.
     */
    public void reset() {
        if (myWatcher != null) {
            myWatcher.stop();
        }
        initialize();
    }

    private abstract class MassBagUpdater implements Runnable {
        @Override
        public void run() {
            String updateMsg = "Updating " + updateType() + " for all bag files.";
            myLogger.info(updateMsg);
            List<Long> bagIds = myBagService.getAllBagIds();

            for (Long bagId : bagIds) {
                reportStatus(Status.State.WORKING, updateMsg);
                updateBag(bagId);
            }

            String doneMsg = "Done updating " + updateType() + " for all bag files.";
            reportStatus(Status.State.IDLE, doneMsg);
            myLogger.info(doneMsg);
        }

        abstract protected String updateType();

        @Transactional
        abstract protected void updateBag(Long bagId);
    }

    private class LocationUpdater extends MassBagUpdater {
        @Override
        protected String updateType() {
            return "locations";
        }

        @Override
        @Transactional
        public void updateBag(Long bagId) {
            Bag bag = myBagRepo.findOne(bagId);
            if ((bag.getLocation() == null || bag.getLocation().isEmpty()) &&
                    bag.getLatitudeDeg() != null && bag.getLongitudeDeg() != null &&
                    Math.abs(bag.getLatitudeDeg()) > 0.0001 &&
                    Math.abs(bag.getLongitudeDeg()) > 0.0001) {
                myLogger.debug("Updating location for bag " + bagId + ".");
                String location = myGeocodingService.getLocationName(bag.getLatitudeDeg(), bag.getLongitudeDeg());
                bag.setLocation(location);
                myBagRepo.save(bag);
            }
        }
    }

    private class VehicleNameUpdater extends MassBagUpdater {
        @Override
        protected String updateType() {
            return "vehicle names";
        }

        @Override
        @Transactional
        public void updateBag(Long bagId) {
            String[] vehicleNames =
                    myConfigService.getConfiguration().getVehicleNameTopics();
            if (vehicleNames.length == 0) {
                myLogger.debug("No vehicle name topics configured.");
                return;
            }

            Bag bag = myBagRepo.findOne(bagId);
            if (bag.getVehicle() == null || bag.getVehicle().isEmpty()) {
                String fullPath = bag.getPath() + bag.getFilename();
                try {
                    BagFile bagFile = BagReader.readFile(fullPath);
                    String name = myBagService.getVehicleName(bagFile);
                    if (name != null) {
                        myLogger.debug("Setting vehicle name for " +
                                       fullPath + " to " + name);
                        bag.setVehicle(name);
                        myBagRepo.save(bag);
                    }
                }
                catch (BagReaderException e) {
                    reportStatus(Status.State.ERROR,
                                 "Unable to get vehicle name from bag file " +
                                 fullPath + ": " + e.getLocalizedMessage());
                    reportStatus(Status.State.WORKING,
                                 "Updating vehicle names for all bag files.");
                }
            }
        }
    }

    private class TagUpdater extends MassBagUpdater {
        @Override
        protected String updateType() {
            return "tags";
        }

        @Override
        @Transactional
        public void updateBag(Long bagId) {
            Bag bag = myBagRepo.findOne(bagId);
            String fullPath = bag.getPath() + bag.getFilename();
            try {
                BagFile bagFile = BagReader.readFile(fullPath);
                myBagService.addTagsToBag(bagFile,bag);
            } catch (BagReaderException e) {
                reportStatus(Status.State.ERROR,
                        "Unable to get tags from bag file " +
                                fullPath + ": " + e.getLocalizedMessage());
                reportStatus(Status.State.WORKING,
                        "Updating tags for all bag files.");
            }
        }
    }


    private class GpsPathUpdater extends MassBagUpdater {
        @Override
        protected String updateType() {
            return "GPS paths";
        }

        @Override
        @Transactional
        public void updateBag(Long bagId) {
            myBagService.updateGpsPositionsForBagId(bagId);
        }
    }

    private class GpsInfoUpdater extends MassBagUpdater {
        @Override
        protected String updateType() {
            return "GPS info";
        }

        @Override
        @Transactional
        public void updateBag(Long bagId) {
            Bag bag = myBagRepo.findOne(bagId);

            if (bag.getLatitudeDeg() == null ||
                    bag.getLongitudeDeg() == null ||
                    (Math.abs(bag.getLatitudeDeg()) < 0.0001 &&
                            Math.abs(bag.getLongitudeDeg()) < 0.0001)) {
                String fullPath = bag.getPath() + bag.getFilename();
                try {
                    BagFile bagFile = BagReader.readFile(fullPath);
                    MessageType mt = bagFile.getFirstMessageOfType("gps_common/GPSFix");
                    if (mt == null) {
                        mt = bagFile.getFirstMessageOfType("sensor_msgs/NavSatFix");
                    }
                    if (mt == null) {
                        mt = bagFile.getFirstMessageOfType("marti_gps_common/GPSFix");
                    }
                    if (mt == null) {
                        myLogger.debug("No GPSFix or NavSatFix message found in bag " + fullPath + ".");
                    }
                    else {
                        bag.setCoordinate(myBagService.makePoint(
                                mt.<Float64Type>getField("latitude").getValue(),
                                mt.<Float64Type>getField("longitude").getValue()));
                        myLogger.debug("Setting lat/lon for " + fullPath + " to: " +
                                               bag.getLatitudeDeg() + " / " + bag.getLongitudeDeg());
                        myBagRepo.save(bag);
                    }
                }
                catch (BagReaderException | UninitializedFieldException e) {
                    reportStatus(Status.State.ERROR,
                                 "Unable to get GPS info from bag file " + fullPath + ": " + e.getLocalizedMessage());
                    reportStatus(Status.State.WORKING, "Updating GPS info for all bag files.");
                }
            }
        }
    }

    @PreDestroy
    public void destroy() {
        myExecutor.shutdownNow();
    }

    public void updateAllLatLons() {
        myExecutor.execute(new GpsInfoUpdater());
    }

    public void updateAllLocations() {
        myExecutor.execute(new LocationUpdater());
    }

    public void updateAllGpsPaths() {
        myExecutor.execute(new GpsPathUpdater());
    }

    public void updateAllVehicleNames() {
        myExecutor.execute(new VehicleNameUpdater());
    }

    public void updateAllTags() {
        myExecutor.execute(new TagUpdater());
    }

    public void scanDirectory(boolean forceUpdate) {
        String bagDir = myConfigService.getConfiguration().getBagPath();
        if (bagDir == null || bagDir.isEmpty()) {
            myLogger.info("No bag directory set; not scanning.");
            reportStatus(Status.State.ERROR, "No bag directory set; please configure the server.");
            return;
        }

        myLogger.info("Scheduling a " + (forceUpdate ? "full" : "quick") + " directory scan.");
        myExecutor.execute(new FullScanner(forceUpdate, bagDir));
    }

    private boolean presentSpecialCharacters(Path dir){
        String dirName = dir.toString();
        Pattern p = Pattern.compile("@.*");
        Matcher m = p.matcher(dirName);
        return m.find();
    }

    private Set<File> getBagFiles(Path dir) {
        Set<File> bagFiles = Sets.newHashSet();
        if (!presentSpecialCharacters(dir.getFileName())) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.bag")) {
                for (Path bagFile : stream) {
                    myLogger.trace("  Adding: " + bagFile.toString());
                    bagFiles.add(bagFile.toAbsolutePath().toFile());
                }
            } catch (IOException e) {
                myLogger.error("Error parsing directory:", e);
                reportStatus(Status.State.ERROR, "Unable to read directory: " + dir.toString());
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, myDirFilter)) {
                for (Path subdir : stream) {
                    myLogger.trace("  Checking subdir: " + subdir.toString());
                    bagFiles.addAll(getBagFiles(subdir));
                }
            } catch (IOException e) {
                myLogger.error("Error parsing subdirectory:", e);
            }
        }
        return bagFiles;
    }

    @Override
    public void watchEventsOccurred() {
        myLogger.info("Filesystem change detected.");
        scanDirectory(false);
    }

    @Override
    protected String getStatusProviderName() {
        return "Bag Scanner";
    }

    private class FullScanner implements Runnable {
        private boolean forceUpdate = false;
        private String myBagDirectory;

        private final ExecutorService bagService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());

        public FullScanner(boolean forceUpdate, String bagPath) {
            this.myBagDirectory = bagPath;
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {
            String msg = "Scanning for new bag files in " + myBagDirectory + ".";
            reportStatus(Status.State.WORKING, msg);
            myLogger.info(msg);
            try {
                Path bagDir = FileSystems.getDefault().getPath(myBagDirectory);
                Set<File> bagFiles = getBagFiles(bagDir);

                myLogger.debug("Found " + bagFiles.size() + " bag files on disk.");

                final Map<String, Long> existingBagPaths = Maps.newHashMap();
                final Map<String, Long> missingBagMd5sums = Maps.newHashMap();

                // First, scan over all of the existing entries in the DB and see if
                // any of them are missing from the filesystem.  This will also
                // mark any that are missing or have reappeared and are no longer
                // missing.
                myBagService.scanDatabaseBags(existingBagPaths, missingBagMd5sums);
                myLogger.debug(existingBagPaths.size() + " bags exist in the database and are not missing.");
                myLogger.debug(missingBagMd5sums.size() + " in the DB are missing on disk.");

                // Next, go over all the files on the filesystem.  Calculating individual
                // md5sums is CPU-intensive, so we might as well do multiple bags in parallel.
                for (final File file : bagFiles) {
                    bagService.execute(() -> {
                        try {
                            myBagService.updateBagFile(file,
                                                       existingBagPaths,
                                                       missingBagMd5sums,
                                                       forceUpdate);
                        }
                        catch (RuntimeException e) {
                            reportStatus(Status.State.ERROR,
                                         "Error checking bag file: " + e.getLocalizedMessage());
                            myLogger.error("Unexpected error updating bag file:", e);
                        }
                    });
                }
                bagService.shutdown();
                bagService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

                myBagService.markMissingBags(missingBagMd5sums.values());

                if (myConfigService.getConfiguration().getRemoveOnDeletion()) {
                    myBagService.removeMissingBags();
                }
            }
            catch (RuntimeException e) {
                String error = "Unexpected exception when checking bag files: ";
                myLogger.warn(error, e);
                reportStatus(Status.State.ERROR, error + e.getLocalizedMessage());
            }
            catch (InterruptedException e) {
                bagService.shutdownNow();
                String error = "Interrupted while waiting for bag updates to complete.";
                myLogger.warn(error, e);
                reportStatus(Status.State.ERROR, error);
            }

            myLogger.debug("Done checking bag files.");
            reportStatus(Status.State.IDLE, "Done checking bag files.");
        }
    }
}
