package in.rithik.resumebuilderapi.repository;

import in.rithik.resumebuilderapi.document.Resume;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends MongoRepository<Resume,String> {
    List<Resume> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<Resume>findByUserIdAndId(String userId, String id);
    java.util.List<Resume> findAllByOrderByUpdatedAtDesc();
    void deleteByUserId(String userId);


}
