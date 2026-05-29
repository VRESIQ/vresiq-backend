package in.rithik.resumebuilderapi.repository;

import in.rithik.resumebuilderapi.document.UserAiStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UserAiStatsRepository extends MongoRepository<UserAiStats, String> {
    Optional<UserAiStats> findByUserId(String userId);
    void deleteByUserId(String userId);
}
