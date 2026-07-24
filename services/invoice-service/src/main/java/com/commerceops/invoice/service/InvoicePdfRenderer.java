package com.commerceops.invoice.service;

import com.commerceops.invoice.domain.Invoice;
import com.commerceops.invoice.domain.InvoiceLine;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public byte[] render(Invoice invoice) {
        try {
            String template = StreamUtils.copyToString(
                    new ClassPathResource("templates/invoice.html").getInputStream(),
                    StandardCharsets.UTF_8);
            String html = fill(template, invoice);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render invoice PDF", ex);
        }
    }

    private static String fill(String template, Invoice invoice) {
        StringBuilder rows = new StringBuilder();
        for (InvoiceLine line : invoice.getLines()) {
            rows.append("<tr>")
                    .append("<td>").append(esc(line.getSku())).append("</td>")
                    .append("<td class=\"num\">").append(line.getQuantity()).append("</td>")
                    .append("<td class=\"num\">").append(money(line.getUnitPrice())).append("</td>")
                    .append("<td class=\"num\">").append(money(line.getTaxable())).append("</td>")
                    .append("<td class=\"num\">").append(money(line.getCgst())).append("</td>")
                    .append("<td class=\"num\">").append(money(line.getSgst())).append("</td>")
                    .append("<td class=\"num\">").append(money(line.getIgst())).append("</td>")
                    .append("<td class=\"num\">").append(money(line.getLineGross())).append("</td>")
                    .append("</tr>");
        }

        String buyerAddress = joinNonBlank(
                invoice.getBuyerName(),
                invoice.getBuyerLine1(),
                invoice.getBuyerLine2(),
                joinNonBlank(invoice.getBuyerCity(), invoice.getBuyerState(), invoice.getBuyerPostalCode()),
                invoice.getBuyerCountry());

        return template
                .replace("{{invoiceNumber}}", esc(invoice.getInvoiceNumber()))
                .replace("{{issuedAt}}", DATE_FMT.format(invoice.getCreatedAt()))
                .replace("{{orderId}}", esc(invoice.getOrderId()))
                .replace("{{shipmentId}}", esc(nullToDash(invoice.getShipmentId())))
                .replace("{{paymentRef}}", esc(nullToDash(invoice.getPaymentRef())))
                .replace("{{sellerName}}", esc(invoice.getSellerLegalName()))
                .replace("{{sellerGstin}}", esc(invoice.getSellerGstin()))
                .replace("{{sellerAddress}}", esc(invoice.getSellerAddress()))
                .replace("{{sellerState}}", esc(invoice.getSellerState() + " (" + invoice.getSellerStateCode() + ")"))
                .replace("{{buyerAddress}}", esc(buyerAddress).replace("\n", "<br/>"))
                .replace("{{lineRows}}", rows.toString())
                .replace("{{subtotal}}", money(invoice.getSubtotal()))
                .replace("{{cgst}}", money(invoice.getCgst()))
                .replace("{{sgst}}", money(invoice.getSgst()))
                .replace("{{igst}}", money(invoice.getIgst()))
                .replace("{{total}}", money(invoice.getTotal()))
                .replace("{{currency}}", esc(invoice.getCurrency()));
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%,.2f", amount);
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(part);
        }
        return sb.toString();
    }
}
