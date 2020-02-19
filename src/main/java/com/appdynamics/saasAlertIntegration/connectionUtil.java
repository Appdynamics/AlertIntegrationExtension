/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.roxstudio.utils.CUrl;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Level;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.h2.jdbcx.JdbcConnectionPool;

/**
 *
 * @author igor.simoes
 */
public class connectionUtil {
    final private static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    
    private String current_auth_token;
    private String metric_prefix;
    private String controller_account;
    private long current_auth_token_expiration = 0;
    
    public connectionUtil(String metric_prefix, String controller_account){
        this.metric_prefix = metric_prefix;
        this.controller_account = controller_account;
    }
    
    private CUrl.Resolver<ApiAuthentication> authJsonResolver = new CUrl.Resolver<ApiAuthentication>() {
        @SuppressWarnings("unchecked")
        @Override
        public ApiAuthentication resolve(int httpCode, byte[] responseBody) throws Throwable {
            String response = new String(responseBody, "UTF-8");
            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            ApiAuthentication auth_api = new ApiAuthentication();
            auth_api = gson.fromJson(response, ApiAuthentication.class);
            return gson.fromJson(response, ApiAuthentication.class);
        }
    };
    
    private CUrl.Resolver<Application[]> AppJsonResolver = new CUrl.Resolver<Application[]>() {
        @SuppressWarnings("unchecked")
        @Override
        public Application[] resolve(int httpCode, byte[] responseBody) throws Throwable {
            String response = new String(responseBody, "UTF-8");
            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            Application[] app_list = gson.fromJson(response, Application[].class);
            //  app_list = gson.fromJson(response, ApplicationList.class);
            return app_list;
        }
    };
    
    private CUrl.Resolver<Event[]> EventJsonResolver = new CUrl.Resolver<Event[]>() {
        @SuppressWarnings("unchecked")
        @Override
        public Event[] resolve(int httpCode, byte[] responseBody) throws Throwable {
            String response = new String(responseBody, "UTF-8");
            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            Event[] event_list = gson.fromJson(response, Event[].class);
            //  app_list = gson.fromJson(response, ApplicationList.class);
            return event_list;
        }
    };
    
    private String authenticate(String controllerURL, String controllerUser, String controllerAccount, String controllerKey){
        long curr_time = Instant.now().toEpochMilli();
        long curr_token_expiration_time = getCurrentAuthTokenExpiration();
        String authentication_token = "";
        if (curr_time > curr_token_expiration_time){
            String full_url = controllerURL+"/controller/api/oauth/access_token";
            String data = "grant_type=client_credentials&client_id=http_integration@ibm-metalurgicagerdau-nonprod&client_secret=8a0ae8c2-1988-49a2-be2e-7c54a7610865";
            String header = "Content-Type: application/vnd.appd.cntrl+protobuf;v=1";
            CUrl curl = new CUrl(full_url).data(data);//.data("foo=overwrite");
            curl.header(header);
            ApiAuthentication auth_api = curl.exec(authJsonResolver, null);
            int response_code = curl.getHttpCode();
            if (response_code < 400){
                long expiration_time = curr_time + auth_api.expires_in*1000;
                setCurrentAuthTokenExpiration(expiration_time);
                authentication_token = auth_api.access_token;
                setCurrentAuthToken(authentication_token);
            }
            else{
                LOGGER.log(Level.INFO, "There was a problem authenticating, will try again in next job execution.");
            }
        }
        else{
            authentication_token = getCurrentAuthToken();
        }
        return authentication_token;
    }
    
