package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.InternalCompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalCompanyServiceTest {

    @Mock
    private InternalCompanyRepository internalCompanyRepository;
    @Mock
    private DataAccessService dataAccessService;

    @TempDir
    private Path uploadDir;

    private InternalCompanyService service;

    @BeforeEach
    void setUp() {
        service = new InternalCompanyService(internalCompanyRepository, dataAccessService, uploadDir.toString());
    }

    @Test
    void saveRejectsBlankName() {
        InternalCompanyService.InternalCompanyForm form = form("  ", "#009689");

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Company name is required");
    }

    @Test
    void saveRejectsMalformedColor() {
        InternalCompanyService.InternalCompanyForm form = form("Quad Nusantara", "00968");

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Company color must use #RRGGBB format");
    }

    @Test
    void saveRejectsDuplicateName() {
        when(internalCompanyRepository.existsByNameIgnoreCase("Quad Nusantara")).thenReturn(true);
        InternalCompanyService.InternalCompanyForm form = form("Quad Nusantara", "#112233");

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Company name already exists");
    }

    @Test
    void saveNormalizesColorAndAuditUser() {
        when(dataAccessService.requireCurrentUserReference()).thenReturn(mock(User.class));
        when(internalCompanyRepository.save(any(InternalCompany.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InternalCompanyService.InternalCompanyForm form = form("Quad Nusantara", "009689");

        InternalCompany saved = service.save(form);

        assertThat(saved.getPrimaryColor()).isEqualTo("#009689");
        assertThat(saved.getCreatedBy()).isNotNull();
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    void storedLogoIsReadBackWithItsContentType() {
        byte[] bytes = new byte[] {1, 2, 3};

        String path = service.storeLogo(bytes, "image/png");

        InternalCompany company = new InternalCompany();
        company.setName("Quad Nusantara");
        company.setLogoPath(path);
        Optional<InternalCompanyService.LogoData> logo = service.readLogo(company);

        assertThat(logo).isPresent();
        assertThat(logo.get().contentType()).isEqualTo("image/png");
        assertThat(logo.get().bytes()).containsExactly(bytes);
    }

    @Test
    void normalizeColorDefaultsAndAddsHash() {
        assertThat(InternalCompanyService.normalizeColor(null)).isEqualTo("#009689");
        assertThat(InternalCompanyService.normalizeColor(" ")).isEqualTo("#009689");
        assertThat(InternalCompanyService.normalizeColor("AABBCC")).isEqualTo("#AABBCC");
        assertThat(InternalCompanyService.normalizeColor("#AABBCC")).isEqualTo("#AABBCC");
    }

    private InternalCompanyService.InternalCompanyForm form(String name, String color) {
        InternalCompanyService.InternalCompanyForm form = new InternalCompanyService.InternalCompanyForm();
        form.setName(name);
        form.setPrimaryColor(color);
        return form;
    }
}
