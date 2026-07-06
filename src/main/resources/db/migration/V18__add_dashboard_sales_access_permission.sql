INSERT INTO role_view_permissions (role_id, view_code)
SELECT role.id, 'DASHBOARD_SALES'
FROM roles role
WHERE role.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
