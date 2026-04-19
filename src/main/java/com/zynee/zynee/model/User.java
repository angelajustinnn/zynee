package com.zynee.zynee.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "last_name_change_date")
    private LocalDate lastNameChangeDate;

    private String profileImage;

    @Column(name = "initials")
    private String initials;

    @Column(name = "profile_color")
    private String profileColor;

    private String tempPassword;
    private boolean tempPasswordActive;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;
    private String gender;

    private LocalDate dob;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "rps_max_streak")
    private Integer rpsMaxStreak;

    @Column(nullable = false)
    private String password;

    private LocalDate createdAt;

    @Column(name = "theme_mode")
    private String themeMode;

    @Column(name = "theme_hue")
    private String themeHue;

    @Column(name = "profile_photo")
    private String profilePhoto;

    @Column(name = "mood_forecast_date")
    private LocalDate moodForecastDate;

    @Column(name = "today_mood_prediction")
    private String todayMoodPrediction;

    @Column(name = "today_mood_confidence")
    private Integer todayMoodConfidence;

    @Column(name = "tomorrow_mood_prediction")
    private String tomorrowMoodPrediction;

    @Column(name = "tomorrow_mood_confidence")
    private Integer tomorrowMoodConfidence;

    @Column(name = "journal_pin_hash")
    private String journalPinHash;

    @Column(name = "journal_pin_length")
    private Integer journalPinLength;

    @Column(name = "journal_pin_failed_attempts")
    private Integer journalPinFailedAttempts;

    @Column(name = "journal_pin_lock_until")
    private LocalDateTime journalPinLockUntil;

    @Column(name = "journal_pin_lock_level")
    private Integer journalPinLockLevel;

    @Column(name = "data_analysis_consent")
    private Boolean dataAnalysisConsent;

    @Column(name = "onboarding_tour_completed")
    private Boolean onboardingTourCompleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getLastNameChangeDate() {
        return lastNameChangeDate;
    }

    public void setLastNameChangeDate(LocalDate lastNameChangeDate) {
        this.lastNameChangeDate = lastNameChangeDate;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getProfileColor() {
        return profileColor;
    }

    public void setProfileColor(String profileColor) {
        this.profileColor = profileColor;
    }

    public String getTempPassword() {
        return tempPassword;
    }

    public void setTempPassword(String tempPassword) {
        this.tempPassword = tempPassword;
    }

    public boolean isTempPasswordActive() {
        return tempPasswordActive;
    }

    public void setTempPasswordActive(boolean tempPasswordActive) {
        this.tempPasswordActive = tempPasswordActive;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public Integer getRpsMaxStreak() {
        return rpsMaxStreak;
    }

    public void setRpsMaxStreak(Integer rpsMaxStreak) {
        this.rpsMaxStreak = rpsMaxStreak;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode;
    }

    public String getThemeHue() {
        return themeHue;
    }

    public void setThemeHue(String themeHue) {
        this.themeHue = themeHue;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    public LocalDate getMoodForecastDate() {
        return moodForecastDate;
    }

    public void setMoodForecastDate(LocalDate moodForecastDate) {
        this.moodForecastDate = moodForecastDate;
    }

    public String getTodayMoodPrediction() {
        return todayMoodPrediction;
    }

    public void setTodayMoodPrediction(String todayMoodPrediction) {
        this.todayMoodPrediction = todayMoodPrediction;
    }

    public Integer getTodayMoodConfidence() {
        return todayMoodConfidence;
    }

    public void setTodayMoodConfidence(Integer todayMoodConfidence) {
        this.todayMoodConfidence = todayMoodConfidence;
    }

    public String getTomorrowMoodPrediction() {
        return tomorrowMoodPrediction;
    }

    public void setTomorrowMoodPrediction(String tomorrowMoodPrediction) {
        this.tomorrowMoodPrediction = tomorrowMoodPrediction;
    }

    public Integer getTomorrowMoodConfidence() {
        return tomorrowMoodConfidence;
    }

    public void setTomorrowMoodConfidence(Integer tomorrowMoodConfidence) {
        this.tomorrowMoodConfidence = tomorrowMoodConfidence;
    }

    public String getJournalPinHash() {
        return journalPinHash;
    }

    public void setJournalPinHash(String journalPinHash) {
        this.journalPinHash = journalPinHash;
    }

    public Integer getJournalPinLength() {
        return journalPinLength;
    }

    public void setJournalPinLength(Integer journalPinLength) {
        this.journalPinLength = journalPinLength;
    }

    public Integer getJournalPinFailedAttempts() {
        return journalPinFailedAttempts;
    }

    public void setJournalPinFailedAttempts(Integer journalPinFailedAttempts) {
        this.journalPinFailedAttempts = journalPinFailedAttempts;
    }

    public LocalDateTime getJournalPinLockUntil() {
        return journalPinLockUntil;
    }

    public void setJournalPinLockUntil(LocalDateTime journalPinLockUntil) {
        this.journalPinLockUntil = journalPinLockUntil;
    }

    public Integer getJournalPinLockLevel() {
        return journalPinLockLevel;
    }

    public void setJournalPinLockLevel(Integer journalPinLockLevel) {
        this.journalPinLockLevel = journalPinLockLevel;
    }

    public Boolean getDataAnalysisConsent() {
        return dataAnalysisConsent;
    }

    public void setDataAnalysisConsent(Boolean dataAnalysisConsent) {
        this.dataAnalysisConsent = dataAnalysisConsent;
    }

    public boolean isDataAnalysisEnabled() {
        return Boolean.TRUE.equals(dataAnalysisConsent);
    }

    public boolean hasDataAnalysisConsentChoice() {
        return dataAnalysisConsent != null;
    }

    public Boolean getOnboardingTourCompleted() {
        return onboardingTourCompleted;
    }

    public void setOnboardingTourCompleted(Boolean onboardingTourCompleted) {
        this.onboardingTourCompleted = onboardingTourCompleted;
    }

    public boolean hasCompletedOnboardingTour() {
        // Backward compatibility: existing users may have null in DB.
        // Treat null as already completed so they are not forced into first-login flow.
        return onboardingTourCompleted == null || Boolean.TRUE.equals(onboardingTourCompleted);
    }

    public boolean needsFirstLoginOnboarding() {
        return Boolean.FALSE.equals(onboardingTourCompleted);
    }
}