    public Event[] getEvents(String application_id, String application_name, String controllerURL, String controllerUser, String controllerAccount, String controllerKey, int duration_seconds) {
        Event[] event_list = null;
        List<Event> temp_event_list = new ArrayList<>();
        int duration_mins = duration_seconds/60;
            String auth_token = authenticate(controllerURL, controllerUser, controllerAccount, controllerKey);
            if (!auth_token.equals("")){
                try{
                    LOGGER.log(Level.INFO, "{0}: Trying to retrieve events for application {1}", new Object[]{Thread.currentThread().getName(), application_name});
                    String full_url = controllerURL+"/controller/rest/applications/"+application_id+"/problems/healthrule-violations?time-range-type=BEFORE_NOW&duration-in-mins="+duration_mins+"&output=json";
                    CUrl curl = new CUrl(full_url);
                    String header = "Authorization:Bearer "+auth_token;
                    curl.header(header);
                    LOGGER.log(Level.INFO, "Curl: \""+ full_url +"\", Header: \"Authorization:Bearer xxxxxxxxx\"");
                    event_list = curl.exec(EventJsonResolver, null);
                    if (curl.getHttpCode() <= 400){
                        LOGGER.log(Level.INFO, "{0}: Events successfully retrieved for application {1}:", new Object[]{Thread.currentThread().getName(), application_name});
                    }
                    else{
                        LOGGER.log(Level.INFO, "{0}: Connection failed for application: {1}.", new Object[]{Thread.currentThread().getName(), application_name});
                    }
                }
                catch (Exception e){
                    LOGGER.log(Level.WARNING, "{0} There was an error while retrieving application {1} events: {2}",new Object[]{Thread.currentThread().getName(), application_name, e.getMessage()});
                }
            }
            //leaving only the alerts that are actually in the current time range
            for (Event event:event_list){
                if (duration_seconds<=300) duration_seconds+=60;//this will make that any small differences on execution time do not leave any alerts behind
                if ((System.currentTimeMillis()-event.getStartTimeInMillis())<=(duration_seconds*1000)){
                    temp_event_list.add(event);
                }
            }
            Event[] final_event_list = new Event[temp_event_list.size()];//temp_event_list.toArray();
            final_event_list = temp_event_list.toArray(final_event_list);
        return final_event_list;
    }
    
    private String mapFields(String input, Event event, String application){
        String output;
        String severity;
        if (event.getIncidentStatus() == "RESOLVED" ){
            LOGGER.log(Level.INFO, "{0}: Mapping fields, incident status: {1}", new Object[]{Thread.currentThread().getName(), event.getIncidentStatus()});
            severity = "CLEAR";
        }
        else{
            severity = event.getSeverity();
        }
        //Pattern pattern = Pattern.compile("{(.*?)}");
        //Matcher matcher = pattern.matcher(appd_field);
        output = input;
        output = output.replace("ACCOUNT_NAME", this.controller_account);
        output = output.replace("AFFECTED_ENTITY_NAME", event.getAffectedEntityDefinition().getName());
        output = output.replace("TRIGGERED_ENTITY_NAME", event.getTriggeredEntityDefinition().getName());
        output = output.replace("APPLICATION_NAME", application);
        output = output.replace("DEEP_LINK", event.getDeepLinkUrl());
        output = output.replace("SEVERITY", severity);
        output = output.replace("TRIGGERED_ENTITY_TYPE", event.getTriggeredEntityDefinition().getEntityType());
        output = output.replace("TRIGGERED_ENTITY_ID", event.getTriggeredEntityDefinition().getEntityId());
        output = output.replace("START_TIME", Long.toString(event.getStartTimeInMillis()));
        output = output.replace("DETECTED_TIME", Long.toString(event.getDetectedTimeInMillis()));
        output = output.replace("END_TIME", Long.toString(event.getEndTimeInMillis()));
        output = output.replace("NAME", event.getName());
        output = output.replace("DESCRIPTIPTION", event.getDescription());
        output = output.replace("ID", event.getId());
        output = output.replace("AFFECTED_ENTITY_TYPE", event.getAffectedEntityDefinition().getEntityType());
        output = output.replace("AFFECTED_ENTITY_ID", event.getAffectedEntityDefinition().getEntityId());
        output = output.replace("STATUS", event.getIncidentStatus());
        output = output.replace("{", "").replace("}", "");
        LOGGER.log(Level.INFO, "{0}: Mapping fields: {1}", new Object[]{Thread.currentThread().getName(), output});
        return output;
    }
    
