package com.red_escuela.backend.identity.usecase.person;

import org.springframework.web.multipart.MultipartFile;

public interface UploadAvatarUseCase {
    void upload(MultipartFile file);
}
