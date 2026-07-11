package com.tenant.serverj.model;

import java.util.Date;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notes")
public class Note{
    @Id;
    public String name;
    public void setName(String name){
        this.name = name;
    }
}