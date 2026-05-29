package in.rithik.resumebuilderapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_ai_stats")
public class UserAiStats {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private int dailyRewriteCount;
}
