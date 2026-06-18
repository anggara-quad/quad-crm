ALTER TABLE leads
    ADD COLUMN converted_at TIMESTAMP,
    ADD COLUMN converted_by BIGINT;

ALTER TABLE leads
    ADD CONSTRAINT fk_leads_converted_by
        FOREIGN KEY (converted_by) REFERENCES users(id);

UPDATE leads
SET converted_at = opportunities.created_at,
    converted_by = COALESCE(opportunities.created_by, leads.assigned_to)
FROM opportunities
WHERE opportunities.lead_id = leads.id
  AND leads.converted_at IS NULL;

CREATE UNIQUE INDEX uq_opportunities_lead_id
    ON opportunities(lead_id)
    WHERE lead_id IS NOT NULL;
