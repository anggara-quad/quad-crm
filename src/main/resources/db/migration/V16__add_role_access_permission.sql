INSERT INTO role_view_permissions (role_id, view_code)
SELECT role.id, 'ROLE_ACCESS'
FROM roles role
WHERE role.code = 'ADMIN'
ON CONFLICT DO NOTHING;
