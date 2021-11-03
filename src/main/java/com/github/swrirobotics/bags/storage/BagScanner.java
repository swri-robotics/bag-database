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

package com.github.swrirobotics.bags.storage;


import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.NonexistentBagException;
import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.Float64Type;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.persistence.Bag;
import com.github.swrirobotics.persistence.BagRepository;
import com.github.swrirobotics.remote.GeocodingService;
import com.github.swrirobotics.status.Status;
import com.github.swrirobotics.status.StatusProvider;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Profile("default")
// This class doesn't directly access it, but we need to depend on the Liquibase
// bean to ensure the database is configured before our @PostContruct runs.
@DependsOn("liquibase")
public class BagScanner extends StatusProvider implements BagStorageChangeListener {
    private final ConfigService myConfigService;
    private final BagRepository myBagRepo;
    private final BagService myBagService;
    private final GeocodingService myGeocodingService;
    private final TransactionTemplate transactionTemplate;

    private final ExecutorService myExecutor = Executors.newSingleThreadExecutor();

    private final Logger myLogger = LoggerFactory.getLogger(BagScanner.class);

    private final Map<String, BagStorage> myBagStorages = Maps.newHashMap();

    public BagScanner(ConfigService myConfigService, BagRepository myBagRepo, BagService myBagService,
                      GeocodingService myGeocodingService,
                      PlatformTransactionManager transactionManager) {
        this.myConfigService = myConfigService;
        this.myBagRepo = myBagRepo;
        this.myBagService = myBagService;
        this.myGeocodingService = myGeocodingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

        Collection<BagStorage> storages = myBagService.getBagStorages();

        myBagService.deleteUnownedBags();

        for (BagStorage storage : storages) {
            myBagStorages.put(storage.getStorageId(), storage);
            storage.addChangeListener(this);
            scanStorage(storage, false);
        }
    }

    /**
     * Stops the file watcher that is watching the current bag directory,
     * then reinitializes everything.
     */
    public void reset() {
        for (BagStorage storage : myBagStorages.values()) {
            storage.stop();
            storage.removeChangeListener(this);
        }
        myBagStorages.clear();

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
            Bag bag = myBagRepo.findById(bagId).orElseThrow();
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

            Bag bag = myBagRepo.findById(bagId).orElseThrow();
            if (bag.getVehicle() == null || bag.getVehicle().isEmpty()) {
                try (BagWrapper wrapper = myBagService.getBagWrapper(bag)) {
                    BagFile bagFile = wrapper.getBagFile();
                    String name = myBagService.getVehicleName(bagFile);
                    if (name != null) {
                        myLogger.debug("Setting vehicle name for " +
                                       bag.getFilename() + " to " + name);
                        bag.setVehicle(name);
                        myBagRepo.save(bag);
                    }
                }
                catch (BagReaderException | IOException e) {
                    reportStatus(Status.State.ERROR,
                                 "Unable to get vehicle name from bag file " +
                                 bag.getFilename() + ": " + e.getLocalizedMessage());
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
            Bag bag = myBagRepo.findById(bagId).orElseThrow();
            try (BagWrapper wrapper = myBagService.getBagWrapper(bag)) {
                BagFile bagFile = wrapper.getBagFile();
                myBagService.addTagsToBag(bagFile,bag);
            }
            catch (BagReaderException e) {
                reportStatus(Status.State.ERROR,
                        "Unable to get tags from bag file " +
                                bag.getFilename() + ": " + e.getLocalizedMessage());
                reportStatus(Status.State.WORKING,
                        "Updating tags for all bag files.");
            }
            catch (IOException e) {
                reportStatus(Status.State.ERROR, "Unable to find bag " + bagId);
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
            try {
                myBagService.updateGpsPositionsForBagId(bagId);
            }
            catch (NonexistentBagException e) {
                reportStatus(Status.State.ERROR,
                    "Unable to get GPS coordinates from bag " +
                        bagId + ": " + e.getLocalizedMessage());
            }
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
            Bag bag = myBagRepo.findById(bagId).orElseThrow();

            if (bag.getLatitudeDeg() == null ||
                    bag.getLongitudeDeg() == null ||
                    (Math.abs(bag.getLatitudeDeg()) < 0.0001 &&
                            Math.abs(bag.getLongitudeDeg()) < 0.0001)) {
                try (BagWrapper wrapper = myBagService.getBagWrapper(bag)) {
                    BagFile bagFile = wrapper.getBagFile();
                    MessageType mt = bagFile.getFirstMessageOfType("gps_common/GPSFix");
                    if (mt == null) {
                        mt = bagFile.getFirstMessageOfType("sensor_msgs/NavSatFix");
                    }
                    if (mt == null) {
                        mt = bagFile.getFirstMessageOfType("marti_gps_common/GPSFix");
                    }
                    if (mt == null) {
                        myLogger.debug("No GPSFix or NavSatFix message found in bag " + bag.getFilename() + ".");
                    }
                    else {
                        bag.setCoordinate(myBagService.makePoint(
                                mt.<Float64Type>getField("latitude").getValue(),
                                mt.<Float64Type>getField("longitude").getValue()));
                        myLogger.debug("Setting lat/lon for " + bag.getFilename() + " to: " +
                                               bag.getLatitudeDeg() + " / " + bag.getLongitudeDeg());
                        myBagRepo.save(bag);
                    }
                }
                catch (BagReaderException | UninitializedFieldException | IOException e) {
                    reportStatus(Status.State.ERROR,
                                 "Unable to get GPS info from bag file " + bag.getFilename() + ": "
                                     + e.getLocalizedMessage());
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

    public void scanAllStorages(boolean forceUpdate) {
        myLogger.info("Scanning all storage backends: " + Joiner.on(',').join(myBagStorages.keySet()));
        for (BagStorage storage : myBagStorages.values()) {
            scanStorage(storage, forceUpdate);
        }
    }

    @Override
    public void bagStorageChanged(BagStorageChangeEvent event) {
        myLogger.info("Bag storage change detected.");
        scanStorage(event.getStorage(), false);
    }

    @Override
    protected String getStatusProviderName() {
        return "Bag Scanner";
    }

    public void scanStorage(BagStorage storage, boolean forceUpdate) {
        String msg = "Scanning for new bag files for storage [" + storage.getStorageId() + "]";
        reportStatus(Status.State.WORKING, msg);
        myLogger.info(msg);
        try {
            myExecutor.execute(() -> {
                // Scan for new bags and add them to the database or update any that were missing
                TransactionCallback<Object> cb = transactionStatus -> {
                    storage.updateBagExistence();
                    storage.updateBags(forceUpdate);
                    return null;
                };
                transactionTemplate.execute(cb);

                // After that's done, if we're configured to remove missing bags, double-check to see whether
                // they exist now, and if not, remove them.
                if (myConfigService.getConfiguration().getRemoveOnDeletion()) {
                    cb = transactionStatus -> {
                        storage.updateBagExistence();
                        myBagService.removeMissingBags();
                        return null;
                    };
                    transactionTemplate.execute(cb);
                }
            });
        }
        catch (RuntimeException e) {
            String error = "Unexpected exception when checking bag files: ";
            myLogger.warn(error, e);
            reportStatus(Status.State.ERROR, error + e.getLocalizedMessage());
        }

        myLogger.debug("Done checking bag files.");
        reportStatus(Status.State.IDLE, "Done checking bag files.");
    }
}
