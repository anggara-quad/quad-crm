INSERT INTO users (full_name, email, password_hash, is_active)
SELECT
    'Administrator',
    'admin@quadteknologi.com',
    '$2a$10$zFrzViBoTkcbqgRDVrW9FO7Hu3tmSLlNmd8od10HquPhfO3/NElzK',
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    WHERE r.name = 'Administrator'
)
AND NOT EXISTS (
    SELECT 1
    FROM users u
    WHERE LOWER(u.email) = LOWER('admin@quadteknologi.com')
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'Administrator'
WHERE LOWER(u.email) = LOWER('admin@quadteknologi.com')
AND NOT EXISTS (
    SELECT 1
    FROM user_roles ur
    JOIN roles admin_role ON admin_role.id = ur.role_id
    WHERE admin_role.name = 'Administrator'
)
ON CONFLICT DO NOTHING;
