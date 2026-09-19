package com.designart.admin;

import com.designart.admin.AdminBillingService.View;
import com.designart.admin.dto.AdminDtos.*;
import com.designart.billing.InvoiceStatus;
import com.designart.security.SuperAdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Financeiro SaaS do Nexus Control Center. Somente SUPER_ADMIN. O tenant alvo vem sempre do PATH (ou do
 * registro persistido da cobrança), nunca do corpo. Listagens paginadas (size máx. 100).
 */
@RestController
@RequestMapping("/api/admin")
@SuperAdminOnly
@RequiredArgsConstructor
public class AdminBillingController {

    private final AdminBillingService billing;
    private final AdminTenantService tenants;

    // ---- cobranças ----
    @GetMapping("/billing/invoices")
    public PageDto<InvoiceDto> invoices(@RequestParam(required = false) Long tenantId,
                                        @RequestParam(required = false) InvoiceStatus status,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listInvoices(View.ALL, tenantId, status, null, page, size);
    }

    @GetMapping("/billing/invoices/upcoming")
    public PageDto<InvoiceDto> upcoming(@RequestParam(required = false) Long tenantId, @RequestParam(required = false) Integer days,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listInvoices(View.UPCOMING, tenantId, null, days, page, size);
    }

    @GetMapping("/billing/invoices/overdue")
    public PageDto<InvoiceDto> overdue(@RequestParam(required = false) Long tenantId,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listInvoices(View.OVERDUE, tenantId, null, null, page, size);
    }

    @GetMapping("/billing/invoices/in-grace")
    public PageDto<InvoiceDto> inGrace(@RequestParam(required = false) Long tenantId,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listInvoices(View.IN_GRACE, tenantId, null, null, page, size);
    }

    /** Vencidas E fora da carência: candidatas à suspensão por inadimplência (decisão do SUPER_ADMIN). */
    @GetMapping("/billing/invoices/beyond-grace")
    public PageDto<InvoiceDto> beyondGrace(@RequestParam(required = false) Long tenantId,
                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listInvoices(View.BEYOND_GRACE, tenantId, null, null, page, size);
    }

    @GetMapping("/billing/invoices/{id}")
    public InvoiceDto invoice(@PathVariable Long id) {
        return billing.getInvoice(id);
    }

    @GetMapping("/billing/invoices/{id}/payments")
    public List<PaymentDto> invoicePayments(@PathVariable Long id) {
        return billing.paymentsOfInvoice(id);
    }

    @PostMapping("/billing/invoices/{id}/pay")
    public InvoiceDto pay(@PathVariable Long id, @RequestBody PayInvoiceRequest req) {
        return billing.pay(id, req);
    }

    @PostMapping("/billing/invoices/{id}/cancel")
    public InvoiceDto cancel(@PathVariable Long id) {
        return billing.cancel(id);
    }

    /** Persiste OPEN -> OVERDUE (idempotente). As leituras já fazem isso; útil para rotina futura. */
    @PostMapping("/billing/refresh")
    public RefreshResultDto refresh() {
        return billing.refresh();
    }

    @GetMapping("/billing/payments")
    public PageDto<PaymentDto> payments(@RequestParam(required = false) Long tenantId,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listPayments(tenantId, page, size);
    }

    @GetMapping("/billing/dashboard")
    public BillingDashboardDto dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Integer days) {
        return billing.dashboard(from, to, days);
    }

    // ---- por tenant ----
    @GetMapping("/tenants/{id}/billing")
    public TenantBillingDto tenantBilling(@PathVariable Long id) {
        return billing.tenantBilling(id);
    }

    @GetMapping("/tenants/{id}/billing-terms")
    public BillingTermsDto terms(@PathVariable Long id) {
        return billing.getTerms(id);
    }

    @PutMapping("/tenants/{id}/billing-terms")
    public BillingTermsDto putTerms(@PathVariable Long id, @RequestBody BillingTermsRequest req) {
        return billing.setTerms(id, req);
    }

    @GetMapping("/tenants/{id}/invoices")
    public PageDto<InvoiceDto> tenantInvoices(@PathVariable Long id, @RequestParam(required = false) InvoiceStatus status,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return billing.listInvoices(View.ALL, id, status, null, page, size);
    }

    /** 201 quando criou; 200 quando o período já tinha cobrança (idempotente, nada criado). */
    @PostMapping("/tenants/{id}/invoices")
    public ResponseEntity<InvoiceGenerationDto> generate(@PathVariable Long id, @RequestBody(required = false) GenerateInvoiceRequest req) {
        InvoiceGenerationDto dto = billing.generate(id, req);
        return ResponseEntity.status(dto.created() ? HttpStatus.CREATED : HttpStatus.OK).body(dto);
    }

    @PostMapping("/tenants/{id}/suspend-non-payment")
    public TenantDto suspendNonPayment(@PathVariable Long id) {
        return tenants.suspendForNonPayment(id);
    }
}
