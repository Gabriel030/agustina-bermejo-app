package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.service.TransactionBoundFileCleanup;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@UseCase
public class UploadAvatarService implements UploadAvatarUseCase {

    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;
    private final CurrentPersonService currentPersonService;
    private final PersonRepository personRepository;

    public UploadAvatarService(FileStorageService fileStorageService, TransactionBoundFileCleanup transactionBoundFileCleanup,
                               CurrentPersonService currentPersonService,
                               PersonRepository personRepository) {
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
        this.currentPersonService = currentPersonService;
        this.personRepository = personRepository;
    }

    @Override
    @Transactional
    public void upload(MultipartFile file) {
        Person person = currentPersonService.resolveCurrentPerson();
        String previousPath = person.getAvatarPath();
        String path = fileStorageService.storeAvatar(person.getId(), file);
        transactionBoundFileCleanup.deleteIfRolledBack(path);
        if (previousPath != null && !previousPath.equals(path)) {
            transactionBoundFileCleanup.deleteAfterCommit(previousPath);
        }
        person.setAvatarPath(path);
        personRepository.save(person);
    }
}
