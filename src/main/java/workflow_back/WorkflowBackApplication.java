package workflow_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {"com.workflow"})
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {"com.workflow.*.repository"})
public class WorkflowBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkflowBackApplication.class, args);
	}

}
