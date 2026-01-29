/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.sql;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import static com.appdynamics.extensions.sql.Constant.METRIC_PREFIX;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.util.CryptoUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public class SQLMonitor extends ABaseMonitor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(SQLMonitor.class);
    private long previousTimestamp = 0;
    private long currentTimestamp = System.currentTimeMillis();

    @Override
    protected String getDefaultMetricPrefix() {
        return METRIC_PREFIX;
    }

    @Override
    public String getMonitorName() {
        return "SQL Monitor";
    }

    @Override
    protected void doRun(TasksExecutionServiceProvider serviceProvider) {

        List<Map<String, ?>> servers = (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("dbServers");

        previousTimestamp = currentTimestamp;
        currentTimestamp = System.currentTimeMillis();
        if (previousTimestamp != 0) {
            for (Map<String, ?> server : servers) {
                try {
                    SQLMonitorTask task = createTask(server, serviceProvider);
                    serviceProvider.submit((String) server.get("displayName"), task);
                } catch (Exception e) {
                    logger.error("Error while creating task for {}", Util.convertToString(server.get("displayName"), ""),e);
                }
            }
        }
    }

    @Override
    protected List<Map<String, ?>> getServers() {
        return (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("dbServers");
    }

    private SQLMonitorTask createTask(Map<String, ?> server, TasksExecutionServiceProvider serviceProvider) {
        String connUrl = createConnectionUrl(server);

        //Fix for ACE-1001
        if(Strings.isNullOrEmpty(serverName(server))){
            throw new IllegalArgumentException("The 'displayName' field under the 'dbServers' section in config.yml is not initialised");
        }
        if(Strings.isNullOrEmpty(createConnectionUrl(server))){
            throw new IllegalArgumentException("The 'connectionUrl' field under the 'dbServers' section in config.yml is not initialised");
        }
        if(Strings.isNullOrEmpty(driverName(server))){
            throw new IllegalArgumentException("The 'driver' field under the 'dbServers' section in config.yml is not initialised");
        }

        //AssertUtils.assertNotNull(serverName(server), "The 'displayName' field under the 'dbServers' section in config.yml is not initialised");
        //AssertUtils.assertNotNull(createConnectionUrl(server), "The 'connectionUrl' field under the 'dbServers' section in config.yml is not initialised");
        //AssertUtils.assertNotNull(driverName(server), "The 'driver' field under the 'dbServers' section in config.yml is not initialised");

        Map<String, String> connectionProperties = getConnectionProperties(server);
        JDBCConnectionAdapter jdbcAdapter = JDBCConnectionAdapter.create(connUrl, connectionProperties);
        boolean windowsAuthentication = (System.getProperty("os.name").toLowerCase().contains("win") && connUrl.contains("integratedSecurity"));
        logger.info("setting the connUrl==============================>"+ connUrl);
        if(windowsAuthentication) {
            jdbcAdapter.setEnableWindowsAuthentication(windowsAuthentication);
            jdbcAdapter.setWinLibPath(getWinLibPath(server));
        }


        logger.debug("Task Created for "+server.get("displayName"));

        return new SQLMonitorTask.Builder()
                .metricWriter(serviceProvider.getMetricWriteHelper())
                .metricPrefix(getContextConfiguration().getMetricPrefix())
                .jdbcAdapter(jdbcAdapter)
                .previousTimestamp(previousTimestamp)
                .currentTimestamp(currentTimestamp)
                .server(server).build();

    }

    private String serverName(Map<String, ?> server) {
        String name = Util.convertToString(server.get("displayName"), "");
        return name;
    }

    private String driverName(Map<String, ?> server) {
        String name = Util.convertToString(server.get("driver"), "");
        return name;
    }

    private String createConnectionUrl(Map<String, ?> server) {
        String url = Util.convertToString(server.get("connectionUrl"), "");
        return url;
    }

    private String getWinLibPath(Map<String, ?> server) {
        String url = Util.convertToString(server.get("driverDllFolderPath"), "");
        return url;
    }

    private Map<String, String> getConnectionProperties(Map<String, ?> server) {
        Map<String, String> connectionProperties = (Map<String, String>) server.get("connectionProperties");
        String password = connectionProperties.get("password");
        if (Strings.isNullOrEmpty(password))
            password = getPassword(connectionProperties);

        connectionProperties.put("password", password);
        return connectionProperties;
    }


    private String getPassword(Map<String, String> server) {
        String encryptedPassword =  server.get(Constant.ENCRYPTED_PASSWORD);
        Map<String, ?> configMap = getContextConfiguration().getConfigYml();
        String encryptionKey = (String) configMap.get(Constant.ENCRYPTION_KEY);
        if (!Strings.isNullOrEmpty(encryptedPassword)) {
            Map<String, String> cryptoMap = Maps.newHashMap();
            cryptoMap.put("encryptedPassword", encryptedPassword);
            cryptoMap.put("encryptionKey", encryptionKey);
            logger.debug("Decrypting the encrypted password........");
            return CryptoUtils.getPassword(cryptoMap);
        }
        return "";
    }

//    public static void main(String[] args) throws TaskExecutionException {
//
//        SQLMonitor monitor = new SQLMonitor();
//
//        final Map<String, String> taskArgs = new HashMap<String, String>();
//
//        taskArgs.put("config-file", "src/main/resources/conf/config.yml");
//        monitor.execute(taskArgs, null);
//    }

}