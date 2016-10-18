/**
 * Copyright 2014 AppDynamics
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appdynamics.monitors.sql.config;


import java.util.List;

public class Server {

    private String displayName;
    private String driver;
    private String connectionString;
    private String user;
    private String password;
    private String encryptedPassword;
    private String encryptionKey;
    private String isolationLevel;
    private List<Command> commands;

    public enum IsolationLevel {
        TRANSACTION_READ_UNCOMMITTED(1), TRANSACTION_READ_COMMITTED(2), TRANSACTION_REPEATABLE_READ(4), TRANSACTION_SERIALIZABLE(8);

        int isolationLevel;

        IsolationLevel(int isolationLevel) {
            this.isolationLevel = isolationLevel;
        }

        public static int getIsolationLevel(String level) {

            int isolationLevelForString = IsolationLevel.TRANSACTION_READ_UNCOMMITTED.isolationLevel;
            for (IsolationLevel txIsolationLevel : IsolationLevel.values()) {
                if (txIsolationLevel.name().equals(level)) {
                    isolationLevelForString = txIsolationLevel.isolationLevel;
                    break;
                }
            }
            return isolationLevelForString;
        }

    }


    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String getIsolationLevel() {
        return isolationLevel;
    }

    public void setIsolationLevel(String isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public List<Command> getCommands() {
        return commands;
    }

    public void setCommands(List<Command> commands) {
        this.commands = commands;
    }
}

