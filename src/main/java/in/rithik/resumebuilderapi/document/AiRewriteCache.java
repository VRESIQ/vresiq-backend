package in.rithik.resumebuilderapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_rewrite_cache")
public class AiRewriteCache {

    @Id
    private String id;

    @Indexed(unique = true)
    private String contentHash; // SHA-256 of original content

    private String originalContent;
    private String rewrittenContent;

    private LocalDateTime createdAt;
}
