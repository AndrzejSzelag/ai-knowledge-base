package pl.szelag.ai_knowledge_base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableCaching
public class AiKnowledgeBaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiKnowledgeBaseApplication.class, args);
	}

}