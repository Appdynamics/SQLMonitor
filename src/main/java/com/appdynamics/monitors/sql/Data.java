/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.monitors.sql;

/**
 * A simple bean to hold name and value pair
 * @author stevew
 */
public class Data
{
    private String name;
    private String value;
    
    public Data()
    {
        this.name = null;
        this.value = null;
    }
    
    public Data(String name, String value)
    {
        this.name = name;
        this.value = value;
    }
    
    public void setName(String name)
    {
        this.name = name;
    }
    
    public void setValue(String value)
    {
        this.value = value;
    }
    
    public String getName()
    {
        return this.name;
    }
    
    public String getValue()
    {
        return this.value;
    }
    
    @Override
    public String toString()
    {
        return name + " = " + value;
    }
}
