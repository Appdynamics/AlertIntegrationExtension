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
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.h2.jdbcx.JdbcConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

/**
 *
 * @author igor.simoes
 */
public class connectionUtil {
    final private static Logger LOGGER = LogManager.getRootLogger();
    
    private String current_auth_token;
    private String metric_prefix;
    private String controller_account;
    private long current_auth_token_expiration = 0;
    private HashMap<String, String> severity_mapping_codes;
    
    public connectionUtil(String metric_prefix, String controller_account, HashMap<String, String> severity_mapping_codes){
        this.metric_prefix = metric_prefix;
        this.controller_account = controller_account;
        this.severity_mapping_codes = severity_mapping_codes;
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
        LOGGER.log(Level.INFO, "{}: Authenticating on the controller...", new Object(){}.getClass().getEnclosingMethod().getName());
        if (curr_time > curr_token_expiration_time){
            String full_url = controllerURL+"/controller/api/oauth/access_token";
            String data = "grant_type=client_credentials&client_id="+controllerUser+"@"+controllerAccount+"&client_secret="+controllerKey;
            String header = "Content-Type: application/vnd.appd.cntrl+protobuf;v=1";
            CUrl curl = new CUrl(full_url).data(data);//.data("foo=overwrite");
            curl.header(header);
            ApiAuthentication auth_api = null;
            int auth_api_tentatives = 4;
            while ((auth_api == null) && (auth_api_tentatives>=0)){
                auth_api = curl.exec(authJsonResolver, null);
                auth_api_tentatives--;
                try{ 
                    Thread.sleep(500);
                }
                catch (InterruptedException ex){
                    LOGGER.log(Level.WARN, "{}: There was a problem sleeping the thread:{}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
                }
            }
            int response_code = curl.getHttpCode();
            LOGGER.log(Level.INFO, "{}: Auth API HTTP Response code: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), response_code});
            long expiration_time=0L;
            try {
                if ((response_code < 400)&&(response_code >= 200)){
                    expiration_time = curr_time + auth_api.expires_in*1000;
                    setCurrentAuthTokenExpiration(expiration_time);
                    authentication_token = auth_api.access_token;
                    setCurrentAuthToken(authentication_token);
                }
                else{
                    writeMetric("Failed controller authenticatoin", "AVERAGE", "1");
                    LOGGER.log(Level.WARN, "{}: There was a problem authenticating, will try again in next job execution. HTTP Response code:{}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), response_code});
                }
            }
            catch (NullPointerException ex){
                LOGGER.log(Level.WARN, "{}: There is a NullPointerException. expiration_time: {}\n curr_time: {}\n auth_api.expires_in: {}\n ", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), Long.toString(expiration_time), Long.toString(curr_time), auth_api.expires_in});
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
                    LOGGER.log(Level.INFO, "{}: Trying to retrieve events for application {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application_name});
                    String full_url = controllerURL+"/controller/rest/applications/"+application_id+"/problems/healthrule-violations?time-range-type=BEFORE_NOW&duration-in-mins="+duration_mins+"&output=json";
                    CUrl curl = new CUrl(full_url);
                    String header = "Authorization:Bearer "+auth_token;
                    curl.header(header);
                    LOGGER.log(Level.INFO, "{}: Curl: \"{}\", Header: \"Authorization:Bearer xxxxxxxxx\"", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), full_url});
                    event_list = curl.exec(EventJsonResolver, null);
                    int http_status = curl.getHttpCode();
                    LOGGER.log(Level.INFO, "{}: HTTP Status for retrieving events for application {}: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application_name, http_status});
                    if ((http_status < 400) && (http_status>0)){
                        int events_length = 0;
                        if (event_list != null) events_length = event_list.length;
                        LOGGER.log(Level.INFO, "{}: Total of {} events successfully retrieved for application: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), events_length, application_name});
                    }
                    else{
                        LOGGER.log(Level.WARN, "{}: Connection failed for application: {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application_name});
                    }
                }
                catch (Exception e){
                    LOGGER.log(Level.WARN, "{}: There was an error while retrieving application {} events: {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application_name, e.getMessage()});
                }
            }
            if (event_list != null){
                //leaving only the alerts that are actually in the current time range
                for (Event event:event_list){
                    if (duration_seconds<=300) duration_seconds+=60;//this will make that any small differences on execution time do not leave any alerts behind
                    LOGGER.log(Level.INFO, "{}: Event: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), event.toString()});
                    if (((System.currentTimeMillis()-event.getStartTimeInMillis())<=(duration_seconds*1000)) || (event.getIncidentStatus().equals("RESOLVED")) || (event.getIncidentStatus().equals("CANCELED")) || (event.getIncidentStatus().equals("CANCELLED"))){
                        temp_event_list.add(event);
                    }
                }
            }
            Event[] final_event_list = new Event[temp_event_list.size()];//temp_event_list.toArray();
            final_event_list = temp_event_list.toArray(final_event_list);
            LOGGER.log(Level.INFO, "{}: Total of {} events need to be handled by integration for application: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), final_event_list.length, application_name});
        return final_event_list;
    }
    
    private String mapFields(String input, Event event, String application){
        String output;
        String incidentStatus = event.getIncidentStatus();
        String severity;
        if ((incidentStatus.equals("CANCELED")) || (incidentStatus.equals("CANCELLED")) || (incidentStatus.equals("RESOLVED"))) severity = "CLEAR";
        else severity = event.getSeverity();
        
        String severity_code = severity_mapping_codes.get(severity);
        LOGGER.log(Level.INFO, "{}: Severity WARNING value: {}, Severity CRITICAL value: {}, Severity CLEAR value: {}, Severity event value: {}, Severity event code value: {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), severity_mapping_codes.get("WARNING"), severity_mapping_codes.get("CRITICAL"), severity_mapping_codes.get("CLEAR"), severity, severity_code});
        event.setITMSeverityCode(severity_code);
     
        
        output = input;
        output = output.replace("ACCOUNT_NAME", this.controller_account);
        output = output.replace("AFFECTED_ENTITY_NAME", event.getAffectedEntityDefinition().getName());
        output = output.replace("TRIGGERED_ENTITY_NAME", event.getTriggeredEntityDefinition().getName());
        output = output.replace("APPLICATION_NAME", application);
        output = output.replace("DEEP_LINK", event.getDeepLinkUrl());
        output = output.replace("SEVERITY_CODE", severity_code);
        output = output.replace("SEVERITY", severity);
        output = output.replace("TRIGGERED_ENTITY_TYPE", event.getTriggeredEntityDefinition().getEntityType());
        output = output.replace("TRIGGERED_ENTITY_ID", event.getTriggeredEntityDefinition().getEntityId());
        output = output.replace("START_TIME", Long.toString(event.getStartTimeInMillis()));
        output = output.replace("DETECTED_TIME", Long.toString(event.getDetectedTimeInMillis()));
        output = output.replace("END_TIME", Long.toString(event.getEndTimeInMillis()));
        output = output.replace("NAME", event.getName());
        output = output.replace("REDUCED_DESCRIPTION", event.getReducedDescription());
        output = output.replace("DESCRIPTIPTION", event.getDescription());
        output = output.replace("ID", event.getId());
        output = output.replace("AFFECTED_ENTITY_TYPE", event.getAffectedEntityDefinition().getEntityType());
        output = output.replace("AFFECTED_ENTITY_ID", event.getAffectedEntityDefinition().getEntityId());
        output = output.replace("STATUS", event.getIncidentStatus());
        output = output.replace("{", "").replace("}", "");
        LOGGER.log(Level.INFO, "{}: Mapping fields: {} -> {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), input, output});
        return output;
    }
    
    private int sendEventHTTPRequest(Event event, String integration_hostname, String integration_port, String integration_protocol, String application){
        ConfigReader itm_appd_config = new ConfigReader();
        int HTTPReturnCode = 0;
        try{
            String itm_classname = "", itm_customercode = "", itm_origin = "", itm_severity = "", itm_hostname = "", itm_applid = "", itm_msg = "", itm_ipaddress = "", itm_component = "", itm_subcomponent = "", itm_instancesituation = "", itm_instanceid = "", itm_eventtype = "";
            Properties itm_appd_field_mapping = itm_appd_config.getPropValues("itm_appd_mapping.properties");
            Enumeration<String> enums = (Enumeration<String>) itm_appd_field_mapping.propertyNames();
            String JSONInnerContents = "";
            while (enums.hasMoreElements()) {
                String key = enums.nextElement(); 
                String value = itm_appd_field_mapping.getProperty(key);
                JSONInnerContents += "\""+key+"\":\""+mapFields(value, event, application)+"\",";
                /*switch (key){
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
                }*/
            }
            String POST_BODY = "{" + JSONInnerContents + "\"source\":\"AppDynamics\"}";
                               /*"\"classname\":\""+itm_classname+"\","+
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
                               "}";*/
            LOGGER.log(Level.INFO, "{}: Integration POST BODY Json: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), POST_BODY});
            String full_integration_url = integration_protocol+"://"+integration_hostname+":"+integration_port;
            CUrl curl = new CUrl(full_integration_url);
            curl.data(POST_BODY);
            LOGGER.log(Level.INFO, "{}: Curl: \"{}\"", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), full_integration_url});
            curl.exec();
            HTTPReturnCode = curl.getHttpCode();
        }
        catch(IOException ex){
            LOGGER.log(Level.WARN, "{}: There was an error while loading the mapping configuration: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        return HTTPReturnCode;
    }
    
    public boolean insertEventsToTemp(Event[] events, String application, Connection con){
        boolean success = true;
        String insert_string = "";
        Event current_event = null;
        String severity;
        try{
            Statement stm = con.createStatement();
            for (Event event:events){
                if ((event.getIncidentStatus().equals("RESOLVED"))||(event.getIncidentStatus().equals("CANCELED"))||(event.getIncidentStatus().equals("CANCELLED"))||(event.getSeverity()==null)){
                    LOGGER.log(Level.INFO, "{}: Adjusting severity of event, incident status: {}, severity: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), event.getIncidentStatus(), event.getSeverity()});
                    severity = "CLEAR";
                }
                else{
                    severity = event.getSeverity();
                }
                current_event = event;
                insert_string = "insert into temp_events values('"+event.getId()+"','"
                                                                        +event.getDeepLinkUrl()+"','"
                                                                        +severity+"','"
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
                LOGGER.log(Level.INFO, "{}: Event being inserted: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), event.toString()});
            }
        }
        catch (SQLException ex){
            success = false;
            LOGGER.log(Level.WARN, "{}: There was a problem inserting application {} event ''{}'' to the local control database. Will retry on next cycle.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, current_event.getId()});
            LOGGER.log(Level.WARN, "{}: SQL: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), insert_string});
            LOGGER.log(Level.WARN, "{}: There was an exception while updating temp_events: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        
        return success;
    }
    
    public List<Event> eventsToBeSent(Event[] event_list, String application, Connection con){
        List<Event> Events = new ArrayList<>();
        try{
            Statement stm = con.createStatement();
            for (Event event:event_list){
                LOGGER.log(Level.INFO, "{}: Selecting eventsToBeSent: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), event.toString()});
                
                String select_string = "select * from temp_events where application = '"+application+
                                       "' and temp_events.id = '"+event.getId()+
                                       "' and temp_events.id not in (select id from events where application = '"+application+"\'"+
                                       " and type = '"+event.getSeverity().substring(0,2)+"\'"+
                                       " and status = \'"+event.getIncidentStatus().substring(0,2)+"\'"+
                                       " and id = \'"+event.getId()+
                                       "')";
                String temp_select = "select * from temp_events";
                ResultSet temprs = stm.executeQuery(temp_select);
                while (temprs.next()){
                    LOGGER.log(Level.INFO, "{}: Temp Event ID: {}, Temp Event Name: {}, Temp Event Severity: {}, Temp Event Application: {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), temprs.getString("ID"), temprs.getString("NAME"), temprs.getString("SEVERITY"), temprs.getString("APPLICATION")});
                }
                temp_select = "select id from events where application = '"+application+"\'"+
                                       " and type = '"+event.getSeverity().substring(0,2)+"\'"+
                                       " and status = \'"+event.getIncidentStatus().substring(0,2)+"\'"+
                                       " and id = \'"+event.getId()+"'";
                temprs = stm.executeQuery(temp_select);
                if (temprs.next()) LOGGER.log(Level.INFO, "{}: ID from Events: {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), temprs.getString("id")});
                else LOGGER.log(Level.INFO, "{}: No data on events table fro thhis event.");
                LOGGER.log(Level.INFO, "{}: Query to be executed: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), select_string});
                ResultSet rs = stm.executeQuery(select_string);
                rs.next();
                if ((rs.isFirst()) && (rs.isLast())){//we got a single line
                    Events.add(event);
                    LOGGER.log(Level.INFO, "{}: Event to be sent: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), event.toString()});
                }
            }
        }
        catch (SQLException ex){
            LOGGER.log(Level.WARN, "{}: There was an exception while getting list of events from temp_events: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        return Events;
    }
    
    public int sendEventsToIntegration(List<Event> events, String application, int tentative, Connection con, String integration_hostname, String integration_port, String integration_protocol){
        int  succeeded = 0;
        LOGGER.log(Level.INFO, "{}: Sending total of {} EVENTS for application {} to integration.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), events.size(), application});
        for (Event event:events){
            boolean sendToIntegrationResult;
            updateLogTables(event, application, con);
            sendToIntegrationResult = sendEventToIntegration(event, application, tentative, integration_hostname, integration_port, integration_protocol);
            completeDBTransaction(sendToIntegrationResult, con);
            try{
                LOGGER.log(Level.INFO, "{}: Writing event to file.", new Object(){}.getClass().getEnclosingMethod().getName());
                if (sendToIntegrationResult == true) writeSentEventsToFile("Event ID: "+event.getId()+
                                                                           ", Event Name: "+event.getName()+
                                                                           ", Application: "+application+
                                                                           ", Full Event String:"+event.toString());
            }
            catch (IOException ex){
                LOGGER.log(Level.WARN, "{}: WRITING EVENTS TO FILE FAILED: {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
            }
            succeeded += (sendToIntegrationResult) ? 1 : 0;
        }
        LOGGER.log(Level.INFO, "{}: Total of {} EVENTS for application {} SENT to integration.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), succeeded, application});
        try{
            con.close();
        }
        catch(SQLException ex){
            LOGGER.log(Level.ERROR, "{}: There was an error closing the connection fro application {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application});
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
        LOGGER.log(Level.INFO, "{}: Sending application {} event {} to integration.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), event.getId(), application});
        if (tentative >= 10){
            LOGGER.log(Level.WARN, new Object(){}.getClass().getEnclosingMethod().getName()+": Number of tentatives should be less than 10, changing value to 9.", new Object(){}.getClass().getEnclosingMethod().getName());
            tentative = 9;
        }
        while (tentative >=0){
            LOGGER.log(Level.INFO, "{}: Tentative {}, application {}, event {} to integration.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), tentative, application, event.getId()});
            int httpResponse = sendEventHTTPRequest(event, integration_hostname, integration_port, integration_protocol, application);
            LOGGER.log(Level.INFO, "{}: HTTP response code from application {}, event {} to integration: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, event.getId(), httpResponse});
            if (httpResponse == 200) {
                succeeded = true;
                writeMetric("Success sendEventToIntegration", "AVERAGE", "1");
                break;
            }
            else {
                try{
                    tentative--;
                    writeMetric("Retries sendEventToIntegration", "AVERAGE", "1");
                    if (tentative == 0) writeMetric("Fail sendEventToIntegration", "AVERAGE", "1");
                    succeeded = false;
                    Thread.sleep(500);
                    sendEventToIntegration(event, application, tentative, integration_hostname, integration_port, integration_protocol);
                }
                catch(InterruptedException ex){
                    LOGGER.log(Level.WARN, "{}: There was a problem sleeping between integration tentatives.", new Object(){}.getClass().getEnclosingMethod().getName());
                }
            }
        }
        return succeeded;
    }
    
    private void updateLogTables(Event event, String application, Connection con){
        LOGGER.log(Level.INFO, "{}: Updating log tables for application {} event {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, event.getId()});
        sendEventToLogTable(event, application, con);
        removeEventFromTempLogTable(event, application, con);
    }
    
    private boolean completeDBTransaction(boolean commit, Connection con){
        boolean succeeded = false;
        LOGGER.log(Level.INFO, "{}: Completing DB transaction.", new Object(){}.getClass().getEnclosingMethod().getName());
        try{
            if (commit == true){
                con.commit();
                LOGGER.log(Level.INFO, "{}: Successfully commited DB transaction.", new Object(){}.getClass().getEnclosingMethod().getName());
            }
            else{
                con.rollback();
                LOGGER.log(Level.WARN, "{}: Successfully rolled back DB transaction.", new Object(){}.getClass().getEnclosingMethod().getName());
            }
            succeeded = true;
        }
        catch (SQLException ex){
            LOGGER.log(Level.WARN, "{}: There was an exception ***COMITING*** transactions: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        return succeeded;
    }
    
    private boolean sendEventToLogTable(Event event, String application, Connection con){
        boolean succeeded = false;
        try{    
            Statement stm = con.createStatement();
            String select_string = "select id from events where id ='"+event.getId()+"'";
            LOGGER.log(Level.INFO, "{}: Checking if event needs to be updated for application {} event {}. Query: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, event.getId(), select_string});
            ResultSet rs = stm.executeQuery(select_string);
            rs.next();
            if ((rs.isFirst())&&(rs.isLast())){
                //update the record
                String update_string = "update events set type = '"+event.getSeverity().substring(0,2)+"', status = '"+event.getIncidentStatus().substring(0,2)+"' where id = '"+event.getId()+"'";
                LOGGER.log(Level.INFO, "{}: Updating log table for application {} event {}. Previous status: {}, current status: {}, previous severity: {}, current severity: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, event.getId(), rs.getString("status"), event.getIncidentStatus(), rs.getString("severity"), event.getSeverity()});
                LOGGER.log(Level.INFO, "{}: Updating query: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), update_string});
                stm.execute(update_string);
                succeeded = true;
                writeMetric("Insert log success", "AVERAGE", "1");
            }
            else{
                //insert the record
                String insert_string = "insert into events values("+event.getId()+","+event.getStartTimeInMillis()+","+System.currentTimeMillis()+",'"+event.getSeverity().substring(0,2)+"','"+application+"','"+event.getIncidentStatus().substring(0, 2)+"')";
                LOGGER.log(Level.INFO, "{}: Inserting into log table for application {} event {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, event.getId()});
                LOGGER.log(Level.INFO, "{}: Insert query {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), insert_string});
                stm.execute(insert_string);
                succeeded = true;
                writeMetric("Insert log success", "AVERAGE", "1");
            }
            
        }
        catch(SQLException ex){
            writeMetric("Insert log fail", "AVERAGE", "1");
            LOGGER.log(Level.WARN, "{}: There was an exception writing to events log table: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        return succeeded;
    }
    
    private boolean removeEventFromTempLogTable(Event event, String application, Connection con){
        boolean succeeded = false;
        try{
            LOGGER.log(Level.INFO, "{}: Removing from log table for application {} event {}.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), application, event.getId()});
            Statement stm = con.createStatement();
            String delete_string = "delete from temp_events where id = "+event.getId()+" and application='"+ application+"'";
            stm.execute(delete_string);
            succeeded = true;
            writeMetric("Delete temp log success", "AVERAGE", "1");
        }
        catch(SQLException ex){
            writeMetric("Delete temp log fail", "AVERAGE", "1");
            LOGGER.log(Level.WARN, "{}: There was an exception deleting from tempo_events log table: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        return succeeded;
    }
    
    public Application[] getApplications(String controllerURL, String controllerUser, String controllerAccount, String controllerKey, String alert_server_viz, String alert_db_viz) {
            Application[] app_list = null;
            Application[] final_app_list = null;
            String auth_token = authenticate(controllerURL, controllerUser, controllerAccount, controllerKey);
            if (!auth_token.equals("")){
                try{
                    LOGGER.log(Level.INFO, "{}: Trying to retrieve applications...", new Object(){}.getClass().getEnclosingMethod().getName());
                    String full_url = controllerURL+"/controller/rest/applications?output=json";
                    CUrl curl = new CUrl(full_url);
                    String header = "Authorization:Bearer "+auth_token;
                    curl.header(header);
                    LOGGER.log(Level.INFO, "{}: Curl: \"{}\", Header: \"Authorization:Bearer xxxxxxxxx\"", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), full_url});
                    app_list = curl.exec(AppJsonResolver, null);
                    if (curl.getHttpCode() <= 400){
                        LOGGER.log(Level.INFO, "{}: Applications successfully retrieved:", new Object(){}.getClass().getEnclosingMethod().getName());
                        for (Application app:app_list){
                            LOGGER.log(Level.INFO, app.toString());
                        }
                    }
                    else{
                        LOGGER.log(Level.WARN, "{}: Failed retrieving applicatios. HTTP STATUS CODE: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), curl.getHttpCode()});
                    }
                    //adding database monitoring and server monitoring to the app list
                    //getting Server Monitoring info
                    Application[] server_monitoring_app = null;
                    Application[] database_monitoring_app = null;
                    int final_application_quantity = app_list.length;
                    if (alert_server_viz.equals("true")) {
                        full_url = controllerURL+"/controller/rest/applications/Server%20%26%20Infrastructure%20Monitoring?output=json";
                        curl = new CUrl(full_url);
                        curl.header(header);
                        server_monitoring_app = curl.exec(AppJsonResolver, null);
                        final_application_quantity+=1;
                    }
                    
                    //getting Database Monitoring info
                    
                    if (alert_db_viz.equals("true")){
                        full_url = controllerURL+"/controller/rest/applications/Database%20Monitoring?output=json";
                        curl = new CUrl(full_url);
                        curl.header(header);
                        database_monitoring_app = curl.exec(AppJsonResolver, null);
                        final_application_quantity+=1;
                    }
                    final_app_list = new Application[final_application_quantity];
                    int i=0;
                    for (Application app:app_list){
                        final_app_list[i] = app;
                        i++;                        
                    }
                    if (server_monitoring_app != null) {
                        final_app_list[i] = server_monitoring_app[0];
                        i++;
                    }
                    
                    if (database_monitoring_app !=  null){
                        final_app_list[i] = database_monitoring_app[0];
                    }
                }
                catch (Exception e){
                    LOGGER.log(Level.WARN, "{}: There was an error: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), e.getMessage()});
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
