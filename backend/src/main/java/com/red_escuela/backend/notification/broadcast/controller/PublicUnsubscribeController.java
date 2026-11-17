package com.red_escuela.backend.notification.broadcast.controller;

import com.red_escuela.backend.notification.broadcast.dto.UnsubscribeLinkResponse;
import com.red_escuela.backend.notification.broadcast.usecase.unsubscribe.ConfirmUnsubscribeUseCase;
import com.red_escuela.backend.notification.broadcast.usecase.unsubscribe.DescribeUnsubscribeLinkUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/unsubscribe/{token}")
public class PublicUnsubscribeController {

    private final DescribeUnsubscribeLinkUseCase describeUnsubscribeLinkUseCase;
    private final ConfirmUnsubscribeUseCase confirmUnsubscribeUseCase;

    public PublicUnsubscribeController(DescribeUnsubscribeLinkUseCase describeUnsubscribeLinkUseCase,
                                       ConfirmUnsubscribeUseCase confirmUnsubscribeUseCase) {
        this.describeUnsubscribeLinkUseCase = describeUnsubscribeLinkUseCase;
        this.confirmUnsubscribeUseCase = confirmUnsubscribeUseCase;
    }

    @GetMapping
    public UnsubscribeLinkResponse describeUnsubscribeLink(@PathVariable String token) {
        return describeUnsubscribeLinkUseCase.describeUnsubscribeLink(token);
    }

    @PostMapping
    public void confirmUnsubscribe(@PathVariable String token) {
        confirmUnsubscribeUseCase.confirmUnsubscribe(token);
    }
}
