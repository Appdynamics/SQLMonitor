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

import java.io.IOException;
import java.util.LinkedHashMap;
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

        List<Map<String, String>> servers = (List<Map<String, String>>) getContextConfiguration().getConfigYml().get("dbServers");

        previousTimestamp = currentTimestamp;
        currentTimestamp = System.currentTimeMillis();
        if (previousTimestamp != 0) {
            for (Map<String, String> server : servers) {
                try {
                    SQLMonitorTask task = createTask(server, serviceProvider);
                    serviceProvider.submit(server.get("displayName"), task);
                } catch (IOException e) {
                    logger.error("Cannot construct JDBC uri for {}", Util.convertToString(server.get("displayName"), ""));
                }
            }
        }
    }

    @Override
    protected List<Map<String, ?>> getServers() {
        return (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("dbServers");
    }

    private SQLMonitorTask createTask(Map server, TasksExecutionServiceProvider serviceProvider) throws IOException {
        String connUrl = createConnectionUrl(server);

        AssertUtils.assertNotNull(serverName(server), "The 'displayName' field under the 'dbServers' section in config.yml is not initialised");
        AssertUtils.assertNotNull(createConnectionUrl(server), "The 'connectionUrl' field under the 'dbServers' section in config.yml is not initialised");
        AssertUtils.assertNotNull(driverName(server), "The 'driver' field under the 'dbServers' section in config.yml is not initialised");

        logger.debug("Task Created");
        Map<String, String> connectionProperties = getConnectionProperties(server);
        JDBCConnectionAdapter jdbcAdapter = JDBCConnectionAdapter.create(connUrl, connectionProperties);

        return new SQLMonitorTask.Builder()
                .metricWriter(serviceProvider.getMetricWriteHelper())
                .metricPrefix(getContextConfiguration().getMetricPrefix())
                .jdbcAdapter(jdbcAdapter)
                .previousTimestamp(previousTimestamp)
                .currentTimestamp(currentTimestamp)
                .server(server).build();

    }

    private String serverName(Map server) {
        String name = Util.convertToString(server.get("displayName"), "");
        return name;
    }

    private String driverName(Map server) {
        String name = Util.convertToString(server.get("driver"), "");
        return name;
    }

    private String createConnectionUrl(Map server) {
        String url = Util.convertToString(server.get("connectionUrl"), "");
        return url;
    }

    private Map<String, String> getConnectionProperties(Map server) {
        Map<String, String> connectionProperties = new LinkedHashMap<String, String>();
        List<Map<String, String>> listOfMaps = (List<Map<String, String>>) server.get("connectionProperties");

        if (listOfMaps != null) {
            for (Map amap : listOfMaps) {
                for (Object key : amap.keySet()) {
                    if (key.toString().equals("password")) {
                        String password;

                        if (Strings.isNullOrEmpty((String) amap.get(key))) {
                            password = getPassword(server);
                        } else {
                            password = (String) amap.get(key);
                        }
                        connectionProperties.put((String) key, password);
                    } else {
                        connectionProperties.put((String) key, (String) amap.get(key));
                    }
                }
            }
            return connectionProperties;
        }

        return null;
    }

    private String getPassword(Map server) {
        String password = (String) server.get(Constant.PASSWORD);
        String encryptedPassword = (String) server.get(Constant.ENCRYPTED_PASSWORD);
        Map<String, ?> configMap = getContextConfiguration().getConfigYml();
        String encryptionKey = (String) configMap.get(Constant.ENCRYPTION_KEY);
        if (!Strings.isNullOrEmpty(password)) {
            return password;
        }
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
//        SQLMonitor sqlMonitor = new SQLMonitor();
//        Map<String, String> params = new HashMap<>();
//        params.put("config-file", "/Users/prashant.mehta/dev/SQLMonitor/src/main/resources/conf/config.yml");
//        sqlMonitor.execute(params, null);
//    }

}