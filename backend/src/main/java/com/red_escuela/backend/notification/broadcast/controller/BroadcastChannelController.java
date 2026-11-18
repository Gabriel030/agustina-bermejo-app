package com.red_escuela.backend.notification.broadcast.controller;

import com.red_escuela.backend.notification.broadcast.dto.BroadcastChannelEntry;
import com.red_escuela.backend.notification.broadcast.usecase.channel.ListBroadcastChannelsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/broadcasts")
@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRATIVO')")
public class BroadcastChannelController {

    private final ListBroadcastChannelsUseCase listBroadcastChannels;

    public BroadcastChannelController(ListBroadcastChannelsUseCase listBroadcastChannels) {
        this.listBroadcastChannels = listBroadcastChannels;
    }

    @GetMapping("/channels")
    public List<BroadcastChannelEntry> listBroadcastChannels() {
        return listBroadcastChannels.listBroadcastChannels();
    }
}
