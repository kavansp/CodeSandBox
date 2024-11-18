package chan.project.codesandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CodeSandBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeSandBoxApplication.class, args);
    }

}
