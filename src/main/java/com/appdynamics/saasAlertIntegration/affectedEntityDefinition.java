/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

/**
 *
 * @author igor.simoes
 */
public class affectedEntityDefinition {
    //"affectedEntityDefinition":   {
    private String entityType;//": "APPLICATION_COMPONENT_NODE",
    private String name;//": "SCM-SG1_ggadb010_SG1_11",
    private String entityId;//": 7846678
    
    public String getEntityType() {
          return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public String getName() {
          return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEntityId() {
          return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    
    @Override
    public String toString() {
        return "AffectedEntityDefinition [entityType=" + entityType + 
               ", name=" + name + 
               ", entityId=" +entityId + "]";
    }
  //},*/
}
