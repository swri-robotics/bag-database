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

package com.github.swrirobotics.config;

import com.google.common.collect.Maps;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@Configuration
public class LiquibaseConfig {
    @Autowired
    private DataSource myDataSource;

    private static final Logger myLogger = LoggerFactory.getLogger(LiquibaseConfig.class);

    private void syncIfNecessary() {
        // Prior to version 2.3, the bag database generated its DB tables via
        // the Hibernate mappings.  This was kind of bad.  In version 2.3 it
        // uses Liquibase to handle database schema versioning.
        // This function checks to see if we have an old database that was
        // generated without Liquibase, then sets it up properly if we do.

        try (Connection conn = myDataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            boolean missingChangelog = !doesTableExist(md, "databasechangelog");
            myLogger.debug("databasechangelog table " + (missingChangelog ? "doesn't exist." : "exists."));
            boolean bagTableExists = doesTableExist(md, "bags");
            myLogger.debug("bags table " + (bagTableExists ? "exists." : "doesn't exist."));

            if (missingChangelog && bagTableExists) {
                myLogger.info("Synchronizing existing database with Liquibase schema.");
                Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                Liquibase lb = new Liquibase("db/changelog/db.changelog-1.0.yaml",
                                             new ClassLoaderResourceAccessor(),
                                             db);
                lb.changeLogSync("");
            }
        }
        catch (SQLException | LiquibaseException e) {
            myLogger.error("Error checking database version", e);
        }
    }

    private boolean doesTableExist(DatabaseMetaData md, String tablename) throws SQLException {
        boolean tableExists = false;
        try (ResultSet rs = md.getTables(null, null, tablename.toUpperCase(), null)) {
            if (rs.next()) {
                tableExists = true;
            }
        }
        if (!tableExists) {
            try (ResultSet rs = md.getTables(null, null, tablename.toLowerCase(), null)) {
                if (rs.next()) {
                    tableExists = true;
                }
            }
        }

        return tableExists;
    }

    @Bean
    public SpringLiquibase liquibase() {
        myLogger.info("Initializing Liquibase.");
        syncIfNecessary();

        // If the database is out of date, this will update it to the latest
        // schema.
        SpringLiquibase lb = new SpringLiquibase();
        lb.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        lb.setDataSource(myDataSource);
        Map<String, String> params = Maps.newHashMap();
        params.put("verbose", "true");
        lb.setChangeLogParameters(params);
        lb.setShouldRun(true);

        return lb;
    }
}
