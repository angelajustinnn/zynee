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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quick_checkin_entries")
public class QuickCheckinEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "questionnaire_version", nullable = false, length = 40)
    private String questionnaireVersion;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount;

    @Column(name = "response_confidence", nullable = false)
    private int responseConfidence;

    @Column(name = "average_score", nullable = false)
    private double averageScore;

    @Column(name = "mood_label", nullable = false, length = 80)
    private String moodLabel;

    @Column(name = "low_signal_count", nullable = false)
    private int lowSignalCount;

    @Column(name = "overall_mood_score")
    private Integer overallMoodScore;

    @Column(name = "stress_score")
    private Integer stressScore;

    @Column(name = "anxiety_score")
    private Integer anxietyScore;

    @Column(name = "energy_score")
    private Integer energyScore;

    @Column(name = "sleep_score")
    private Integer sleepScore;

    @Column(name = "focus_score")
    private Integer focusScore;

    @Column(name = "motivation_score")
    private Integer motivationScore;

    @Column(name = "social_score")
    private Integer socialScore;

    @Column(name = "physical_score")
    private Integer physicalScore;

    @Column(name = "regulation_score")
    private Integer regulationScore;

    @Column(name = "hope_score")
    private Integer hopeScore;

    @Column(name = "support_score")
    private Integer supportScore;

    @Column(name = "primary_trigger", length = 120)
    private String primaryTrigger;

    @Column(name = "quick_note", length = 600)
    private String quickNote;

    @ElementCollection
    @CollectionTable(name = "quick_checkin_trigger_tags", joinColumns = @JoinColumn(name = "quick_checkin_entry_id"))
    @Column(name = "trigger_tag", length = 120)
    private List<String> moodTriggerTags;

    @Lob
    @Column(name = "answers_json", nullable = false)
    private String answersJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getQuestionnaireVersion() {
        return questionnaireVersion;
    }

    public void setQuestionnaireVersion(String questionnaireVersion) {
        this.questionnaireVersion = questionnaireVersion;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public void setAnsweredCount(int answeredCount) {
        this.answeredCount = answeredCount;
    }

    public int getResponseConfidence() {
        return responseConfidence;
    }

    public void setResponseConfidence(int responseConfidence) {
        this.responseConfidence = responseConfidence;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public String getMoodLabel() {
        return moodLabel;
    }

    public void setMoodLabel(String moodLabel) {
        this.moodLabel = moodLabel;
    }

    public int getLowSignalCount() {
        return lowSignalCount;
    }

    public void setLowSignalCount(int lowSignalCount) {
        this.lowSignalCount = lowSignalCount;
    }

    public Integer getOverallMoodScore() {
        return overallMoodScore;
    }

    public void setOverallMoodScore(Integer overallMoodScore) {
        this.overallMoodScore = overallMoodScore;
    }

    public Integer getStressScore() {
        return stressScore;
    }

    public void setStressScore(Integer stressScore) {
        this.stressScore = stressScore;
    }

    public Integer getAnxietyScore() {
        return anxietyScore;
    }

    public void setAnxietyScore(Integer anxietyScore) {
        this.anxietyScore = anxietyScore;
    }

    public Integer getEnergyScore() {
        return energyScore;
    }

    public void setEnergyScore(Integer energyScore) {
        this.energyScore = energyScore;
    }

    public Integer getSleepScore() {
        return sleepScore;
    }

    public void setSleepScore(Integer sleepScore) {
        this.sleepScore = sleepScore;
    }

    public Integer getFocusScore() {
        return focusScore;
    }

    public void setFocusScore(Integer focusScore) {
        this.focusScore = focusScore;
    }

    public Integer getMotivationScore() {
        return motivationScore;
    }

    public void setMotivationScore(Integer motivationScore) {
        this.motivationScore = motivationScore;
    }

    public Integer getSocialScore() {
        return socialScore;
    }

    public void setSocialScore(Integer socialScore) {
        this.socialScore = socialScore;
    }

    public Integer getPhysicalScore() {
        return physicalScore;
    }

    public void setPhysicalScore(Integer physicalScore) {
        this.physicalScore = physicalScore;
    }

    public Integer getRegulationScore() {
        return regulationScore;
    }

    public void setRegulationScore(Integer regulationScore) {
        this.regulationScore = regulationScore;
    }

    public Integer getHopeScore() {
        return hopeScore;
    }

    public void setHopeScore(Integer hopeScore) {
        this.hopeScore = hopeScore;
    }

    public Integer getSupportScore() {
        return supportScore;
    }

    public void setSupportScore(Integer supportScore) {
        this.supportScore = supportScore;
    }

    public String getPrimaryTrigger() {
        return primaryTrigger;
    }

    public void setPrimaryTrigger(String primaryTrigger) {
        this.primaryTrigger = primaryTrigger;
    }

    public String getQuickNote() {
        return quickNote;
    }

    public void setQuickNote(String quickNote) {
        this.quickNote = quickNote;
    }

    public List<String> getMoodTriggerTags() {
        return moodTriggerTags;
    }

    public void setMoodTriggerTags(List<String> moodTriggerTags) {
        this.moodTriggerTags = moodTriggerTags;
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public void setAnswersJson(String answersJson) {
        this.answersJson = answersJson;
    }
}
