package com.commerceops.invoice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "shipment_id")
    private String shipmentId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cgst = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sgst = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal igst = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_line1")
    private String buyerLine1;

    @Column(name = "buyer_line2")
    private String buyerLine2;

    @Column(name = "buyer_city")
    private String buyerCity;

    @Column(name = "buyer_state")
    private String buyerState;

    @Column(name = "buyer_postal_code")
    private String buyerPostalCode;

    @Column(name = "buyer_country")
    private String buyerCountry;

    @Column(name = "seller_legal_name", nullable = false)
    private String sellerLegalName;

    @Column(name = "seller_gstin", nullable = false)
    private String sellerGstin;

    @Column(name = "seller_address", nullable = false, columnDefinition = "TEXT")
    private String sellerAddress;

    @Column(name = "seller_state", nullable = false)
    private String sellerState;

    @Column(name = "seller_state_code", nullable = false)
    private String sellerStateCode;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Column(name = "pdf_bytes")
    private byte[] pdfBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    public Long getId() { return id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getShipmentId() { return shipmentId; }
    public void setShipmentId(String shipmentId) { this.shipmentId = shipmentId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getCgst() { return cgst; }
    public void setCgst(BigDecimal cgst) { this.cgst = cgst; }

    public BigDecimal getSgst() { return sgst; }
    public void setSgst(BigDecimal sgst) { this.sgst = sgst; }

    public BigDecimal getIgst() { return igst; }
    public void setIgst(BigDecimal igst) { this.igst = igst; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBuyerLine1() { return buyerLine1; }
    public void setBuyerLine1(String buyerLine1) { this.buyerLine1 = buyerLine1; }

    public String getBuyerLine2() { return buyerLine2; }
    public void setBuyerLine2(String buyerLine2) { this.buyerLine2 = buyerLine2; }

    public String getBuyerCity() { return buyerCity; }
    public void setBuyerCity(String buyerCity) { this.buyerCity = buyerCity; }

    public String getBuyerState() { return buyerState; }
    public void setBuyerState(String buyerState) { this.buyerState = buyerState; }

    public String getBuyerPostalCode() { return buyerPostalCode; }
    public void setBuyerPostalCode(String buyerPostalCode) { this.buyerPostalCode = buyerPostalCode; }

    public String getBuyerCountry() { return buyerCountry; }
    public void setBuyerCountry(String buyerCountry) { this.buyerCountry = buyerCountry; }

    public String getSellerLegalName() { return sellerLegalName; }
    public void setSellerLegalName(String sellerLegalName) { this.sellerLegalName = sellerLegalName; }

    public String getSellerGstin() { return sellerGstin; }
    public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }

    public String getSellerAddress() { return sellerAddress; }
    public void setSellerAddress(String sellerAddress) { this.sellerAddress = sellerAddress; }

    public String getSellerState() { return sellerState; }
    public void setSellerState(String sellerState) { this.sellerState = sellerState; }

    public String getSellerStateCode() { return sellerStateCode; }
    public void setSellerStateCode(String sellerStateCode) { this.sellerStateCode = sellerStateCode; }

    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }

    public byte[] getPdfBytes() { return pdfBytes; }
    public void setPdfBytes(byte[] pdfBytes) { this.pdfBytes = pdfBytes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<InvoiceLine> getLines() { return lines; }

    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }
}
