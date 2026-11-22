package com.red_escuela.backend.notification.broadcast.controller;

import com.red_escuela.backend.notification.broadcast.dto.BroadcastChannelEntry;
import com.red_escuela.backend.notification.broadcast.usecase.channel.ListBroadcastChannelsUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/broadcasts")
public class BroadcastChannelController {

    private final ListBroadcastChannelsUseCase listBroadcastChannels;

    public BroadcastChannelController(ListBroadcastChannelsUseCase listBroadcastChannels) {
        this.listBroadcastChannels = listBroadcastChannels;
    }

    @RequiresPermission(code = "notification.compose_broadcast", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping("/channels")
    public List<BroadcastChannelEntry> listBroadcastChannels() {
        return listBroadcastChannels.listBroadcastChannels();
    }
}
