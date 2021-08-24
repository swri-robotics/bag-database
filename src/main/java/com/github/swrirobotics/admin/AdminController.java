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

package com.github.swrirobotics.admin;

import com.github.swrirobotics.account.Account;
import com.github.swrirobotics.account.AccountRepository;
import com.github.swrirobotics.bags.BagService;
import com.github.swrirobotics.bags.storage.BagScanner;
import com.github.swrirobotics.config.ConfigService;
import com.github.swrirobotics.support.web.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Controller
@RequestMapping("admin")
@Secured("ROLE_ADMIN")
// This class doesn't directly access it, but we need to depend on the Liquibase
// bean to ensure the database is configured before our @PostContruct runs.
@DependsOn("liquibase")
public class AdminController {
    @Autowired(required = false)
    private BagScanner myBagScanner;
    @Autowired
    private BagService myBagService;
    @Autowired
    private AccountRepository myAccountRepository;
    @Autowired
    private ConfigService myConfigService;
    @Autowired
    private PlatformTransactionManager myTxManager;

    private static final Logger myLogger = LoggerFactory.getLogger(AdminController.class);

    @PostConstruct
    public void checkAdminAccount() {
        // Spring's Transaction Manager may not have fully initialized at the point this
        // code runs, so we need to create our own.
        TransactionTemplate txTemplate = new TransactionTemplate(myTxManager);
        txTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                Account account = myAccountRepository.findByEmail("admin");
                Configuration config = myConfigService.getConfiguration();
                if (config != null ) {
                    String password = config.getAdminPassword();
                    if (account == null || (password != null && !password.isEmpty())) {
                        if (password != null && !password.isEmpty()) {
                            myLogger.info("Setting admin password from config file.");
                            config.setAdminPassword("");
                            try {
                                myConfigService.setConfiguration(config);
                            }
                            catch (IOException e) {
                                myLogger.error("Error setting configuration", e);
                            }
                        }
                        else {
                            password = RandomStringUtils.randomAlphanumeric(20);
                        }

                        if (account == null) {
                            myLogger.info("No admin account found; creating a default one.");
                            account = new Account("admin", password, "ROLE_ADMIN");
                            myLogger.info("*** The default admin password is " + password + " ***");
                        }
                        else {
                            account.setPassword(password);
                        }
                        myAccountRepository.save(account);
                    }
                }
            }
        });
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public String getAdminPage(Model model) {
        myLogger.info("getAdminPage");
        return "admin/admin";
    }

    @RequestMapping(value = "/updateLatLons", method = RequestMethod.POST)
    @ResponseBody
    public void updateLatLons() {
        myLogger.info("updateLatLons");
        myBagScanner.updateAllLatLons();
    }

    @RequestMapping(value = "/updateLocations", method = RequestMethod.POST)
    @ResponseBody
    public void updateLocations() {
        myLogger.info("updateLocations");
        myBagScanner.updateAllLocations();
    }

    @RequestMapping(value = "/updateGpsPaths", method = RequestMethod.POST)
    @ResponseBody
    public void updateGpsPaths() {
        myLogger.info("updateGpsPaths");
        myBagScanner.updateAllGpsPaths();
    }

    @RequestMapping(value = "/updateVehicleNames", method = RequestMethod.POST)
    @ResponseBody
    public void updateVehicleNames() {
        myLogger.info("updateVehicleNames");
        myBagScanner.updateAllVehicleNames();
    }

    @RequestMapping(value = "/updateTags", method = RequestMethod.POST)
    @ResponseBody
    public void updateTags() {
        myLogger.info("updateTags");
        myBagScanner.updateAllTags();
    }

    @RequestMapping(value = "/removeMissingBags", method = RequestMethod.POST)
    @ResponseBody
    public void removeMissingBags() {
        myLogger.info("removeMissingBags");
        myBagService.removeMissingBags();
    }

    @RequestMapping(value = "/removeDuplicates", method = RequestMethod.POST)
    @ResponseBody
    public void removeDuplicates() {
        myLogger.info("removeDuplicates()");
        myBagService.removeDuplicateBags();
    }

    @RequestMapping(value = "/forceScan", method = RequestMethod.POST)
    @ResponseBody
    public void forceScan() {
        myLogger.info("forceScan");
        myBagScanner.scanAllStorages(false);
    }

    @RequestMapping(value = "/forceFullScan", method = RequestMethod.POST)
    @ResponseBody
    public void forceFullScan() {
        myLogger.info("forceFullScan");
        myBagScanner.scanAllStorages(true);
    }

    @Transactional
    public void setAdminPassword(String password) {
        try {
            Account account = myAccountRepository.findByEmail("admin");
            account.setPassword(password);
            myAccountRepository.save(account);
            myLogger.info("Successfully changed admin password.");
        }
        catch (Exception e) {
            myLogger.error("Error changing password", e);
        }
    }

    @RequestMapping(value = "/changePassword", method = RequestMethod.POST)
    @ResponseBody
    public void changePassword(@RequestParam String password) {
        myLogger.info("changePassword");
        setAdminPassword(password);
    }
}
