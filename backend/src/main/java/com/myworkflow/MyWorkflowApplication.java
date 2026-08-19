package com.myworkflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.myworkflow.**.mapper")
public class MyWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyWorkflowApplication.class, args);
    }
}
