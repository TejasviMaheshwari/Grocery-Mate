package com.grocerymate.security;

import java.io.Serializable;

public record AuthenticatedAccount(
        long id,
        String role,
        Long householdId
) implements Serializable {
}

