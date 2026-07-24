package com.commerceops.invoice.service;

import com.commerceops.invoice.client.OrderPaymentClient.OrderLine;
import com.commerceops.invoice.config.InvoiceProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GstCalculator {

    private GstCalculator() {}

    public record LineTax(
            String sku,
            String description,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineGross,
            BigDecimal taxable,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst
    ) {}

    public record InvoiceTax(
            BigDecimal subtotal,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal total,
            boolean intraState,
            List<LineTax> lines
    ) {}

    public static InvoiceTax calculate(
            List<OrderLine> orderLines,
            BigDecimal orderTotal,
            String buyerState,
            InvoiceProperties properties) {
        BigDecimal rate = properties.getTaxRate() != null ? properties.getTaxRate() : new BigDecimal("0.18");
        BigDecimal divisor = BigDecimal.ONE.add(rate);
        boolean intraState = isIntraState(buyerState, properties.getSeller());

        List<LineTax> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal grossSum = BigDecimal.ZERO;

        int idx = 0;
        for (OrderLine line : orderLines) {
            idx++;
            BigDecimal lineGross = line.unitPrice()
                    .multiply(BigDecimal.valueOf(line.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxable = lineGross.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal tax = lineGross.subtract(taxable);
            BigDecimal half = tax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            BigDecimal lineCgst = BigDecimal.ZERO;
            BigDecimal lineSgst = BigDecimal.ZERO;
            BigDecimal lineIgst = BigDecimal.ZERO;
            if (intraState) {
                lineCgst = half;
                lineSgst = tax.subtract(half);
            } else {
                lineIgst = tax;
            }
            lines.add(new LineTax(
                    line.sku(),
                    line.sku(),
                    line.quantity(),
                    line.unitPrice(),
                    lineGross,
                    taxable,
                    lineCgst,
                    lineSgst,
                    lineIgst));
            subtotal = subtotal.add(taxable);
            cgst = cgst.add(lineCgst);
            sgst = sgst.add(lineSgst);
            igst = igst.add(lineIgst);
            grossSum = grossSum.add(lineGross);
        }

        BigDecimal total = orderTotal != null ? orderTotal.setScale(2, RoundingMode.HALF_UP) : grossSum;
        // Absorb rounding drift into IGST or CGST so invoice total matches order total
        BigDecimal computed = subtotal.add(cgst).add(sgst).add(igst);
        BigDecimal drift = total.subtract(computed);
        if (drift.compareTo(BigDecimal.ZERO) != 0) {
            if (intraState) {
                cgst = cgst.add(drift);
            } else {
                igst = igst.add(drift);
            }
        }

        return new InvoiceTax(subtotal, cgst, sgst, igst, total, intraState, lines);
    }

    static boolean isIntraState(String buyerState, InvoiceProperties.Seller seller) {
        if (buyerState == null || buyerState.isBlank()) {
            return false;
        }
        String normalized = buyerState.trim().toLowerCase(Locale.ROOT);
        String sellerState = seller.getState() == null ? "" : seller.getState().trim().toLowerCase(Locale.ROOT);
        String sellerCode = seller.getStateCode() == null ? "" : seller.getStateCode().trim().toLowerCase(Locale.ROOT);
        return normalized.equals(sellerState) || normalized.equals(sellerCode);
    }
}
