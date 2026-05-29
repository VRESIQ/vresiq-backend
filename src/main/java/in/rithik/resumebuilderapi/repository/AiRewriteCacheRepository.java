package in.rithik.resumebuilderapi.repository;

import in.rithik.resumebuilderapi.document.AiRewriteCache;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface AiRewriteCacheRepository extends MongoRepository<AiRewriteCache, String> {
    Optional<AiRewriteCache> findByContentHash(String contentHash);
}
