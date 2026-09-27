package com.example.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationSecurityTest extends AbstractIntegrationTest {

    private String admin, user1, user2;
    private Long resA;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        user1 = user1Token();
        user2 = user2Token();
        resA = resourceId(0);
    }

    // ---------- owner comes from the JWT ----------

    @Test
    void ownerAndStatusAreNotTakenFromRequestBody() throws Exception {
        String base = reservationJson(resA, slot(9), slot(10), "100.00");
        // try to spoof the owner and the status
        String spoofed = base.substring(0, base.length() - 1)
                + ",\"username\":\"admin\",\"status\":\"CONFIRMED\"}";

        mockMvc.perform(post("/reservations").header(AUTH, bearer(user1))
                        .contentType(MediaType.APPLICATION_JSON).content(spoofed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void anonymousCannotCreateReservation() throws Exception {
        mockMvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(resA, slot(9), slot(10), "100.00")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- ownership on read ----------

    @Test
    void ownerCanReadOwnReservation() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(get("/reservations/" + id).header(AUTH, bearer(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user1"));
    }

    @Test
    void otherUserCannotReadReservation() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(get("/reservations/" + id).header(AUTH, bearer(user2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyReservation() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(get("/reservations/" + id).header(AUTH, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void missingReservationReturns404() throws Exception {
        mockMvc.perform(get("/reservations/999999").header(AUTH, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ---------- RBAC on update and delete ----------

    @Test
    void userCannotUpdateEvenOwnReservation() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(put("/reservations/" + id).header(AUTH, bearer(user1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(resA, slot(9), slot(10), "100.00", "CONFIRMED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotDeleteEvenOwnReservation() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(delete("/reservations/" + id).header(AUTH, bearer(user1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateReservationStatus() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(put("/reservations/" + id).header(AUTH, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(resA, slot(9), slot(10), "120.00", "CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price").value(120.0));
    }

    @Test
    void adminCanDeleteReservationThenItIsGone() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(delete("/reservations/" + id).header(AUTH, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/reservations/" + id).header(AUTH, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ---------- validation ----------

    private void assertCreateRejected(String json, int expectedStatus) throws Exception {
        mockMvc.perform(post("/reservations").header(AUTH, bearer(user1))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        assertCreateRejected("{}", 400);
    }

    @Test
    void endBeforeStartReturns400() throws Exception {
        assertCreateRejected(reservationJson(resA, slot(11), slot(10), "100.00"), 400);
    }

    @Test
    void endEqualToStartReturns400() throws Exception {
        assertCreateRejected(reservationJson(resA, slot(10), slot(10), "100.00"), 400);
    }

    @Test
    void startInThePastReturns400() throws Exception {
        assertCreateRejected(reservationJson(resA,
                slot(9).minusDays(30), slot(10).minusDays(30), "100.00"), 400);
    }

    @Test
    void negativeOrZeroPriceReturns400() throws Exception {
        assertCreateRejected(reservationJson(resA, slot(9), slot(10), "-5"), 400);
        assertCreateRejected(reservationJson(resA, slot(9), slot(10), "0"), 400);
    }

    @Test
    void priceWithMoreThanTwoDecimalsReturns400() throws Exception {
        assertCreateRejected(reservationJson(resA, slot(9), slot(10), "10.999"), 400);
    }

    @Test
    void malformedDateReturns400() throws Exception {
        assertCreateRejected("{\"resourceId\":" + resA
                + ",\"startTime\":\"10-05-2026\",\"endTime\":\"10-05-2026\",\"price\":10}", 400);
    }

    @Test
    void unknownResourceReturns404() throws Exception {
        assertCreateRejected(reservationJson(999_999L, slot(9), slot(10), "100.00"), 404);
    }

    @Test
    void invalidStatusOnUpdateReturns400() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(put("/reservations/" + id).header(AUTH, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(resA, slot(9), slot(10), "100.00", "DONE")))
                .andExpect(status().isBadRequest());
    }

    // ---------- double booking ----------

    @Test
    void overlappingReservationReturns409() throws Exception {
        createReservation(user1, resA, 9, "100.00");
        assertCreateRejected(reservationJson(resA, slot(9), slot(10), "100.00"), 409);
    }

    @Test
    void cancelledReservationFreesTheSlot() throws Exception {
        long id = createReservation(user1, resA, 9, "100.00");
        mockMvc.perform(put("/reservations/" + id).header(AUTH, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(resA, slot(9), slot(10), "100.00", "CANCELLED")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/reservations").header(AUTH, bearer(user2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(resA, slot(9), slot(10), "100.00")))
                .andExpect(status().isCreated());
    }
}