    private int sendEventHTTPRequest(Event event, String integration_hostname, String integration_port, String integration_protocol, String application){
        ConfigReader itm_appd_config = new ConfigReader();
        int HTTPReturnCode = 0;
        try{
            String itm_classname = "", itm_customercode = "", itm_origin = "", itm_severity = "", itm_hostname = "", itm_applid = "", itm_msg = "", itm_ipaddress = "", itm_component = "", itm_subcomponent = "", itm_instancesituation = "", itm_instanceid = "", itm_eventtype = "";
            Properties itm_appd_field_mapping = itm_appd_config.getPropValues("itm_appd_mapping.properties");
            Enumeration<String> enums = (Enumeration<String>) itm_appd_field_mapping.propertyNames();
            while (enums.hasMoreElements()) {
                String key = enums.nextElement(); 
                String value = itm_appd_field_mapping.getProperty(key);
                switch (key){
                    case "classname":
                        itm_classname=mapFields(value, event, application);
                        break;
                    case "customercode":
                        itm_customercode=mapFields(value, event, application);
                        break;
                    case "origin":
                        itm_origin=mapFields(value, event, application);
                        break;
                    case "severity":
                        itm_severity=mapFields(value, event, application);
                        break;
                    case "hostname":
                        itm_hostname=mapFields(value, event, application);
                        break;
                    case "applid":
                        itm_applid=mapFields(value, event, application);
                        break;
                    case "msg":
                        itm_msg=mapFields(value, event, application);
                        break;
                    case "ipaddress":
                        itm_ipaddress=mapFields(value, event, application);
                        break;
                    case "component":
                        itm_component=mapFields(value, event, application);
                        break;
                    case "subcomponent":
                        itm_subcomponent=mapFields(value, event, application);
                        break;
                    case "instancesituation":
                        itm_instancesituation=mapFields(value, event, application);
                        break;
                    case "instanceid":
                        itm_instanceid=mapFields(value, event, application);
                        break;
                    case "eventtype":
                        itm_eventtype=mapFields(value, event, application);
                        break;
                }
            }
            String POST_BODY = "{"+
                               "\"source\":\"AppDynamics\","+
                               "\"classname\":\""+itm_classname+"\","+
                               "\"customercode\":\""+itm_customercode+"\","+
                               "\"origin\":\""+itm_origin+"\","+
                               "\"severity\":\""+itm_severity+"\","+
                               "\"hostname\":\""+itm_hostname+"\","+
                               "\"applid\":\""+itm_applid+"\","+
                               "\"msg\":\""+itm_msg+"\","+
                               "\"ipaddress\":\""+itm_ipaddress+"\","+
                               "\"component\":\""+itm_component+"\","+
                               "\"subcomponent\":\""+itm_subcomponent+"\","+
                               "\"instancesituation\":\""+itm_instancesituation+"\","+
                               "\"instanceid\":\""+itm_instanceid+"\","+
                               "\"eventtype\":\""+itm_eventtype+"\""+
                               "}";
            LOGGER.log(Level.INFO, "{0}: Integration POST BODY Json: {1}", new Object[]{Thread.currentThread().getName(), POST_BODY});
            String full_integration_url = integration_protocol+"://"+integration_hostname+":"+integration_port;
            CUrl curl = new CUrl(full_integration_url);
            curl.data(POST_BODY);
            LOGGER.log(Level.INFO, "Curl: \"{0}\"", full_integration_url);
            curl.exec();
            HTTPReturnCode = curl.getHttpCode();
        }
        catch(IOException ex){
            LOGGER.log(Level.INFO, "{0}: There was an error while loading the mapping configuration: {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
        }
        return HTTPReturnCode;
    }
    
    public boolean insertEventsToTemp(Event[] events, String application, Connection con){
        boolean success = true;
        String insert_string = "";
        Event current_event = null;
        try{
            //Connection con = connectToH2();
            //Connection con = this.connectionPool.getConnection();
            
            //con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            Statement stm = con.createStatement();
            for (Event event:events){
                current_event = event;
                insert_string = "insert into temp_events values('"+event.getId()+"','"
                                                                        +event.getDeepLinkUrl()+"','"
                                                                        +event.getSeverity()+"','"
                                                                        +event.getTriggeredEntityDefinition().getEntityType()+"','"
                                                                        +event.getTriggeredEntityDefinition().getName()+"','"
                                                                        +event.getTriggeredEntityDefinition().getEntityId()+"','"
                                                                        +event.getStartTimeInMillis()+"','"
                                                                        +event.getDetectedTimeInMillis()+"','"
                                                                        +event.getEndTimeInMillis()+"','"
                                                                        +event.getName()+"','"
                                                                        +event.getDescription().replace("'", "''")+"','"//escaping invalid chars is needed
                                                                        +event.getAffectedEntityDefinition().getEntityType()+"','"
                                                                        +event.getTriggeredEntityDefinition().getName()+"','"
                                                                        +event.getTriggeredEntityDefinition().getEntityId()+"','"
                                                                        +event.getIncidentStatus()+"','"
                                                                        +application+"')";
                stm.execute(insert_string);
                LOGGER.log(Level.INFO, "{0}: Event being inserted: {1}", new Object[]{Thread.currentThread().getName(), event.toString()});
            }
        }
        catch (SQLException ex){
            success = false;
            LOGGER.log(Level.WARNING, "{0}: There was a problem inserting application {1} event ''{2}'' to the local control database. Will retry on next cycle.", new Object[]{Thread.currentThread().getName(), application, current_event.getId()});
            LOGGER.log(Level.WARNING, "{0}: SQL: {1}", new Object[]{Thread.currentThread().getName(), insert_string});
            LOGGER.log(Level.INFO, "{0}: There was an exception while updating temp_events. {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
        }
        
        return success;
    }
    
    public List<Event> eventsToBeSent(Event[] event_list, String application, Connection con){
        List<Event> Events = new ArrayList<>();
        try{
            //Connection con = connectToH2();
            //Connection con = this.connectionPool.getConnection();
            //con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            Statement stm = con.createStatement();
            for (Event event:event_list){
                String select_string = "select * from temp_events where application = '"+application+
                                       "' and temp_events.id not in (select id from events where application = '"+application+
                                       " and type = "+event.getSeverity().substring(0,2)+
                                       " and status = "+event.getIncidentStatus().substring(0,2)+
                                       " and id = "+event.getId()+
                                       "')";
                LOGGER.log(Level.INFO, "{0}: Query to be executed: {1}", new Object[]{Thread.currentThread().getName(), select_string});
                ResultSet rs = stm.executeQuery(select_string);
                rs.next();
                if ((rs.isFirst()) && (rs.isLast())){//we got a single line
                    Events.add(event);
                    LOGGER.log(Level.INFO, "{0}: Event to be sent: {1}", new Object[]{Thread.currentThread().getName(), event.toString()});
                }
            }
        }
        catch (SQLException ex){
            LOGGER.log(Level.INFO, "{0}: There was an exception while updating temp_events.{1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
        }
        return Events;
    }
    
    public int sendEventsToIntegration(List<Event> events, String application, int tentative, Connection con, String integration_hostname, String integration_port, String integration_protocol){
        int  succeeded = 0;
        LOGGER.log(Level.INFO, "{0}: Sending application {1} EVENTS to integration.", new Object[]{Thread.currentThread().getName(), application});
        for (Event event:events){
            boolean sendToIntegrationResult;
            updateLogTables(event, application, con);
            sendToIntegrationResult = sendEventToIntegration(event, application, tentative, integration_hostname, integration_port, integration_protocol);
            completeDBTransaction(sendToIntegrationResult, con);
            try{
                LOGGER.log(Level.INFO, "{0}: Writing event to file.", new Object[]{Thread.currentThread().getName()});
                if (sendToIntegrationResult == true) writeSentEventsToFile("Event ID: "+event.getId()+", Event Name: "+event.getName()+", Application: "+application);
            }
            catch (IOException ex){
                LOGGER.log(Level.WARNING, "{0}: WRITING EVENTS TO FILE FAILED: {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
            }
            succeeded += (sendToIntegrationResult) ? 1 : 0;
        }
        return succeeded;
    }
    
    private void writeSentEventsToFile(String event) throws IOException{
        FileWriter writer = new FileWriter("SentAlerts.txt", true);
        writer.append(event+"\n");
        writer.flush();
        writer.close();
    }
    
    public void writeMetric(String metric_path, String metric_aggregation, String metric_value){
        String name;
        
        name = this.metric_prefix+"|"+metric_path;
        name = name.replace("\"", "").replace("||", "|");
        System.out.println("name="+name+",value="+metric_value+",aggregator="+metric_aggregation);
    }
    
    private boolean sendEventToIntegration(Event event, String application, int tentative, String integration_hostname, String integration_port, String integration_protocol){
        boolean succeeded=false;
        LOGGER.log(Level.INFO, "{0}: Sending application {2} event {1} to integration.", new Object[]{Thread.currentThread().getName(), event.getId(), application});
        if (tentative >= 10){
            LOGGER.log(Level.WARNING, "{0}: Number of tentatives shoulf be less than 10, changing value to 9.", Thread.currentThread().getName());
            tentative = 9;
        }
        //if (tentative <= 2){    
        while (tentative >=0){
            LOGGER.log(Level.INFO, "{0}: Tentative {3}, application {2}, event {1} to integration.", new Object[]{Thread.currentThread().getName(), event.getId(), application, tentative});
            int httpResponse = sendEventHTTPRequest(event, integration_hostname, integration_port, integration_protocol, application);
            LOGGER.log(Level.INFO, "{0}: HTTP response code from application {2}, event {1} to integration: {3}", new Object[]{Thread.currentThread().getName(), event.getId(), application, httpResponse});
            if (httpResponse == 200) {
                succeeded = true;
                writeMetric("Success sendEventToIntegration", "AVERAGE", "1");
                break;
            }
            else {
                try{
                    tentative--;
                    writeMetric("Fail sendEventToIntegration", "AVERAGE", "1");
                    succeeded = false;
                    Thread.sleep(500);
                    sendEventToIntegration(event, application, tentative, integration_hostname, integration_port, integration_protocol);
                }
                catch(InterruptedException ex){
                    LOGGER.log(Level.WARNING, "{0}: There was a problem sleeping between integration tentatives.", Thread.currentThread().getName());
                }
            }
        }
        return succeeded;
    }
    
    private void updateLogTables(Event event, String application, Connection con){
        LOGGER.log(Level.INFO, "{0}: Updating log tables for application {2} event {1}.", new Object[]{Thread.currentThread().getName(), event.getId(), application});
        sendEventToLogTable(event, application, con);
        removeEventFromTempLogTable(event, application, con);
    }
    
    private boolean completeDBTransaction(boolean commit, Connection con){
        boolean succeeded = false;
        LOGGER.log(Level.INFO, "{0}: Completing DB transaction.", new Object[]{Thread.currentThread().getName()});
        try{
            if (commit == true){
                LOGGER.log(Level.INFO, "{0}: Successfully commited DB transaction.", new Object[]{Thread.currentThread().getName()});
                con.commit();
            }
            else{
                LOGGER.log(Level.WARNING, "{0}: Successfully rolled back DB transaction.", new Object[]{Thread.currentThread().getName()});
                con.rollback();
            }
            succeeded = true;
        }
        catch (SQLException ex){
            LOGGER.log(Level.WARNING, "{0}: There was an exception ***COMITING*** transactions: {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
        }
        return succeeded;
    }
    
    private boolean sendEventToLogTable(Event event, String application, Connection con){
        boolean succeeded = false;
        try{
            //Connection con = this.connectionPool.getConnection();//getH2Connection();
            //con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            Statement stm = con.createStatement();
            LOGGER.log(Level.INFO, "{0}: Checking if event needs to be updated for application {1} event {2}.", new Object[]{Thread.currentThread().getName(), application, event.getId()});
            String select_string = "select id from events where id ='"+event.getId()+"'";
            ResultSet rs = stm.executeQuery(select_string);
            rs.next();
            if ((rs.isFirst())&&(rs.isLast())){
                //update the record
                String update_string = "update events set severity = "+event.getSeverity().substring(0,2)+", status="+event.getIncidentStatus().substring(0,2)+" where id = "+event.getId()+")";
                LOGGER.log(Level.INFO, "{0}: Updating log table for application {1} event {2}. Previous status: {3}, current status: {4}, previous severity: {5}, current severity: {6}", new Object[]{Thread.currentThread().getName(), application, event.getId(), rs.getString("status"), event.getIncidentStatus(), rs.getString("severity"), event.getSeverity()});
                LOGGER.log(Level.INFO, "{0}: Updating query: {1}", new Object[]{Thread.currentThread().getName(), update_string});
                stm.execute(update_string);
                succeeded = true;
                writeMetric("Insert log success", "AVERAGE", "1");
            }
            else{
                //insert the record
                String insert_string = "insert into events values("+event.getId()+","+event.getStartTimeInMillis()+","+System.currentTimeMillis()+",'"+event.getSeverity().substring(0,2)+"','"+application+"','"+event.getIncidentStatus().substring(0, 2)+"')";
                LOGGER.log(Level.INFO, "{0}: Inserting into log table for application {1} event {2}.", new Object[]{Thread.currentThread().getName(), application, event.getId()});
                LOGGER.log(Level.INFO, "{0}: Insert query {1}.", new Object[]{Thread.currentThread().getName(), insert_string});
                stm.execute(insert_string);
                succeeded = true;
                writeMetric("Insert log success", "AVERAGE", "1");
            }
            
        }
        catch(SQLException ex){
            writeMetric("Insert log fail", "AVERAGE", "1");
            LOGGER.log(Level.WARNING, "{0}: There was an exception writing to events log table: {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
        }
        return succeeded;
    }
    
    private boolean removeEventFromTempLogTable(Event event, String application, Connection con){
        boolean succeeded = false;
        try{
            LOGGER.log(Level.INFO, "{0}: Removing from log table for application {1} event {2}.", new Object[]{Thread.currentThread().getName(), application, event.getId()});
            Statement stm = con.createStatement();
            String delete_string = "delete from temp_events where id = "+event.getId()+" and application='"+ application+"'";
            stm.execute(delete_string);
            succeeded = true;
            writeMetric("Delete temp log success", "AVERAGE", "1");
        }
        catch(SQLException ex){
            writeMetric("Delete temp log fail", "AVERAGE", "1");
            LOGGER.log(Level.WARNING, "{0}: There was an exception deleting from tempo_events log table: {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
        }
        return succeeded;
    }
    
    public Application[] getApplications(String controllerURL, String controllerUser, String controllerAccount, String controllerKey) {
            Application[] app_list = null;
            Application[] final_app_list = null;
            String auth_token = authenticate(controllerURL, controllerUser, controllerAccount, controllerKey);
            if (!auth_token.equals("")){
                try{
                    LOGGER.log(Level.INFO, "{0}: Trying to retrieve applications...", Thread.currentThread().getName());
                    String full_url = controllerURL+"/controller/rest/applications?output=json";
                    CUrl curl = new CUrl(full_url);
                    String header = "Authorization:Bearer "+auth_token;
                    curl.header(header);
                    LOGGER.log(Level.INFO, "{0}: Curl: \"{1}\", Header: \"Authorization:Bearer xxxxxxxxx\"", new Object[]{Thread.currentThread().getName(), full_url});
                    app_list = curl.exec(AppJsonResolver, null);
                    if (curl.getHttpCode() <= 400){
                        LOGGER.log(Level.INFO, "{0}: Applications successfully retrieved:", Thread.currentThread().getName());
                        for (Application app:app_list){
                            LOGGER.log(Level.INFO, app.toString());
                        }
                        //System.out.println("Applications successfully retrieved...");
                    }
                    else{
                        LOGGER.log(Level.INFO, "{0}: Failed retrieving applications. HTTP STATUS CODE: {1}", new Object[]{Thread.currentThread().getName(), curl.getHttpCode()});
                        //System.out.println("Connection failed...");
                    }
                    //adding database monitoring and server monitoring to the app list
                    //getting Server Monitoring info
                    full_url = controllerURL+"/controller/rest/applications/Server%20%26%20Infrastructure%20Monitoring?output=json";
                    curl = new CUrl(full_url);
                    curl.header(header);
                    Application[] server_monitoring_app = curl.exec(AppJsonResolver, null);
                    
                    //getting Database Monitoring info
                    full_url = controllerURL+"/controller/rest/applications/Database%20Monitoring?output=json";
                    curl = new CUrl(full_url);
                    curl.header(header);
                    Application[] database_monitoring_app = curl.exec(AppJsonResolver, null);
                    final_app_list = new Application[app_list.length+2];
                    int i=0;
                    for (Application app:app_list){
                        final_app_list[i] = app;
                        i++;                        
                    }
                    final_app_list[i] = server_monitoring_app[0];
                    i++;
                    final_app_list[i] = database_monitoring_app[0];
                }
                catch (Exception e){
                    LOGGER.log(Level.WARNING, "{0}: There was an error: {1}", new Object[]{Thread.currentThread().getName(), e.getMessage()});
                }
            }
            
        return final_app_list;
    }
    
    private void setCurrentAuthTokenExpiration(long expiration_milis) {
        this.current_auth_token_expiration = expiration_milis;
    }
    
    private long getCurrentAuthTokenExpiration() {
        return current_auth_token_expiration;
    }
    
    private void setCurrentAuthToken(String token) {
        this.current_auth_token = token;
    }
    
    private String getCurrentAuthToken() {
        return current_auth_token;
    }
    
}
