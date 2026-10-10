package com.template.account_login_logout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"management.health.redis.enabled=false",
		"management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void adminEndpointsRequireAdminRole() throws Exception {
		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/users")
					.with(SecurityMockMvcRequestPostProcessors.user("sam").roles("USER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void healthAndPrometheusEndpointsAreExposed() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk());
	}

}
