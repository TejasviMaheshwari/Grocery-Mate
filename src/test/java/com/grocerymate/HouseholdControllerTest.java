
package com.grocerymate;

import com.grocerymate.controller.HouseholdController;
import com.grocerymate.security.AuthenticatedAccount;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private AuthenticatedAccount account;

    @InjectMocks
    private HouseholdController householdController;


    // Test 1: Unauthenticated users cannot access household inventory.
    @Test
    void getItemsWithoutAuthenticationReturnsUnauthorized() {

        ResponseEntity<?> response =
                householdController.getItems(null, null);

        assertEquals(401, response.getStatusCode().value());
    }


    // Test 2: An invalid authentication principal is rejected.
    @Test
    void getItemsWithInvalidPrincipalReturnsUnauthorized() {

        when(authentication.getPrincipal())
                .thenReturn("invalid-principal");

        ResponseEntity<?> response =
                householdController.getItems(null, authentication);

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                response.getStatusCode()
        );
    }


    // Test 3: Vendors cannot access the user household inventory endpoint.
    @Test
    void getItemsWithVendorRoleReturnsForbidden() {

        when(authentication.getPrincipal()).thenReturn(account);
        when(account.role()).thenReturn("VENDOR");

        ResponseEntity<?> response =
                householdController.getItems(null, authentication);

        assertEquals(
                HttpStatus.FORBIDDEN,
                response.getStatusCode()
        );
    }


    // Test 4: Stock check-ins require the mandatory request fields.
    @Test
    void addStockCheckinWithoutRequiredFieldsReturnsBadRequest() {

        when(authentication.getPrincipal()).thenReturn(account);
        when(account.role()).thenReturn("USER");
        when(account.householdId()).thenReturn(1L);

        ResponseEntity<?> response =
                householdController.addStockCheckin(
                        10L,
                        Map.of(),
                        authentication
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );
    }


    // Test 5: Stock consumption must use a positive quantity.
    @Test
    void updateStockWithNegativeQuantityReturnsBadRequest() {

        when(authentication.getPrincipal()).thenReturn(account);
        when(account.role()).thenReturn("USER");
        when(account.householdId()).thenReturn(1L);

        Map<String, Object> request = Map.of(
                "action", "CONSUME",
                "quantity", "-2"
        );

        ResponseEntity<?> response =
                householdController.updateStock(
                        10L,
                        request,
                        authentication
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );
    }
}
