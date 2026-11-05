package com.red_escuela.backend.announcement.dto;

public record AnnouncementReadCount(long recipientCount, long readCount) {

    public static final AnnouncementReadCount EMPTY = new AnnouncementReadCount(0, 0);
}
