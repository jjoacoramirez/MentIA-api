package com.mentia.gamification.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition", nullable = false)
    private Map<String, Object> condition;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Achievement() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getCondition() {
        return Map.copyOf(condition);
    }

    public int getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }
}
