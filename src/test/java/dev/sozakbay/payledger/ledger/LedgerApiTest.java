package dev.sozakbay.payledger.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.sozakbay.payledger.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class LedgerApiTest {

    @Autowired MockMvc mvc;

    @Test
    void transferMovesMoneyAndShowsInStatement() throws Exception {
        var alice = openAccount();
        var bob = openAccount();
        postForId("/accounts/" + alice + "/deposits", "{\"amount\":10000}", 201);

        var transferId = postForId("/transfers", transferBody(alice, bob, 2500), 201);

        mvc.perform(get("/transfers/" + transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccountId").value(alice))
                .andExpect(jsonPath("$.amount").value(2500));
        mvc.perform(get("/accounts/" + alice)).andExpect(jsonPath("$.balance").value(7500));
        mvc.perform(get("/accounts/" + bob + "/entries"))
                .andExpect(jsonPath("$[0].transferId").value(transferId))
                .andExpect(jsonPath("$[0].amount").value(2500));
    }

    @Test
    void insufficientFundsIsProblemDetail() throws Exception {
        var alice = openAccount();
        var bob = openAccount();

        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(transferBody(alice, bob, 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Insufficient funds"));
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        mvc.perform(get("/accounts/00000000-0000-0000-0000-000000000001")).andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestIsBadRequest() throws Exception {
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"kzt\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void healthAndOpenApiAreExposed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(jsonPath("$.paths['/transfers']").exists());
    }

    private String openAccount() throws Exception {
        return postForId("/accounts", "{\"currency\":\"KZT\"}", 201);
    }

    /** Posts JSON, checks the status and returns the "id" of the response. */
    private String postForId(String url, String body, int expectedStatus) throws Exception {
        var response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String transferBody(String from, String to, long amount) {
        return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%d}".formatted(from, to, amount);
    }
}
