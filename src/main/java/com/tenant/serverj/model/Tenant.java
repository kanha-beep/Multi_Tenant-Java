package com.tenant.serverj.model;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("tenants")
public class Tenant {
    @Id
    private String id;
    private String name;
    private String displayName;
    private String plan = "free";
    private String noteLimit;
    private Integer paidUsers = 0;
    private Map<String, Object> billing = new LinkedHashMap<String, Object>();
    private Map<String, Object> settings = new LinkedHashMap<String, Object>();
    private List<Map<String, Object>> templates;
    private Date createdAt;
    private Date updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getNoteLimit() {
        return noteLimit;
    }

    public void setNoteLimit(String noteLimit) {
        this.noteLimit = noteLimit;
    }

    public Integer getPaidUsers() {
        return paidUsers;
    }

    public void setPaidUsers(Integer paidUsers) {
        this.paidUsers = paidUsers;
    }

    public Map<String, Object> getBilling() {
        return billing;
    }

    public void setBilling(Map<String, Object> billing) {
        this.billing = billing;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public List<Map<String, Object>> getTemplates() {
        return templates;
    }

    public void setTemplates(List<Map<String, Object>> templates) {
        this.templates = templates;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
