package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.InternalCompanyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.quadteknologi.crm.util.TextUtils.trimToNull;

@Service
@Transactional(readOnly = true)
public class InternalCompanyService {

    private static final long MAX_LOGO_BYTES = 512 * 1024;
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final String LOGO_FOLDER = "logos";
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "svg", "image/svg+xml");

    private final InternalCompanyRepository internalCompanyRepository;
    private final DataAccessService dataAccessService;
    private final Path uploadRoot;

    public InternalCompanyService(
            InternalCompanyRepository internalCompanyRepository,
            DataAccessService dataAccessService,
            @Value("${crm.upload.logo-dir:./data/uploads}") String uploadDir) {
        this.internalCompanyRepository = internalCompanyRepository;
        this.dataAccessService = dataAccessService;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<InternalCompany> findActiveCompanies() {
        return internalCompanyRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<InternalCompany> findAllCompanies() {
        return internalCompanyRepository.findAllByOrderByNameAsc();
    }

    public Optional<InternalCompany> findByPublicId(UUID publicId) {
        return internalCompanyRepository.findByPublicId(publicId);
    }

    public Optional<InternalCompany> findById(Long id) {
        return internalCompanyRepository.findById(id);
    }

    public Optional<InternalCompany> findDefaultCompany() {
        return findActiveCompanies().stream().findFirst();
    }

    @Transactional
    public InternalCompany save(InternalCompanyForm form) {
        String name = trimToNull(form.getName());
        if (name == null) {
            throw new IllegalArgumentException("Company name is required");
        }
        String color = normalizeColor(form.getPrimaryColor());
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException("Company color must use #RRGGBB format");
        }

        InternalCompany company = form.getId() == null
                ? new InternalCompany()
                : internalCompanyRepository.findById(form.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Company was not found"));

        boolean nameTaken = form.getId() == null
                ? internalCompanyRepository.existsByNameIgnoreCase(name)
                : internalCompanyRepository.existsByNameIgnoreCaseAndIdNot(name, form.getId());
        if (nameTaken) {
            throw new IllegalArgumentException("Company name already exists");
        }

        User currentUser = dataAccessService.requireCurrentUserReference();
        company.setName(name);
        company.setPrimaryColor(color.toUpperCase(Locale.ROOT));
        company.setLogoPath(trimToNull(form.getLogoPath()));
        company.setActive(form.getActive() == null || form.getActive());
        if (company.getId() == null) {
            company.setCreatedBy(currentUser);
        }
        company.setUpdatedBy(currentUser);
        return internalCompanyRepository.save(company);
    }

    public String storeLogo(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Logo file is empty");
        }
        if (bytes.length > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("Logo must be 512 KB or smaller");
        }
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Logo must be PNG, JPEG, WebP or SVG");
        }

        String fileName = UUID.randomUUID() + "." + extension;
        Path target = logoRoot().resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new UncheckedIOException("Logo could not be saved", exception);
        }
        return LOGO_FOLDER + "/" + fileName;
    }

    public Optional<LogoData> readLogo(InternalCompany company) {
        String path = company == null ? null : trimToNull(company.getLogoPath());
        if (path == null) {
            return Optional.empty();
        }
        Path file = resolveInsideUploadRoot(path);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LogoData(Files.readAllBytes(file), contentTypeOf(file)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public void deleteLogo(String path) {
        Path file = resolveInsideUploadRoot(trimToNull(path));
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A stale logo file is harmless; the company row no longer references it.
        }
    }

    public static String normalizeColor(String color) {
        String value = trimToNull(color);
        if (value == null) {
            return "#009689";
        }
        return value.startsWith("#") ? value : "#" + value;
    }

    private Path logoRoot() {
        return uploadRoot.resolve(LOGO_FOLDER);
    }

    private Path resolveInsideUploadRoot(String path) {
        if (path == null) {
            return null;
        }
        Path file = uploadRoot.resolve(path).normalize();
        return file.startsWith(uploadRoot) ? file : null;
    }

    private String contentTypeOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return CONTENT_TYPE_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
    }

    public record LogoData(byte[] bytes, String contentType) {
    }

    public static class InternalCompanyForm {

        private Long id;
        private String name;
        private String logoPath;
        private String primaryColor = "#009689";
        private Boolean active = true;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLogoPath() {
            return logoPath;
        }

        public void setLogoPath(String logoPath) {
            this.logoPath = logoPath;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }
}
