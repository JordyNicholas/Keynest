package dev.keynest.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.SQLNonTransientConnectionException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthEndpointIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoSpyBean
  DataSource dataSource;

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
  void healthyResponsesContainOnlyStatus(String path) throws Exception {
    mockMvc.perform(get(path))
      .andExpect(status().isOk())
      .andExpect(content().json("{\"status\":\"UP\"}", org.springframework.test.json.JsonCompareMode.STRICT));
  }

  @Test
  void aggregateHealthExposesOnlyStatusAndPublicGroupNames() throws Exception {
    mockMvc.perform(get("/actuator/health"))
      .andExpect(status().isOk())
      .andExpect(content().json(
        "{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}",
        org.springframework.test.json.JsonCompareMode.STRICT
      ));
  }

  @Test
  void databaseOutageRemovesReadinessWithoutFailingLivenessAndRecovers() throws Exception {
    try {
      doThrow(new SQLNonTransientConnectionException("simulated database outage"))
        .when(dataSource).getConnection();

      mockMvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().json("{\"status\":\"DOWN\"}", org.springframework.test.json.JsonCompareMode.STRICT));
      mockMvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", org.springframework.test.json.JsonCompareMode.STRICT));
    } finally {
      reset(dataSource);
    }

    mockMvc.perform(get("/actuator/health/readiness"))
      .andExpect(status().isOk())
      .andExpect(content().json("{\"status\":\"UP\"}", org.springframework.test.json.JsonCompareMode.STRICT));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "/actuator", "/actuator/env", "/actuator/configprops", "/actuator/heapdump",
    "/actuator/loggers", "/actuator/beans", "/actuator/mappings", "/actuator/metrics",
    "/actuator/flyway", "/actuator/threaddump"
  })
  void diagnosticEndpointsAreUnavailable(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isNotFound());
  }
}
