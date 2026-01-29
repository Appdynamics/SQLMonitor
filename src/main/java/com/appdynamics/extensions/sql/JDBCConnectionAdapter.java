/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.sql;


import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.google.common.base.Strings;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;


public class JDBCConnectionAdapter {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(JDBCConnectionAdapter.class);
    private final String connUrl;
    private final Map<String, String> connectionProperties;

    private String winLibPath;

    private boolean enableWindowsAuthentication;


    private JDBCConnectionAdapter(String connStr, Map<String, String> connectionProperties) {
        this.connUrl = connStr;
        this.connectionProperties = connectionProperties;

    }

    static JDBCConnectionAdapter create(String connUrl, Map<String, String> connectionProperties) {
        return new JDBCConnectionAdapter(connUrl, connectionProperties);
    }

    Connection open(String driver) throws SQLException, ClassNotFoundException {
        Connection connection;
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc");
        log.setLevel(Level.FINE);
        Class.forName(driver);
        logger.info("driver====>"+driver);
        logger.info("Passed all checks for properties and attempting to connect to =====>"+ connUrl);
        long timestamp1 = System.currentTimeMillis();
        if(enableWindowsAuthentication){
            System.setProperty("java.library.path", winLibPath);
            logger.info("setting the libreary path :"+ winLibPath);
            connection = DriverManager.getConnection(connUrl);
        } else {
            Properties properties = new Properties();
            if (connectionProperties != null) {
                for (String key : connectionProperties.keySet()) {
                    if (!Strings.isNullOrEmpty(connectionProperties.get(key)))
                        properties.put(key, connectionProperties.get(key));
                }
            }
            connection = DriverManager.getConnection(connUrl, properties);
        }
        long timestamp2 = System.currentTimeMillis();
        logger.debug("Connection received in JDBC ConnectionAdapter in :"+ (timestamp2-timestamp1)+ " ms");

        return connection;
    }

    ResultSet queryDatabase(String query, Statement stmt) throws SQLException {
        return stmt.executeQuery(query);
    }

    void closeStatement(Statement statement) throws SQLException {
        statement.close();
    }

    void closeConnection(Connection connection) throws SQLException {
        connection.close();
    }

    public void setWinLibPath(String winLibPath) {
        this.winLibPath = winLibPath;
    }

    public void setEnableWindowsAuthentication(boolean enableWindowsAuthentication) {
        this.enableWindowsAuthentication = enableWindowsAuthentication;
    }
}