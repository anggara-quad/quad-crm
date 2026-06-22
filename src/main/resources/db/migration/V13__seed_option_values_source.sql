INSERT INTO option_groups (code, name, description, is_system) VALUES
                                                                   ('SOURCE_TYPE', 'Source', 'Source for list', TRUE);
INSERT INTO option_values (
    group_code, code, name, sort_order, color, icon, is_system, is_final, metadata
) VALUES
      ('SOURCE_TYPE', 'EMAIL', 'Email', 1, 'primary', 'EMAIL', TRUE, FALSE,
       '{"allowConvertToOpportunity": false, "showInPipeline": true}'::jsonb),

      ('SOURCE_TYPE', 'WEB', 'Web', 2, 'warning', 'WEB', TRUE, FALSE,
       '{"allowConvertToOpportunity": false, "showInPipeline": true}'::jsonb),

      ('SOURCE_TYPE', 'PHONE', 'Phone', 3, 'success', 'PHONE', TRUE, TRUE,
       '{"allowConvertToOpportunity": false, "showInPipeline": true}'::jsonb),

      ('SOURCE_TYPE', 'DIRECT', 'Direct', 4, 'error', 'ARROW', TRUE, TRUE,
       '{"allowConvertToOpportunity": false, "showInPipeline": true, "requireInvalidReason": true}'::jsonb),

      ('SOURCE_TYPE', 'MANAGEMENT', 'Management', 5, 'error', 'USERS', TRUE, TRUE,
       '{"allowConvertToOpportunity": false, "showInPipeline": true, "requireInvalidReason": true}'::jsonb);

