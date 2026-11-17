package com.red_escuela.backend.notification.broadcast.usecase.unsubscribe;

import com.red_escuela.backend.notification.broadcast.dto.UnsubscribeLinkResponse;

public interface DescribeUnsubscribeLinkUseCase {
    UnsubscribeLinkResponse describeUnsubscribeLink(String token);
}
