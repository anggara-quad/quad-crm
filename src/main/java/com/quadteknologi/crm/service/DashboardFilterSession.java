package com.quadteknologi.crm.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
public class DashboardFilterSession {

    private DashboardFilter dashboard;
    private DashboardFilter sales;

    public DashboardFilter getDashboard() {
        return dashboard;
    }

    public void setDashboard(DashboardFilter dashboard) {
        this.dashboard = dashboard;
    }

    public DashboardFilter getSales() {
        return sales;
    }

    public void setSales(DashboardFilter sales) {
        this.sales = sales;
    }
}
