package com.example.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationListTest extends AbstractIntegrationTest {

    private String admin, user1, user2;

    /**
     * Test data:
     *   user1: 50.00 PENDING, 150.00 CONFIRMED, 300.00 PENDING   (resource A)
     *   user2: 500.00 PENDING                                    (resource B)
     */
    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        user1 = user1Token();
        user2 = user2Token();

        Long resA = resourceId(0);
        Long resB = resourceId(1);

        createReservation(user1, resA, 8, "50.00");
        long confirmedId = createReservation(user1, resA, 10, "150.00");
        createReservation(user1, resA, 12, "300.00");
        createReservation(user2, resB, 8, "500.00");

        mockMvc.perform(put("/reservations/" + confirmedId).header(AUTH, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(resA, slot(10), slot(11), "150.00", "CONFIRMED")))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions list(String token, String query) throws Exception {
        return mockMvc.perform(get("/reservations" + query).header(AUTH, bearer(token)));
    }

    // ---------- ownership scoping ----------

    @Test
    void adminSeesAllReservations() throws Exception {
        list(admin, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void userSeesOnlyOwnReservations() throws Exception {
        list(user1, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.username != 'user1')]").isEmpty());

        list(user2, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("user2"));
    }

    @Test
    void filtersCannotLeakOtherUsersData() throws Exception {
        // user2's 500.00 reservation must never appear for user1, even when filtering for it
        list(user1, "?minPrice=400").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anonymousCannotListReservations() throws Exception {
        mockMvc.perform(get("/reservations")).andExpect(status().isUnauthorized());
    }

    // ---------- filtering ----------

    @Test
    void filterByStatus() throws Exception {
        list(admin, "?status=CONFIRMED").andExpect(jsonPath("$.totalElements").value(1));
        list(admin, "?status=PENDING").andExpect(jsonPath("$.totalElements").value(3));
        list(admin, "?status=CANCELLED").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filterByMinPrice() throws Exception {
        list(admin, "?minPrice=150").andExpect(jsonPath("$.totalElements").value(3)); // inclusive
    }

    @Test
    void filterByMaxPrice() throws Exception {
        list(admin, "?maxPrice=150").andExpect(jsonPath("$.totalElements").value(2)); // inclusive
    }

    @Test
    void filterByPriceRange() throws Exception {
        list(admin, "?minPrice=100&maxPrice=400").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void combinedFilters() throws Exception {
        list(admin, "?status=PENDING&minPrice=100&maxPrice=400")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(300.0));
    }

    // ---------- pagination ----------

    @Test
    void paginationSplitsResults() throws Exception {
        list(admin, "?page=0&size=3").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        list(admin, "?page=1&size=3").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void pageBeyondLastReturnsEmptyContent() throws Exception {
        list(admin, "?page=99").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ---------- sorting ----------

    @Test
    void sortByPriceDescending() throws Exception {
        list(user1, "?sortBy=price&direction=desc")
                .andExpect(jsonPath("$.content[0].price").value(300.0));
    }

    @Test
    void sortByPriceAscending() throws Exception {
        list(user1, "?sortBy=price&direction=asc")
                .andExpect(jsonPath("$.content[0].price").value(50.0));
    }

    @Test
    void directionIsCaseInsensitive() throws Exception {
        list(user1, "?sortBy=price&direction=DESC")
                .andExpect(jsonPath("$.content[0].price").value(300.0));
    }

    // ---------- invalid parameters ----------

    @Test
    void invalidParametersReturn400() throws Exception {
        list(admin, "?status=DONE").andExpect(status().isBadRequest());
        list(admin, "?size=0").andExpect(status().isBadRequest());
        list(admin, "?size=101").andExpect(status().isBadRequest());
        list(admin, "?page=-1").andExpect(status().isBadRequest());
        list(admin, "?minPrice=-1").andExpect(status().isBadRequest());
        list(admin, "?minPrice=500&maxPrice=100").andExpect(status().isBadRequest());
        list(admin, "?sortBy=user.password").andExpect(status().isBadRequest());
        list(admin, "?direction=up").andExpect(status().isBadRequest());
        list(admin, "?minPrice=abc").andExpect(status().isBadRequest());
    }
}