/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.sql;

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bhuvnesh.kumar on 9/28/17.
 */
public class SQLMonitorTest {

    private static final String CONFIG_ARG = "config-file";

    private SQLMonitor testClass;

    @Before
    public void init() throws Exception {

        testClass = new SQLMonitor();
    }


    @Test
    public void testSQLMonitoringExtension() throws TaskExecutionException {
        Map<String, String> taskArgs = new HashMap<String, String>();
        taskArgs.put(CONFIG_ARG, "/Users/bhuvnesh.kumar/repos/appdynamics/extensions/vertica-monitoring-extension/src/test/resources/conf/config_generic.yml");
        testClass.execute(taskArgs, null);

    }

}
