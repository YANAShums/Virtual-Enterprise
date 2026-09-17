package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
    @Column(nullable = false)
    private boolean active = true;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();
    @Version private Long version;
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getStatus() { return status; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return roles; }
    @Column(nullable=false) private long securityRevision;
    public long getSecurityRevision() { return securityRevision; }
    public void revokeSessions() { securityRevision++; }
    public Long getVersion() { return version; }
    public void setUsername(String value) { this.username = value; }
    public void setEmail(String value) { this.email = value; }
    public void setPasswordHash(String value) { this.passwordHash = value; }
    public void setStatus(String value) { this.status = value; }
    public void setActive(boolean value) { this.active = value; }
    public void setRoles(Set<Role> value) { this.roles = value; }
}
