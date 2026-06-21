package com.quadteknologi.crm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "lead_items")
public class LeadItem extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(name = "product_type_group_code", nullable = false, length = 50)
    private String productTypeGroupCode = "PRODUCT_TYPE";

    @Column(name = "product_type_code", nullable = false, length = 50)
    private String productTypeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "product_type_group_code", referencedColumnName = "group_code", insertable = false, updatable = false),
            @JoinColumn(name = "product_type_code", referencedColumnName = "code", insertable = false, updatable = false)
    })
    private OptionValue productType;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "estimated_unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedUnitPrice = BigDecimal.ZERO;

    @Column(name = "estimated_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedTotal = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public String getProductTypeGroupCode() {
        return productTypeGroupCode;
    }

    public void setProductTypeGroupCode(String productTypeGroupCode) {
        this.productTypeGroupCode = productTypeGroupCode;
    }

    public String getProductTypeCode() {
        return productTypeCode;
    }

    public void setProductTypeCode(String productTypeCode) {
        this.productTypeCode = productTypeCode;
    }

    public OptionValue getProductType() {
        return productType;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getEstimatedUnitPrice() {
        return estimatedUnitPrice;
    }

    public void setEstimatedUnitPrice(BigDecimal estimatedUnitPrice) {
        this.estimatedUnitPrice = estimatedUnitPrice;
    }

    public BigDecimal getEstimatedTotal() {
        return estimatedTotal;
    }

    public void setEstimatedTotal(BigDecimal estimatedTotal) {
        this.estimatedTotal = estimatedTotal;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
