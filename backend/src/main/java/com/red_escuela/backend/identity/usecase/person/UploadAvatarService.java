package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@UseCase
public class UploadAvatarService implements UploadAvatarUseCase {

    private final FileStorageService fileStorageService;
    private final CurrentPersonService currentPersonService;
    private final PersonRepository personRepository;

    public UploadAvatarService(FileStorageService fileStorageService,
                               CurrentPersonService currentPersonService,
                               PersonRepository personRepository) {
        this.fileStorageService = fileStorageService;
        this.currentPersonService = currentPersonService;
        this.personRepository = personRepository;
    }

    @Override
    @Transactional
    public void upload(MultipartFile file) {
        Person person = currentPersonService.resolveCurrentPerson();
        if (person.getAvatarPath() != null) {
            fileStorageService.delete(person.getAvatarPath());
        }
        String path = fileStorageService.storeAvatar(person.getId(), file);
        person.setAvatarPath(path);
        personRepository.save(person);
    }
}
