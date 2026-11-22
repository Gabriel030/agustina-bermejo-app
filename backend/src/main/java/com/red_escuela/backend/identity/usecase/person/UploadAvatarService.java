package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.service.AvatarUploadTransaction;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.web.multipart.MultipartFile;

@UseCase
public class UploadAvatarService implements UploadAvatarUseCase {

    private final AvatarUploadTransaction avatarUploadTransaction;

    public UploadAvatarService(AvatarUploadTransaction avatarUploadTransaction) {
        this.avatarUploadTransaction = avatarUploadTransaction;
    }

    @Override
    public void upload(MultipartFile file) {
        avatarUploadTransaction.upload(file);
    }
}
