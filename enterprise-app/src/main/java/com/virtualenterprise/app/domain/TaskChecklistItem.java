package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="task_checklist_items")
public class TaskChecklistItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="task_id")
    private Task task;
    @Column(nullable=false,length=500)
    private String title;
    @Column(nullable=false)
    private boolean completed;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="completed_by_user_id")
    private AppUser completedBy;
    private LocalDateTime completedAt;
    @Column(nullable=false)
    private Integer itemOrder=0;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="created_by_user_id")
    private AppUser createdBy;
    @Column(nullable=false,insertable=false,updatable=false)
    private LocalDateTime createdAt;
    @Version private Long version;
    public Long getId(){return id;} public Task getTask(){return task;} public String getTitle(){return title;}
    public boolean isCompleted(){return completed;} public AppUser getCompletedBy(){return completedBy;}
    public LocalDateTime getCompletedAt(){return completedAt;} public Integer getItemOrder(){return itemOrder;}
    public AppUser getCreatedBy(){return createdBy;} public LocalDateTime getCreatedAt(){return createdAt;} public Long getVersion(){return version;}
    public void setTask(Task v){task=v;} public void setTitle(String v){title=v;} public void setCompleted(boolean v){completed=v;}
    public void setCompletedBy(AppUser v){completedBy=v;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
    public void setItemOrder(Integer v){itemOrder=v;} public void setCreatedBy(AppUser v){createdBy=v;}
}