package com.economato.inventory.shared;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;

@SpringBootTest
@ActiveProfiles("test")
class InventoryApplicationTests {

	@MockitoBean
	private AuditEventProducer auditEventProducer;

	@Test
	void contextLoads() {

	}

}
