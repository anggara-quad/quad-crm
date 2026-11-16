package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.InternalCompanyService;
import com.quadteknologi.crm.ui.component.MasterDetailCrud;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;

import static com.quadteknologi.crm.ui.util.InternalCompanyUi.logo;
import static com.quadteknologi.crm.ui.util.InternalCompanyUi.swatch;
import static com.quadteknologi.crm.ui.util.UiNotifications.showError;
import static com.quadteknologi.crm.ui.util.UiNotifications.showSuccess;

@PermitAll
@PageTitle("Internal Companies | Quad CRM")
@Route(value = "settings/internal-companies", layout = MainLayout.class)
public class InternalCompaniesView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final int MAX_LOGO_BYTES = 512 * 1024;

    private final InternalCompanyService internalCompanyService;
    private final ViewAccessService viewAccessService;
    private final MasterDetailCrud<InternalCompany> companyCrud;

    public InternalCompaniesView(
            InternalCompanyService internalCompanyService,
            ViewAccessService viewAccessService) {
        this.internalCompanyService = internalCompanyService;
        this.viewAccessService = viewAccessService;

        addClassNames("page-view", "contact-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        companyCrud = new MasterDetailCrud<>(
                "Internal Companies",
                "New Company",
                InternalCompany.class,
                InternalCompany::new,
                internalCompanyService::findAllCompanies,
                this::createCompanyForm);
        configureGrid(companyCrud.getGrid());
        companyCrud.refresh();

        add(createHeader(), companyCrud);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        viewAccessService.checkBeforeEnter(event, AppViewAccess.INTERNAL_COMPANIES);
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("pipeline-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("pipeline-title-group");

        H2 title = new H2("Internal Companies");
        Paragraph subtitle = new Paragraph("Company identity used by the sidebar, theme color, leads and opportunities.");
        titleGroup.add(title, subtitle);

        header.add(titleGroup);
        return header;
    }

    private void configureGrid(Grid<InternalCompany> grid) {
        grid.addComponentColumn(company -> logo(company, internalCompanyService))
                .setHeader("")
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(InternalCompany::getName)
                .setHeader("Name")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addComponentColumn(company -> colorCell(company.getPrimaryColor()))
                .setHeader("Color")
                .setAutoWidth(true);
        grid.addColumn(company -> Boolean.FALSE.equals(company.getActive()) ? "Inactive" : "Active")
                .setHeader("Status")
                .setAutoWidth(true);
        grid.addColumn(company -> company.getUpdatedAt() == null
                ? ""
                : company.getUpdatedAt().format(DATE_FORMAT))
                .setHeader("Updated")
                .setAutoWidth(true);
    }

    private Component colorCell(String color) {
        Div cell = new Div(swatch(color), new Span(InternalCompanyService.normalizeColor(color)));
        cell.addClassName("internal-company-color-cell");
        return cell;
    }

    private Component createCompanyForm(InternalCompany company) {
        boolean editing = company.getId() != null;
        Binder<InternalCompanyService.InternalCompanyForm> binder =
                new Binder<>(InternalCompanyService.InternalCompanyForm.class);

        InternalCompanyService.InternalCompanyForm form = new InternalCompanyService.InternalCompanyForm();
        form.setId(company.getId());
        form.setName(company.getName());
        form.setLogoPath(company.getLogoPath());
        form.setPrimaryColor(company.getPrimaryColor() == null ? "#009689" : company.getPrimaryColor());
        form.setActive(company.getId() == null || Boolean.TRUE.equals(company.getActive()));

        Div detail = createDetailShell(editing ? company.getName() : "New Company");

        TextField name = new TextField("Company name");
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);
        binder.forField(name)
                .asRequired("Company name is required")
                .bind(InternalCompanyService.InternalCompanyForm::getName,
                        InternalCompanyService.InternalCompanyForm::setName);

        TextField color = new TextField("Primary color");
        color.setWidthFull();
        color.setPlaceholder("#009689");
        color.setHelperText("Hex color, for example #009689.");
        color.setValue(form.getPrimaryColor());
        binder.forField(color)
                .asRequired("Primary color is required")
                .bind(InternalCompanyService.InternalCompanyForm::getPrimaryColor,
                        InternalCompanyService.InternalCompanyForm::setPrimaryColor);
        Div colorPreview = new Div();
        colorPreview.addClassName("internal-company-color-preview");
        colorPreview.add(swatch(color.getValue()), new Span(color.getValue()));
        color.addValueChangeListener(event -> {
            colorPreview.removeAll();
            colorPreview.add(swatch(event.getValue()), new Span(InternalCompanyService.normalizeColor(event.getValue())));
        });

        Div logoPreview = new Div();
        logoPreview.addClassName("internal-company-logo-preview");
        logoPreview.add(logo(company, internalCompanyService));

        // Uploaded bytes stay in memory until Save; the file is written only when the form is saved.
        LogoUpload logoUpload = new LogoUpload();
        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            logoUpload.bytes = bytes;
            logoUpload.contentType = metadata.contentType();
            logoPreview.removeAll();
            logoPreview.add(new Image(bytes, "Company logo", metadata.contentType()));
        }));
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/webp", "image/svg+xml");
        upload.setMaxFileSize(MAX_LOGO_BYTES);
        upload.setMaxFiles(1);
        upload.setDropAllowed(false);
        upload.setUploadButton(new Button("Upload logo", VaadinIcon.UPLOAD.create()));
        upload.addFileRejectedListener(event -> showError(event.getErrorMessage()));

        Checkbox active = new Checkbox("Active");
        binder.bind(active, InternalCompanyService.InternalCompanyForm::getActive,
                InternalCompanyService.InternalCompanyForm::setActive);

        binder.readBean(form);

        detail.add(name, logoPreview, upload, color, colorPreview, active,
                createCompanyActions(form, binder, logoUpload));
        return detail;
    }

    private Div createDetailShell(String title) {
        Div detail = new Div();
        detail.addClassName("contact-detail");

        H3 heading = new H3(title);
        heading.addClassName("contact-detail-title");
        detail.add(heading);

        return detail;
    }

    private Component createCompanyActions(
            InternalCompanyService.InternalCompanyForm form,
            Binder<InternalCompanyService.InternalCompanyForm> binder,
            LogoUpload logoUpload) {
        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(form);
            } catch (ValidationException exception) {
                showError("Please complete required fields");
                return;
            }

            String previousLogo = form.getLogoPath();
            String storedLogo = null;
            try {
                if (logoUpload.bytes != null) {
                    storedLogo = internalCompanyService.storeLogo(logoUpload.bytes, logoUpload.contentType);
                    form.setLogoPath(storedLogo);
                }
                internalCompanyService.save(form);
            } catch (IllegalArgumentException exception) {
                if (storedLogo != null) {
                    internalCompanyService.deleteLogo(storedLogo);
                }
                showError(exception.getMessage());
                return;
            }
            if (storedLogo != null && previousLogo != null && !previousLogo.equals(storedLogo)) {
                internalCompanyService.deleteLogo(previousLogo);
            }
            companyCrud.refresh();
            companyCrud.closeDetail();
            showSuccess("Company saved");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", event -> companyCrud.closeDetail());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, cancel);
        actions.addClassName("contact-form-actions");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private static class LogoUpload {
        private byte[] bytes;
        private String contentType;
    }
}
