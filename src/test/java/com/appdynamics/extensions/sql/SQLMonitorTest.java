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
    public void testSQLMonitoringExtension () throws TaskExecutionException{
        Map<String, String> taskArgs = new HashMap<String, String>();
        taskArgs.put(CONFIG_ARG, "/Users/bhuvnesh.kumar/repos/appdynamics/extensions/vertica-monitoring-extension/src/test/resources/conf/config_generic.yml");
        testClass.execute(taskArgs, null);

    }

//    @Test(expected = TaskExecutionException.class)
//    public void testWithNullArgsShouldResultInException() throws Exception {
//        testClass.execute(null, null);
//
//    }
//
//    @Test(expected = TaskExecutionException.class)
//    public void testWithNoValidLogConfigResultInException() throws Exception {
//        Map<String, String> args = Maps.newHashMap();
//        args.put("config-file", "src/test/resources/conf/invalidConfig.yml");
//
//        testClass.execute(args, null);
//    }

}
