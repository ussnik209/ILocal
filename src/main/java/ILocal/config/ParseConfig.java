package ILocal.config;


import ILocal.service.ParseFile;
import org.springframework.context.annotation.*;

@Configuration
public class ParseConfig {
    @Bean
    public ParseFile parsePropertiesFile() {
        return new ParseFile();
    }
}
