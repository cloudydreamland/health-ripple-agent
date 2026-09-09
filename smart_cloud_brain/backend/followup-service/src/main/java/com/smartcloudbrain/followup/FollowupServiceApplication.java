package com.smartcloudbrain.followup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.smartcloudbrain")
public class FollowupServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FollowupServiceApplication.class, args);
  }
}
