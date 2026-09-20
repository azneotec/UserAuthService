package com.azneotech.userauthservice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-context wiring test. Requires a live MySQL at the configured datasource (Flyway migrations
 * run and Hibernate validates the schema), so it is excluded from the default {@code ./mvnw test}.
 * Run it with: {@code ./mvnw test -DexcludedGroups=none -Dgroups=integration}
 */
@Tag("integration")
@SpringBootTest
class UserAuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
