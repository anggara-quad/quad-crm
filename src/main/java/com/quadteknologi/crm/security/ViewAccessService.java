package com.quadteknologi.crm.security;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.views.ForbiddenView;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ViewAccessService {

    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbcTemplate;

    public ViewAccessService(CurrentUserService currentUserService, JdbcTemplate jdbcTemplate) {
        this.currentUserService = currentUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public boolean canAccess(AppViewAccess view) {
        Optional<User> user = currentUserService.getUser();
        if (user.isEmpty() || user.get().getId() == null) {
            return false;
        }

        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from role_view_permissions permission
                join user_roles user_role on user_role.role_id = permission.role_id
                where user_role.user_id = ?
                  and permission.view_code = ?
                """, Integer.class, user.get().getId(), view.code());
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public void checkBeforeEnter(BeforeEnterEvent event, AppViewAccess view) {
        if (!canAccess(view)) {
            event.rerouteTo(ForbiddenView.class);
        }
    }
}
