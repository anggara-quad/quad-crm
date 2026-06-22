ALTER TABLE opportunities
    ADD COLUMN so_number VARCHAR(100),
    ADD COLUMN contract_po_number VARCHAR(100);

CREATE INDEX idx_opportunities_so_number ON opportunities(so_number);
CREATE INDEX idx_opportunities_contract_po_number ON opportunities(contract_po_number);
