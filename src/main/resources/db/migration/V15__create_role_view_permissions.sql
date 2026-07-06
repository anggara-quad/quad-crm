CREATE TABLE role_view_permissions (
    role_id BIGINT NOT NULL,
    view_code VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (role_id, view_code),

    CONSTRAINT fk_role_view_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_view_permissions_view_code ON role_view_permissions(view_code);

INSERT INTO role_view_permissions (role_id, view_code)
SELECT role.id, permission.view_code
FROM roles role
JOIN (
    VALUES
        ('ADMIN', 'DASHBOARD'),
        ('MANAGER', 'DASHBOARD'),
        ('SALES', 'DASHBOARD'),

        ('MANAGER', 'DASHBOARD_MANAGER'),

        ('ADMIN', 'LEADS'),
        ('MANAGER', 'LEADS'),
        ('SALES', 'LEADS'),

        ('ADMIN', 'OPPORTUNITIES'),
        ('MANAGER', 'OPPORTUNITIES'),
        ('SALES', 'OPPORTUNITIES'),

        ('ADMIN', 'CONTACT'),
        ('MANAGER', 'CONTACT'),
        ('SALES', 'CONTACT'),

        ('ADMIN', 'COMPANIES'),
        ('MANAGER', 'COMPANIES'),
        ('SALES', 'COMPANIES'),

        ('ADMIN', 'PERSONS'),
        ('MANAGER', 'PERSONS'),
        ('SALES', 'PERSONS'),

        ('ADMIN', 'USER_SETTINGS')
) AS permission(role_code, view_code) ON permission.role_code = role.code;
