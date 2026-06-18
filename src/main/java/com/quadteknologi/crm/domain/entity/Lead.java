package com.quadteknologi.crm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "leads")
public class Lead extends AbstractAuditedEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "raw_company_name", length = 200)
    private String rawCompanyName;

    @Column(name = "raw_person_name", length = 150)
    private String rawPersonName;

    @Column(name = "raw_email", length = 150)
    private String rawEmail;

    @Column(name = "raw_phone", length = 50)
    private String rawPhone;

    @Column(length = 100)
    private String source;

    @Column(name = "status_group_code", nullable = false, length = 50)
    private String statusGroupCode = "LEAD_STATUS";

    @Column(name = "status_code", nullable = false, length = 50)
    private String statusCode = "NEW";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "status_group_code", referencedColumnName = "group_code", insertable = false, updatable = false),
            @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    })
    private OptionValue status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_by")
    private User convertedBy;

    @Column(name = "invalid_reason", columnDefinition = "text")
    private String invalidReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    public Lead() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public String getRawCompanyName() {
        return rawCompanyName;
    }

    public void setRawCompanyName(String rawCompanyName) {
        this.rawCompanyName = rawCompanyName;
    }

    public String getRawPersonName() {
        return rawPersonName;
    }

    public void setRawPersonName(String rawPersonName) {
        this.rawPersonName = rawPersonName;
    }

    public String getRawEmail() {
        return rawEmail;
    }

    public void setRawEmail(String rawEmail) {
        this.rawEmail = rawEmail;
    }

    public String getRawPhone() {
        return rawPhone;
    }

    public void setRawPhone(String rawPhone) {
        this.rawPhone = rawPhone;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatusGroupCode() {
        return statusGroupCode;
    }

    public void setStatusGroupCode(String statusGroupCode) {
        this.statusGroupCode = statusGroupCode;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public OptionValue getStatus() {
        return status;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public LocalDateTime getConvertedAt() {
        return convertedAt;
    }

    public void setConvertedAt(LocalDateTime convertedAt) {
        this.convertedAt = convertedAt;
    }

    public User getConvertedBy() {
        return convertedBy;
    }

    public void setConvertedBy(User convertedBy) {
        this.convertedBy = convertedBy;
    }

    public String getInvalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(String invalidReason) {
        this.invalidReason = invalidReason;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }
}
