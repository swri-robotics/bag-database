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

import com.github.swrirobotics.BagApplication;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.hibernate.cfg.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
@EnableTransactionManagement(mode= AdviceMode.ASPECTJ)
@EnableJpaRepositories(basePackageClasses = BagApplication.class,
        transactionManagerRef = "annotationDrivenTransactionManager")
@EnableJdbcHttpSession
public class JpaConfig implements TransactionManagementConfigurer {
    @Autowired(required = false)
    private DataSourceProperties properties;

    private Logger myLogger = LoggerFactory.getLogger(JpaConfig.class);

    @Bean
    @Profile("!test")
    public DataSource dataSource() {
        myLogger.info("JDBC driver: " + properties.getDriver());
        myLogger.info("JDBC URL: " + properties.getUrl());
        myLogger.info("JDBC Username: " + properties.getUsername());

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(properties.getDriver());
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("initializationFailTimeout", "10000");

        if (properties.getDriver().equals("org.postgresql.Driver")) {
            System.setProperty("hibernate.dialect", "org.hibernate.spatial.dialect.postgis.PostgisDialect");

            return new HikariDataSource(config);
        }
        else {
            System.setProperty("hibernate.dialect", "org.hibernate.spatial.dialect.h2geodb.GeoDBDialect");

            try {
                return H2GISDBFactory.createDataSource("testdb", true);
            }
            catch (SQLException e) {
                myLogger.error("Unable to open spatial H2 database.");
                return null;
            }
        }
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        myLogger.info("entityManagerFactory");
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(dataSource());
        entityManagerFactoryBean.setPackagesToScan("com.github.swrirobotics");
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProperties = new Properties();
        // Disable HBM2DDL; we use Liquibase to create our database
        jpaProperties.put(Environment.HBM2DDL_AUTO, "");
        // Set a large batch size for better performance over slow network links
        jpaProperties.put(Environment.STATEMENT_BATCH_SIZE, "100");
        jpaProperties.put(Environment.ORDER_INSERTS, "true");
        jpaProperties.put(Environment.ORDER_UPDATES, "true");
        jpaProperties.put(Environment.USE_NEW_ID_GENERATOR_MAPPINGS, "true");
        entityManagerFactoryBean.setJpaProperties(jpaProperties);

        return entityManagerFactoryBean;
    }

    @Bean
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        return new JpaTransactionManager();
    }
}
