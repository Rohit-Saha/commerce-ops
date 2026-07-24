package com.commerceops.customer.web;

import com.commerceops.common.web.ApiMessage;
import com.commerceops.common.web.BusinessException;
import com.commerceops.customer.service.AddressBookService;
import com.commerceops.customer.service.CustomerAuthService;
import com.commerceops.customer.web.dto.AddressRequest;
import com.commerceops.customer.web.dto.AddressResponse;
import com.commerceops.customer.web.dto.AuthResponse;
import com.commerceops.customer.web.dto.CustomerProfileResponse;
import com.commerceops.customer.web.dto.LoginRequest;
import com.commerceops.customer.web.dto.RegisterRequest;
import com.commerceops.customer.web.dto.UpdateProfileRequest;
import com.commerceops.customer.web.filter.CustomerJwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerAuthService authService;
    private final AddressBookService addressBookService;

    public CustomerController(CustomerAuthService authService, AddressBookService addressBookService) {
        this.authService = authService;
        this.addressBookService = addressBookService;
    }

    @ApiMessage("Account created successfully")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @ApiMessage("Signed in successfully")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CustomerProfileResponse me(HttpServletRequest request) {
        String id = customerId(request);
        try {
            return authService.me(id);
        } catch (com.commerceops.customer.service.exception.CustomerNotFoundException ex) {
            return authService.ensureOidcProfile(
                    id,
                    request.getHeader("X-Commerce-Email"),
                    request.getHeader("X-Commerce-Display-Name"));
        }
    }

    @ApiMessage("Profile updated successfully")
    @PutMapping("/me")
    public CustomerProfileResponse updateMe(
            HttpServletRequest request, @Valid @RequestBody UpdateProfileRequest body) {
        return authService.updateProfile(customerId(request), body);
    }

    @GetMapping("/me/addresses")
    public List<AddressResponse> listAddresses(HttpServletRequest request) {
        return addressBookService.list(customerId(request));
    }

    @ApiMessage("Address saved successfully")
    @PostMapping("/me/addresses")
    public ResponseEntity<AddressResponse> createAddress(
            HttpServletRequest request, @Valid @RequestBody AddressRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressBookService.create(customerId(request), body));
    }

    @GetMapping("/me/addresses/{id}")
    public AddressResponse getAddress(HttpServletRequest request, @PathVariable String id) {
        return addressBookService.get(customerId(request), id);
    }

    @ApiMessage("Address updated successfully")
    @PutMapping("/me/addresses/{id}")
    public AddressResponse updateAddress(
            HttpServletRequest request, @PathVariable String id, @Valid @RequestBody AddressRequest body) {
        return addressBookService.update(customerId(request), id, body);
    }

    @ApiMessage("Address deleted successfully")
    @DeleteMapping("/me/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(HttpServletRequest request, @PathVariable String id) {
        addressBookService.delete(customerId(request), id);
        return ResponseEntity.noContent().build();
    }

    @ApiMessage("Default address updated")
    @PutMapping("/me/addresses/{id}/default")
    public AddressResponse setDefault(HttpServletRequest request, @PathVariable String id) {
        return addressBookService.setDefault(customerId(request), id);
    }

    private static String customerId(HttpServletRequest request) {
        Object attr = request.getAttribute(CustomerJwtAuthFilter.CUSTOMER_ID_ATTR);
        if (attr == null) {
            throw BusinessException.unauthorized("Please sign in to continue.");
        }
        return attr.toString();
    }
}
