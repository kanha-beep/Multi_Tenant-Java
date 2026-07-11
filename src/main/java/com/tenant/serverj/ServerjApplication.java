// Ye file Java backend ka main entry point hai.
package com.tenant.serverj;

// SpringApplication Spring Boot app ko start karne ke kaam aata hai.
import org.springframework.boot.SpringApplication;
// @SpringBootApplication ek shortcut annotation hai jo configuration,
// component scanning, aur auto-configuration sab enable karta hai.
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot ko bataya ja raha hai ki ye main application class hai.
@SpringBootApplication
public class ServerjApplication {
    // main() Java program ka starting method hota hai.
    public static void main(String[] args) {
        // Ye line poora backend server start kar deti hai.
        SpringApplication.run(ServerjApplication.class, args);
    }
}
