-- Link companies to country, province, and city/regency master data.
-- Existing text columns are intentionally kept temporarily to avoid accidental data loss.

ALTER TABLE companies
    ADD COLUMN country_id BIGINT,
    ADD COLUMN province_id BIGINT,
    ADD COLUMN city_id BIGINT;

ALTER TABLE companies
    ADD CONSTRAINT fk_companies_country
        FOREIGN KEY (country_id)
        REFERENCES countries(id),

    ADD CONSTRAINT fk_companies_province
        FOREIGN KEY (province_id)
        REFERENCES regions(id),

    ADD CONSTRAINT fk_companies_city
        FOREIGN KEY (city_id)
        REFERENCES regions(id);

CREATE INDEX idx_companies_country_id
    ON companies(country_id);

CREATE INDEX idx_companies_province_id
    ON companies(province_id);

CREATE INDEX idx_companies_city_id
    ON companies(city_id);
