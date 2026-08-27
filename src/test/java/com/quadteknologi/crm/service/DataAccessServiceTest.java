package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataAccessServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    @Test
    void managerCanLoadActiveSalesUsersForTeamFilter() {
        User sales = new User();
        sales.setFullName("Sales User");
        when(currentUserService.hasRole("MANAGER")).thenReturn(true);
        when(userRepository.findActiveSalesUsersOrderByFullNameAsc()).thenReturn(List.of(sales));

        DataAccessService service = new DataAccessService(currentUserService, userRepository);

        assertThat(service.canAccessTeamData()).isTrue();
        assertThat(service.findActiveSalesUsersForTeamFilter()).containsExactly(sales);
    }

    @Test
    void administratorCanAccessTeamFilter() {
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);

        DataAccessService service = new DataAccessService(currentUserService, userRepository);

        assertThat(service.canAccessTeamData()).isTrue();
    }

    @Test
    void salesOnlyUserCannotLoadTeamFilterOptions() {
        DataAccessService service = new DataAccessService(currentUserService, userRepository);

        assertThat(service.canAccessTeamData()).isFalse();
        assertThat(service.findActiveSalesUsersForTeamFilter()).isEmpty();
        verify(userRepository, never()).findActiveSalesUsersOrderByFullNameAsc();
    }
}
