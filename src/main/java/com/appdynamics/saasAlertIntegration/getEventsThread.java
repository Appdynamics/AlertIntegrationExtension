/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

import com.appdynamics.saasAlertIntegration.connectionUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import org.h2.jdbcx.JdbcConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

/**
 *
 * @author igor.simoes
 */
public class getEventsThread extends Thread implements Runnable {
    
    String application_id;
    String application_name;
    String controller_url;
    String controller_user;
    String controller_account;
    String controller_key;
    int poll_interval;
    String metric_prefix;
    String integration_hostname;
    String integration_port;
    String integration_protocol;
    private Connection connection;
    HashMap<String, String> severity_mapping_codes;
    String update_incidents;
    //final private static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    final private static Logger LOGGER = LogManager.getRootLogger();
    
    public getEventsThread(String application_id, 
            String application_name, 
            String controller_url, 
            String controller_user, 
            String controller_account, 
            String controller_key, 
            int poll_interval, 
            Connection connection, 
            String metric_prefix, 
            String integration_hostname, 
            String integration_port, 
            String integration_protocol,
            HashMap<String, String> severity_mapping_codes,
            String update_incidents) {
	this.application_id = application_id;
        this.application_name = application_name;
        this.controller_url = controller_url;
        this.controller_user = controller_user;
        this.controller_account = controller_account;
        this.controller_key = controller_key;
        this.poll_interval = poll_interval;
        this.connection = connection;
        this.metric_prefix = metric_prefix;
        this.integration_hostname = integration_hostname;
        this.integration_port = integration_port;
        this.integration_protocol = integration_protocol;
        this.severity_mapping_codes = severity_mapping_codes;
        this.update_incidents = update_incidents;
    }
    
    public void run() {
        Event[] event_list;
        
        connectionUtil connection = new connectionUtil(metric_prefix, controller_account, severity_mapping_codes);
        event_list = connection.getEvents(this.application_id, this.application_name, this.controller_url, this.controller_user, this.controller_account, this.controller_key, this.poll_interval, this.update_incidents);
        try{
            if (connection.insertEventsToTemp(event_list, this.application_name, this.connection)){
                List<Event> events_to_send = connection.eventsToBeSent(event_list, this.application_name, this.connection);
                if (!events_to_send.isEmpty()){
                    int events_sent = connection.sendEventsToIntegration(events_to_send, this.application_name, 1, this.connection, this.integration_hostname, this.integration_port, this.integration_protocol, this.application_id);
                    //int sent_rate=events_sent/events_to_send.size();
                    connection.writeMetric("Number of successful sent events", "AVERAGE", Integer.toString(events_sent));
                    connection.writeMetric("Number of expected events to be sent", "AVERAGE", Integer.toString(events_to_send.size()));
                    connection.writeMetric("Percentage of successful events sent", "AVERAGE", Integer.toString(Math.round(events_sent*100/events_to_send.size())));
                    
                }
                else LOGGER.log(Level.INFO, "{}: List of events for application {} is empty.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), this.application_name});
            }
            this.connection.close();
            //this.connection.
        }
        catch (SQLException ex){
            LOGGER.log(Level.WARN, "{}: There was a SQL Exception: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        catch (NullPointerException ex2){
            LOGGER.log(Level.WARN, "{}: Null pointer exception: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex2.getMessage(), ex2.getStackTrace().toString()});
            ex2.printStackTrace();
        }
    }
}
