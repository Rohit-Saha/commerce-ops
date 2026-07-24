package com.commerceops.invoice.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.invoice.client.OrderPaymentClient;
import com.commerceops.invoice.client.OrderPaymentClient.OrderSnapshot;
import com.commerceops.invoice.config.InvoiceProperties;
import com.commerceops.invoice.domain.Invoice;
import com.commerceops.invoice.domain.InvoiceLine;
import com.commerceops.invoice.domain.InvoiceStatus;
import com.commerceops.invoice.repository.InvoiceRepository;
import com.commerceops.invoice.web.dto.InvoiceLineResponse;
import com.commerceops.invoice.web.dto.InvoiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final OrderPaymentClient orderPaymentClient;
    private final InvoiceProperties invoiceProperties;
    private final InvoicePdfRenderer pdfRenderer;
    private final OutboxService outboxService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            OrderPaymentClient orderPaymentClient,
            InvoiceProperties invoiceProperties,
            InvoicePdfRenderer pdfRenderer,
            OutboxService outboxService) {
        this.invoiceRepository = invoiceRepository;
        this.orderPaymentClient = orderPaymentClient;
        this.invoiceProperties = invoiceProperties;
        this.pdfRenderer = pdfRenderer;
        this.outboxService = outboxService;
    }

    @Transactional
    public Invoice issueForShipment(Payloads.ShipmentCreated event, String correlationId, String causationId) {
        if (invoiceRepository.existsByOrderId(event.orderId())) {
            log.info("Invoice already exists for orderId={}, skipping", event.orderId());
            return invoiceRepository.findByOrderId(event.orderId()).orElseThrow();
        }

        OrderSnapshot order = orderPaymentClient.fetchOrder(event.orderId());
        String paymentRef = orderPaymentClient.fetchPaymentRef(event.orderId());
        String buyerState = order.shippingAddress() != null ? order.shippingAddress().state() : null;

        GstCalculator.InvoiceTax tax = GstCalculator.calculate(
                order.lines(), order.totalAmount(), buyerState, invoiceProperties);

        InvoiceProperties.Seller seller = invoiceProperties.getSeller();
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(nextInvoiceNumber());
        invoice.setOrderId(order.id());
        invoice.setShipmentId(event.shipmentId());
        invoice.setCustomerId(order.customerId());
        invoice.setCurrency(order.currency() != null ? order.currency() : "INR");
        invoice.setSubtotal(tax.subtotal());
        invoice.setCgst(tax.cgst());
        invoice.setSgst(tax.sgst());
        invoice.setIgst(tax.igst());
        invoice.setTotal(tax.total());
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setPaymentRef(paymentRef);
        invoice.setCreatedAt(Instant.now());

        invoice.setSellerLegalName(seller.getLegalName());
        invoice.setSellerGstin(seller.getGstin());
        invoice.setSellerAddress(seller.formattedAddress());
        invoice.setSellerState(seller.getState());
        invoice.setSellerStateCode(seller.getStateCode());

        if (order.shippingAddress() != null) {
            var ship = order.shippingAddress();
            invoice.setBuyerName(ship.recipientName());
            invoice.setBuyerLine1(ship.line1());
            invoice.setBuyerLine2(ship.line2());
            invoice.setBuyerCity(ship.city());
            invoice.setBuyerState(ship.state());
            invoice.setBuyerPostalCode(ship.postalCode());
            invoice.setBuyerCountry(ship.country());
        }

        int lineNo = 1;
        for (GstCalculator.LineTax lineTax : tax.lines()) {
            InvoiceLine line = new InvoiceLine();
            line.setLineNo(lineNo++);
            line.setSku(lineTax.sku());
            line.setDescription(lineTax.description());
            line.setQuantity(lineTax.quantity());
            line.setUnitPrice(lineTax.unitPrice());
            line.setLineGross(lineTax.lineGross());
            line.setTaxable(lineTax.taxable());
            line.setCgst(lineTax.cgst());
            line.setSgst(lineTax.sgst());
            line.setIgst(lineTax.igst());
            invoice.addLine(line);
        }

        invoice = invoiceRepository.save(invoice);
        invoice.setPdfBytes(pdfRenderer.render(invoice));
        invoice = invoiceRepository.save(invoice);

        publishIssued(invoice, correlationId, causationId);
        log.info("Issued invoice {} for orderId={} total={}",
                invoice.getInvoiceNumber(), invoice.getOrderId(), invoice.getTotal());
        return invoice;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list() {
        return invoiceRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return invoiceRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + id));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getByOrderId(String orderId) {
        return invoiceRepository.findByOrderId(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public byte[] getPdf(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + id));
        if (invoice.getPdfBytes() == null || invoice.getPdfBytes().length == 0) {
            throw new IllegalStateException("PDF missing for invoice " + id);
        }
        return invoice.getPdfBytes();
    }

    private String nextInvoiceNumber() {
        long seq = invoiceRepository.nextInvoiceSequence();
        return String.format("INV-%d-%06d", Year.now().getValue(), seq);
    }

    private void publishIssued(Invoice invoice, String correlationId, String causationId) {
        Payloads.InvoiceIssued payload = new Payloads.InvoiceIssued(
                invoice.getOrderId(),
                String.valueOf(invoice.getId()),
                invoice.getInvoiceNumber());
        EventEnvelope envelope = EventEnvelope.of(
                EventTypes.INVOICE_ISSUED,
                invoice.getOrderId(),
                correlationId != null ? correlationId : invoice.getOrderId(),
                causationId,
                null,
                UUID.randomUUID().toString(),
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.INVOICE_EVENTS, envelope);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLineResponse> lines = invoice.getLines().stream()
                .map(l -> new InvoiceLineResponse(
                        l.getLineNo(),
                        l.getSku(),
                        l.getDescription(),
                        l.getQuantity(),
                        l.getUnitPrice(),
                        l.getLineGross(),
                        l.getTaxable(),
                        l.getCgst(),
                        l.getSgst(),
                        l.getIgst()))
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getOrderId(),
                invoice.getShipmentId(),
                invoice.getCustomerId(),
                invoice.getCurrency(),
                invoice.getSubtotal(),
                invoice.getCgst(),
                invoice.getSgst(),
                invoice.getIgst(),
                invoice.getTotal(),
                invoice.getStatus().name(),
                invoice.getBuyerName(),
                invoice.getBuyerLine1(),
                invoice.getBuyerLine2(),
                invoice.getBuyerCity(),
                invoice.getBuyerState(),
                invoice.getBuyerPostalCode(),
                invoice.getBuyerCountry(),
                invoice.getSellerLegalName(),
                invoice.getSellerGstin(),
                invoice.getSellerAddress(),
                invoice.getSellerState(),
                invoice.getSellerStateCode(),
                invoice.getPaymentRef(),
                lines,
                invoice.getCreatedAt());
    }
}
