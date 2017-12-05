package com.appdynamics.extensions.sql;


import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import com.google.common.base.Strings;


public class JDBCConnectionAdapter {

    private final String connUrl;
    private final Map<String, String> connectionProperties;



    private JDBCConnectionAdapter(String connStr, Map<String, String> connectionProperties){
        this.connUrl = connStr;
        this.connectionProperties = connectionProperties;

    }

    static JDBCConnectionAdapter create(String connUrl,  Map<String, String> connectionProperties){
        return new JDBCConnectionAdapter(connUrl, connectionProperties);
    }

    Connection open(String driver) throws SQLException, ClassNotFoundException {
        Connection connection;
        //System.out.println()
        Class.forName(driver);

        Properties properties = new Properties();
        properties.put("ReadOnly", "true");

        for(String key: connectionProperties.keySet())
        {
            if(!Strings.isNullOrEmpty(connectionProperties.get(key)))
                properties.put(key, connectionProperties.get(key));
        }


        connection = DriverManager.getConnection(connUrl,properties);
        return connection;
    }

    ResultSet queryDatabase(String query, Statement stmt) throws SQLException {
        return stmt.executeQuery(query);
    }

    void closeStatement(Statement statement) throws SQLException{
        statement.close();
    }

    void closeConnection(Connection connection) throws SQLException {
        connection.close();
    }
}
