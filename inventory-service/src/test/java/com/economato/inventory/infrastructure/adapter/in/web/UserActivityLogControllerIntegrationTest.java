package com.economato.inventory.infrastructure.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.domain.model.UserActivityLog;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserActivityLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

class UserActivityLogControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserActivityLogRepository userActivityLogRepository;

    private User admin;
    private User chef;
    private User student;

    @BeforeEach
    void setUp() {
        clearDatabase();

        admin = TestDataUtil.createUser("Admin", "adminUser", "admin123", Role.ADMIN);
        chef = TestDataUtil.createUser("Chef", "chefUser", "chef123", Role.CHEF);
        student = TestDataUtil.createUser("Student", "studentUser", "student123", Role.USER);
        student.setTeacher(chef);

        userRepository.save(admin);
        userRepository.save(chef);
        userRepository.save(student);

        UserActivityLog log1 = new UserActivityLog();
        log1.setUser(student);
        log1.setAction("CONNECTED");
        log1.setScreen("DASHBOARD");
        log1.setSessionId("s1");
        log1.setTimestamp(LocalDateTime.now().minusMinutes(1));

        UserActivityLog log2 = new UserActivityLog();
        log2.setUser(student);
        log2.setAction("SCREEN_CHANGED");
        log2.setScreen("ORDER_RECEPTION");
        log2.setSessionId("s1");
        log2.setTimestamp(LocalDateTime.now());

        userActivityLogRepository.save(log1);
        userActivityLogRepository.save(log2);
        userActivityLogRepository.flush();
    }

    @Test
    void getAll_asAdmin_shouldReturnPaginatedResults() throws Exception {
        String token = login("Admin", "admin123");

        mockMvc.perform(get("/api/user-activity?page=0&size=20&sort=timestamp,desc")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void getByUserId_asAdmin_shouldReturnResults() throws Exception {
        String token = login("Admin", "admin123");

        mockMvc.perform(get("/api/user-activity/user/{userId}", student.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void getMyStudentsActivity_asChef_shouldReturnResults() throws Exception {
        String token = login("Chef", "chef123");

        mockMvc.perform(get("/api/user-activity/my-students?page=0&size=20&sort=timestamp,desc")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }
}
