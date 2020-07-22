SQL Monitoring Extension
====================================

## Use Case 
This extension can be used to query an ANSI SQL compliant database and the resulting values can 
be used as metrics on AppDynamics.
The connection to the database is established through a JDBC connect 
and you will have to use a "connector" JDBC driver jar file in order to have the 
extension connect and query the database.

The metrics reported by the extension can be modified as per the user's requirements.
 This extension can be used to query and pull metrics from any SQL based database.
 
 

## Prerequisites 
This extension requires that the user provide their own Jar file in order to connect to the Database. 

In order to use this extension, you do need a [Standalone JAVA Machine Agent](https://docs.appdynamics.com/display/PRO44/Java+Agent) or [SIM Agent](https://docs.appdynamics.com/display/PRO44/Server+Visibility). 
For more details on downloading these products, please visit [download.appdynamics.com](https://download.appdynamics.com/).

This is very essential in order to establish a connection with the Database to get the metrics.
The extension needs to be able to connect to the database in order to collect and send metrics. 
To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.



## Installation 

1. To build from the source, run "mvn clean install" and find the SQLMonitor.zip file in the "target" folder.
   You can also download the SQLMonitor.zip from [AppDynamics Exchange][].
2. Unzip as "SQLMonitor" and copy the **"SQLMonitor"** directory to `<MACHINE_AGENT_HOME>/monitors`.


**Note:** Please place the extension in the **"monitors"** directory of your **Machine Agent** installation 
directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.
    



## Configuration ##

### JDBC JAR 

**Note:** You will need to provide your own **JDBC** driver for the database you want to connect to. Put the driver JAR file in the same directory and add it to the classpath element in the
monitor.xml file.!

```
<java-task>
    <!-- Use regular classpath foo.jar;bar.jar -->
    <!-- append JDBC driver jar -->
    <classpath>sql-monitoring-extension.jar;Jar-File-For_Your-DB.jar</classpath>
    <impl-class>com.appdynamics.extensions.sql.SQLMonitor</impl-class>
</java-task>
```
3. Edit the config.yaml file. An example config.yaml file follows these installation instructions.
4. Configure the path to the config.yaml file by editing the **task-argments** in the monitor.xml file.
```
    <task-arguments>
        <!-- config file-->
           <argument name="config-file" is-required="true" default-value="monitors/SQLMonitor/config.yml" />
         ....
    </task-arguments>

```
5. Restart the Machine Agent.

### config.yml

**Note** : Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a yaml validator http://yamllint.com/

You will have to Configure the SQL server instances by editing the config.yaml file in `<MACHINE_AGENT_HOME>/monitors/SQLMonitor/`. 
The information provided in this file will be used to connect and query the database. 
You can find a sample config.yaml file below.

```
# Make sure the metric prefix ends with a |
#This will create this metric in all the tiers, under this path.
#metricPrefix: "Custom Metrics|SQL|"
#This will create it in specific Tier. Replace <ComponentID> with TierID
metricPrefix: "Server|Component:<ComponentID>|Custom Metrics|SQL|"


dbServers:
    - displayName: "Instance1"
      connectionUrl: ""
      driver: ""

      connectionProperties:
        - user: ""
        - password: ""

      #Needs to be used in conjunction with `encryptionKey`. Please read the extension documentation to generate encrypted password
      #encryptedPassword: ""

      #Needs to be used in conjunction with `encryptedPassword`. Please read the extension documentation to generate encrypted password
      #encryptionKey: "welcome"

      # Replaces characters in metric name with the specified characters.
      # "replace" takes any regular expression
      # "replaceWith" takes the string to replace the matched characters

      metricCharacterReplacer:
        - replace: "%"
          replaceWith: ""
        - replace: ","
          replaceWith: "-"


      queries:
        - displayName: "Active Events"
          queryStmt: "Select NODE_NAME, EVENT_CODE, EVENT_ID, EVENT_POSTED_COUNT from Active_events"
          columns:
            - name: "NODE_NAME"
              type: "metricPathName"

            - name: "EVENT_ID"
              type: "metricPathName"

            - name: "EVENT_CODE"
              type: "metricValue"

            - name: "EVENT_POSTED_COUNT"
              type: "metricValue"

        - displayName: "TRANSACTION DATABASE"
          queryStmt: "SELECT TARGET_BOX, REACH_DURATION, ROUTER_DURATION FROM ASG_TRANSACTIONS WHERE TARGET_BOX IN ('target1','target2','target3','target4','target5')"
          columns:
            - name: "TARGET_BOX"
              type: "metricPathName"

            - name: "REACH_DURATION"
              type: "metricValue"

            - name: "ROUTER_DURATION"
              type: "metricValue"

        - displayName: "Node Status"
          queryStmt: "Select NODE_NAME, NODE_STATE from NODE_STATES"
          columns:
            - name: "NODE_NAME"
              type: "metricPathName"

            - name: "NODE_STATE"
              type: "metricValue"
              properties:
                convert:
                  "INITIALIZING" : 0
                  "UP" : 1
                  "DOWN" : 2
                  "READY" : 3
                  "UNSAFE" : 4
                  "SHUTDOWN" : 5
                  "RECOVERING" : 6





numberOfThreads: 5

#Run it as a scheduled task instead of running every minute.
#If you want to run this every minute, comment this out
#taskSchedule:
  #numberOfThreads: 1
  #taskDelaySeconds: 120

```

### How to Connect to your Database with the extension 
Lets take a look at some sample connection information: 
```
dbServers:
    - displayName: "Instance1"
      connectionUrl: "jdbc:sqlserver://192.168.57.101:1433;user=bhuv;password=12345;databaseName=frb-test;"
      driver: "com.microsoft.sqlserver.jdbc.SQLServerDriver"

#      connectionProperties:
#        - user: ""
#        - password: ""

```
In order to connect to any database, you will have to provide a connectionUrl. 
In the example above we see that the extension is connected to the "sqlserver"(listed in the config) using the connectionUrl. 
In this case we are also providing the username, password and the databaseName in the same connectionUrl 
and therefore the "connectionProperties" and the fields under it, "user" and "password", are commented out. 
You have to make sure that if you are not sending any connectionProperties to create a 
connection, then you should comment the whole thing out just like in the example. 

As this may not be the same for other types of SQL based systems, lets take
 a look at another way you can connect to the database.
In this case we do need to provide properties such as a username and a password and 
therefore we uncomment those lines and update them with valid information.

```
dbServers:
    - displayName: "Instance2"
      connectionUrl: "jdbc:vertica://192.168.57.102:5433/VMart"
      driver: "com.vertica.jdbc.Driver"

      connectionProperties:
        - user: "dbadmin"
        - password: "password"

```
In this case we do add the Database Name as the last part of the connectionUrl **(VMart)** but all other properties like the **username** and **password** are provided as **connectionProperties**. 
You will have to confirm how your database takes in the login information and based on that provide the information in your config.yaml in order to successfully establish a connection.


### Explanation of the type of queries that are supported with this extension 
Only queries that start with **SELECT** are allowed! Your query should only return one row at a time. 

It is suggested that you only  return one row at a time because if it returns a full table with enormous amount of data, it may overwhelm the system and it may take a very long time to fetch that data.  

The extension does support getting values from multiple columns at once but it can only pull the metrics from the latest value from the row returned.

The name of the metric displayed on the **Metric Browser** will be the "name" value that you specify in the config.yml for that metric. 
Looking at the following sample query : 

```
 queries:
   - displayName: "Active Events"
     queryStmt: "Select NODE_NAME, EVENT_CODE, EVENT_ID, EVENT_POSTED_COUNT from Active_events"
     columns:
       - name: "NODE_NAME"
         type: "metricPathName"

       - name: "EVENT_ID"
         type: "metricPathName"

       - name: "EVENT_CODE"
         type: "metricValue"

       - name: "EVENT_POSTED_COUNT"
         type: "metricValue"

```

1. **queries** : You can add multiple queries under this field. 
    1. **displayName** : The name you would like to give to the metrics produced by this query. 
    2. **queryStmt** : This will be your SQL Query that will be used to query the database.
    3. **columns**: Under this field you will have to list all the columns that you are trying to get values from.
        1. **name** : The name of the column you would like to see on the metric browser.
        2. **type** : This value will define if the value returned from the column will be used for the name of the metric or if it is going to be the value of the metric.
            1. **metricPathName** : If you select this, this value will be added to the metric path for the metric.
            2. **metricValue** : If you select this, then the value returned will become your metric value that will correspond to the name you specified above.
            
For the query listed above, there will be two metrics returned as we have two columns of type "metricValue".
The metric path for them will be : 
1. Custom Metrics|SQL|Instance1|Active Events|NODE_NAME|EVENT_ID|EVENT_CODE
2. Custom Metrics|SQL|Instance1|Active Events|NODE_NAME|EVENT_ID|EVENT_POSTED_COUNT

Lets look at another query.
```
           - displayName: "Node Status"
             queryStmt: "Select NODE_NAME, NODE_STATE from NODE_STATES"
             columns:
               - name: "NODE_NAME"
                 type: "metricPathName"
   
               - name: "NODE_STATE"
                 type: "metricValue"
                 properties:
                   convert:
                     "INITIALIZING" : 0
                     "UP" : 1
                     "DOWN" : 2
                     "READY" : 3
                     "UNSAFE" : 4
                     "SHUTDOWN" : 5
                     "RECOVERING" : 6

```
Lets say if your query returns a text value, but you would still like to see it in the metric browser. 
In order to make that happen, you could use the **"convert"** property and assign each value a number. 
The extension will automatically convert the text value to the corresponding number.

**NOTE:** In order to use this feature, please make sure that the value that is being returned is EXACTLY the same as you have listed in the config.yaml, otherwise the extension will throw an error.
 
## Credentials Encryption
Please visit [this](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) page to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following [document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130) for how to use the Extensions WorkBench

## Troubleshooting
Please follow the steps listed in the [extensions troubleshooting document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension. If these don't solve your issue, please follow the last step on the troubleshooting-document to contact the support team.

## Support Tickets
If after going through the Troubleshooting Document you have not been able to get your extension working, please file a ticket and add the following information.

Please provide the following in order for us to assist you better.  

1. Stop the running machine agent .
2. Delete all existing logs under <MachineAgent>/logs .
3. Please enable debug logging by editing the file <MachineAgent>/conf/logging/log4j.xml. Change the level value of the following <logger> elements to debug. 
   ```
   <logger name="com.singularity">
   <logger name="com.appdynamics">
     ```
4. Start the machine agent and please let it run for 10 mins. Then zip and upload all the logs in the directory <MachineAgent>/logs/*.
5. Attach the zipped <MachineAgent>/conf/* directory here.
 6. Attach the zipped <MachineAgent>/monitors/<ExtensionMonitor> directory here .

For any support related questions, you can also contact help@appdynamics.com.

## Contributing
Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/vertica-monitoring-extension).

## Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |2.0.1       |
|Controller Compatibility  |3.7 or Later|
|Product Tested On         |MySql, SQLServer 4.1,4.2, Vertica|
|Last Update               |07/22/2020 |
|List of Changes           |[Change log](https://github.com/Appdynamics/SQLMonitor/blob/master/CHANGELOG.md) |



[GitHub]: https://github.com/Appdynamics/SQLMonitor
[AppDynamics Exchange]: https://www.appdynamics.com/community/exchange/extension/sqlmonitor/
[AppDynamics Center of Excellence]: mailto:help@appdynamics.com
[troubleshooting-document]: https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695