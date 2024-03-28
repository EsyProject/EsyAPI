package apiBoschEsy.apiInSpringBoot.Service;

import apiBoschEsy.apiInSpringBoot.repository.IFileDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    @Autowired
    private IFileDataRepository repository;
}
