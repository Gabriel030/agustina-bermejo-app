package com.red_escuela.backend.enrollment.usecase.occupancy;

import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse;
import com.red_escuela.backend.enrollment.service.CycleOccupancyReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCycleOccupancyServiceTest {

    @Mock
    private CycleOccupancyReader cycleOccupancyReader;
    @InjectMocks
    private GetCycleOccupancyService service;

    @Test
    void returnsTheOccupancyReadForTheCycle() {
        var expected = new CycleOccupancyResponse(12L, List.of());
        when(cycleOccupancyReader.readOccupancy(12L)).thenReturn(expected);

        assertThat(service.getOccupancy(12L)).isSameAs(expected);
    }
}
