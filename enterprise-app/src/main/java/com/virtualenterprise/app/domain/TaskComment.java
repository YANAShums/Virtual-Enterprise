package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="task_comments")
public class TaskComment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="task_id")
    private Task task;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="author_user_id")
    private AppUser author;
    @Column(nullable=false,length=4000)
    private String body;
    @Column(nullable=false,insertable=false,updatable=false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version private Long version;
    public Long getId(){return id;} public Task getTask(){return task;} public AppUser getAuthor(){return author;}
    public String getBody(){return body;} public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public Long getVersion(){return version;}
    public void setTask(Task v){task=v;} public void setAuthor(AppUser v){author=v;} public void setBody(String v){body=v;}
    public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}