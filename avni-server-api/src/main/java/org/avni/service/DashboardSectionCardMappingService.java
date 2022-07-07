package org.avni.service;

import org.avni.dao.DashboardSectionCardMappingRepository;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Service
public class DashboardSectionCardMappingService implements NonScopeAwareService {

    private final DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository;

    public DashboardSectionCardMappingService(DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository) {
        this.dashboardSectionCardMappingRepository = dashboardSectionCardMappingRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return dashboardSectionCardMappingRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}
