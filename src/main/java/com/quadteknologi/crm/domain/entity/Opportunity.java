package com.quadteknologi.crm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "opportunities")
public class Opportunity extends AbstractAuditedEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "status_group_code", nullable = false, length = 50)
    private String statusGroupCode = "OPPORTUNITY_STATUS";

    @Column(name = "status_code", nullable = false, length = 50)
    private String statusCode = "PRODUCT_SOLUTIONING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "status_group_code", referencedColumnName = "group_code", insertable = false, updatable = false),
            @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    })
    private OptionValue status;

    @Column(name = "estimated_amount", precision = 18, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal margin = BigDecimal.ZERO;

    private Integer probability;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "won_at")
    private LocalDateTime wonAt;

    @Column(name = "lost_at")
    private LocalDateTime lostAt;

    @Column(name = "lost_reason", columnDefinition = "text")
    private String lostReason;

    @Column(name = "so_number", length = 100)
    private String soNumber;

    @Column(name = "contract_po_number", length = 100)
    private String contractPoNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    public Opportunity() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
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

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public void setEstimatedAmount(BigDecimal estimatedAmount) {
        this.estimatedAmount = estimatedAmount;
    }

    public BigDecimal getMargin() {
        return margin;
    }

    public void setMargin(BigDecimal margin) {
        this.margin = margin;
    }

    public Integer getProbability() {
        return probability;
    }

    public void setProbability(Integer probability) {
        this.probability = probability;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
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

    public LocalDateTime getWonAt() {
        return wonAt;
    }

    public void setWonAt(LocalDateTime wonAt) {
        this.wonAt = wonAt;
    }

    public LocalDateTime getLostAt() {
        return lostAt;
    }

    public void setLostAt(LocalDateTime lostAt) {
        this.lostAt = lostAt;
    }

    public String getLostReason() {
        return lostReason;
    }

    public void setLostReason(String lostReason) {
        this.lostReason = lostReason;
    }

    public String getSoNumber() {
        return soNumber;
    }

    public void setSoNumber(String soNumber) {
        this.soNumber = soNumber;
    }

    public String getContractPoNumber() {
        return contractPoNumber;
    }

    public void setContractPoNumber(String contractPoNumber) {
        this.contractPoNumber = contractPoNumber;
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
