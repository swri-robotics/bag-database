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

package com.github.swrirobotics.db;

import com.github.swrirobotics.config.WebAppConfigurationAware;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SpatialDatabaseTest extends WebAppConfigurationAware {
    @Autowired
    private DataSource myDataSource;

    @Test
    public void testDatabase() throws SQLException {
        // This test simply verifies that spatial extensions are enabled on
        // our database; it creats a table with a geometry column, inserts
        // a few values, and then verifies that it can select them.
        boolean foundRow = false;
        try (Connection conn = myDataSource.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE GEO_TABLE(GID SERIAL, THE_GEOM GEOMETRY);\n" +
                           "INSERT INTO GEO_TABLE(THE_GEOM) VALUES\n" +
                           "    ('POINT(500 505)'),\n" +
                           "    ('LINESTRING(550 551, 525 512, 565 566)'),\n" +
                           "    ('POLYGON ((550 521, 580 540, 570 564, 512 566, 550 521))');\n" +
                           "CREATE SPATIAL INDEX GEO_TABLE_SPATIAL_INDEX ON GEO_TABLE(THE_GEOM);");
            }
            try (Statement st = conn.createStatement()) {
                String query = "SELECT * FROM GEO_TABLE\n" +
                               "WHERE THE_GEOM && 'POLYGON ((490 490, 536 490, 536 515, 490 515, 490 490))';";
                try (ResultSet rs = st.executeQuery(query)) {
                    if (rs.next()) {
                        foundRow = true;
                    }
                }
            }
        }

        Assert.assertTrue("Geometry row was not found.", foundRow);
    }
}
