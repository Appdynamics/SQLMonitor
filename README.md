SQL Monitoring Extension
====================================

## Introduction ##

The purpose of this monitor is to use arbitrary queries against a SQL database as metrics
for AppDynamics. The connection to the database is via JDBC.


## Installation ##

1. To build from the source, run "mvn clean install" and find the SQLMonitor.zip file in the "target" folder.
   You can also download the SQLMonitor.zip from [AppDynamics Exchange][].
2. Unzip as "SQLMonitor" and copy the "SQLMonitor" directory to `<MACHINE_AGENT_HOME>/monitors`.

You will need to provide your own JDBC driver for the database you want to connect to.
Put the driver JAR file in the same directory and add it to the classpath element in the
monitor.xml file.!

###Example###
<java-task>
    <!-- Use regular classpath foo.jar;bar.jar -->
    <!-- append JDBC driver jar -->
    <classpath>sql-monitoring-extension.jar:ojdbc6-11.2.0.3.jar</classpath>
    <impl-class>com.appdynamics.monitors.sql.SQLMonitor</impl-class>
</java-task>


##Configuration##


###Note
Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a yaml validator http://yamllint.com/

1. Configure the SQL server instances by editing the config.yaml file in `<MACHINE_AGENT_HOME>/monitors/SQLMonitor/`. Below is the format

   ```
      # Configure the SQLMonitor
      servers:
          - server: "localhost"
            driver: "com.mysql.jdbc.Driver"
            connectionString: "jdbc:mysql://localhost:3388/test"
            user: "root"
            #Provide password or encryptedPassword and encryptionKey. See the documentation to find about password encryption.
            password: "root"

            encryptedPassword:
            encryptionKey:
            #Transaction isolation level to apply on the connection
            #Supported values are TRANSACTION_READ_UNCOMMITTED, TRANSACTION_READ_COMMITTED, TRANSACTION_REPEATABLE_READ and TRANSACTION_SERIALIZABLE
            #Default is TRANSACTION_READ_UNCOMMITTED
            isolationLevel: "TRANSACTION_READ_UNCOMMITTED"


      commands:
         - command: "select value from monitortest where id = 1"
           displayPrefix: "Expedia"
         - command: "select value from monitortest where id = 2"
           displayPrefix: "DerbySoft"

      # Make sure the metric prefix ends with a |

      metricPrefix: "Custom Metrics|SQL|"
```

2. Configure the path to the config.yaml file by editing the <task-arguments> in the monitor.xml file. Below is the sample

     ```
         <task-arguments>
             <!-- config file-->
                <argument name="config-file" is-required="true" default-value="monitors/SQLMonitor/config.yml"     />
              ....
         </task-arguments>
     ```

##Credentials Encryption##

To set an encrypted password in config.yml, follow the steps below:

1. Download the util jar to encrypt the Credentials from [here](https://github.com/Appdynamics/maven-repo/blob/master/releases/com/appdynamics/appd-exts-commons/1.1.2/appd-exts-commons-1.1.2.jar).
2. Run command:

   	```
   	java -cp appd-exts-commons-1.1.2.jar com.appdynamics.extensions.crypto.Encryptor EncryptionKey CredentialToEncrypt

   	For example:
   	java -cp "appd-exts-commons-1.1.2.jar" com.appdynamics.extensions.crypto.Encryptor test password

     ```

3. Set the encryptionKey field in config.yml with the encryption key used, as well as the resulting encrypted password in encryptedPassword fields.


##Metric Queries##

Only queries that start with SELECT are allowed.!
The queries to get the metric values from the database should only return one row and
one column, additional rows and columns will be ignored. The name of the metric will be
the first column name return by the query.!

Example-

```
commands:
   - command: "select foo as foobar from bar where id = 10"
     displayPrefix: "Expedia"
```
For the above query metric path will be "Custom Metrics|SQL|localhost|Expedia|foobar".



## Contributing ##

Always feel free to fork and contribute any changes directly via [GitHub][].

## Community ##

Find out more in the [AppDynamics Exchange][].

## Support ##

For any questions or feature request, please contact [AppDynamics Center of Excellence][].

**Version:** 1.3
**Controller Compatibility:** 3.7 or later
**SQL Version Tested On:** MySQL, 5.5.46

[GitHub]: https://github.com/Appdynamics/SQLMonitor
[AppDynamics Exchange]: https://www.appdynamics.com/community/exchange/extension/sqlmonitor/
[AppDynamics Center of Excellence]: mailto:help@appdynamics.com
