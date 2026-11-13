ALTER TABLE opportunities
    ADD COLUMN invoice_date DATE,
    ADD COLUMN closed_date DATE;

INSERT INTO option_values (
    group_code, code, name, sort_order, color, icon, is_system, is_final, is_won, is_lost, metadata
) VALUES
      ('OPPORTUNITY_STATUS', 'INVOICED', 'Invoiced', 6, 'primary', 'FILE_TEXT', TRUE, FALSE, TRUE, FALSE,
       '{"showInPipeline": true, "allowEditAmount": false, "requireLostReason": false}'::jsonb),

      ('OPPORTUNITY_STATUS', 'CLOSED', 'Closed', 7, 'contrast', 'CHECK', TRUE, TRUE, TRUE, FALSE,
       '{"showInPipeline": true, "allowEditAmount": false, "requireLostReason": false}'::jsonb);
