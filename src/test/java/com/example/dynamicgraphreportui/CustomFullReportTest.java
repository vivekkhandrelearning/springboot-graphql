package com.example.dynamicgraphreportui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomFullReportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testCustomFullReportQuery() throws Exception {
        String query = """
            query {
                customFullReport(
                    type: "Antenna",
                    filters: [
                        { field: "manufacturer_type", op: EQ, values: ["SomeType"] }
                    ],
                    pagination: { page: 0, pageSize: 10 }
                ) {
                    rows
                    pageInfo {
                        page
                        pageSize
                    }
                }
            }
            """;

        mockMvc.perform(post("/api/v1/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\": \"" + query.replace("\n", "").replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customFullReport").exists());
    }
}
