package com.zynee.zynee.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class MoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int moodLevel;

    @ElementCollection
    private List<String> feelings;

    @ElementCollection
    @CollectionTable(name = "mood_log_trigger_tags", joinColumns = @JoinColumn(name = "mood_log_id"))
    @Column(name = "trigger_tag", length = 120)
    private List<String> triggerTags;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getMoodLevel() {
        return moodLevel;
    }

    public void setMoodLevel(int moodLevel) {
        this.moodLevel = moodLevel;
    }

    public List<String> getFeelings() {
        return feelings;
    }

    public void setFeelings(List<String> feelings) {
        this.feelings = feelings;
    }

    public List<String> getTriggerTags() {
        return triggerTags;
    }

    public void setTriggerTags(List<String> triggerTags) {
        this.triggerTags = triggerTags;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
