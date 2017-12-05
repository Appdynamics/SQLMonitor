package com.appdynamics.extensions.sql;


public class Util {


    static String convertToString(final Object field,final String defaultStr){
        if(field == null){
            return defaultStr;
        }
        return field.toString();
    }


}
