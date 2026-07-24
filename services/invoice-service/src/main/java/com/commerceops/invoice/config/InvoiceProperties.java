package com.commerceops.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "commerce.invoice")
public class InvoiceProperties {

    private BigDecimal taxRate = new BigDecimal("0.18");
    private String orderServiceUrl = "http://localhost:8081";
    private String paymentServiceUrl = "http://localhost:8083";
    private final Seller seller = new Seller();

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public String getOrderServiceUrl() {
        return orderServiceUrl;
    }

    public void setOrderServiceUrl(String orderServiceUrl) {
        this.orderServiceUrl = orderServiceUrl;
    }

    public String getPaymentServiceUrl() {
        return paymentServiceUrl;
    }

    public void setPaymentServiceUrl(String paymentServiceUrl) {
        this.paymentServiceUrl = paymentServiceUrl;
    }

    public Seller getSeller() {
        return seller;
    }

    public static class Seller {
        private String legalName = "Northline Goods Private Limited";
        private String gstin = "29AABCU9603R1ZM";
        private String addressLine1 = "12 Indiranagar 100 Feet Road";
        private String city = "Bengaluru";
        private String state = "Karnataka";
        private String stateCode = "KA";
        private String postalCode = "560038";
        private String country = "IN";

        public String getLegalName() {
            return legalName;
        }

        public void setLegalName(String legalName) {
            this.legalName = legalName;
        }

        public String getGstin() {
            return gstin;
        }

        public void setGstin(String gstin) {
            this.gstin = gstin;
        }

        public String getAddressLine1() {
            return addressLine1;
        }

        public void setAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getStateCode() {
            return stateCode;
        }

        public void setStateCode(String stateCode) {
            this.stateCode = stateCode;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String formattedAddress() {
            return addressLine1 + ", " + city + ", " + state + " " + postalCode + ", " + country;
        }
    }
}
