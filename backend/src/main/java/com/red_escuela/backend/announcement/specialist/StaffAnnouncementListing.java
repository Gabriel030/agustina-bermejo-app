package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.entity.Announcement;

import java.util.List;
import java.util.Map;

public record StaffAnnouncementListing(List<Announcement> announcements,
                                       Map<Long, AnnouncementReadCounter.ReadCount> counts) {
}
