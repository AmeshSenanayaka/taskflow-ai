package com.taskflow.service;

import com.taskflow.dto.request.UpdatePreferencesRequest;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.entity.UserPreferences;
import com.taskflow.mapper.PreferencesMapper;
import com.taskflow.repository.PreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pins down what "today" means for the whole application. Due dates are calendar days, so the only thing
 * the configured timezone may influence is which day the user is currently on.
 */
@ExtendWith(MockitoExtension.class)
class PreferencesServiceTest {

    /** 2026-11-02 22:30 UTC: still the 2nd in the Americas, already the 3rd east of UTC+1:30. */
    private static final Instant LATE_EVENING_UTC = Instant.parse("2026-11-02T22:30:00Z");

    @Mock
    private PreferencesRepository preferencesRepository;

    @Test
    void todayIsResolvedInTheConfiguredTimezoneNotTheServers() {
        assertThat(service("Asia/Colombo", Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(service("America/New_York", Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void serverTimezoneDoesNotLeakIntoToday() {
        // Same instant, but the JVM/server clock runs in a zone that is on a different calendar day
        // than the user. The user's zone must win regardless of where the server is deployed.
        Clock serverInLosAngeles = Clock.fixed(LATE_EVENING_UTC, ZoneId.of("America/Los_Angeles"));
        Clock serverInTokyo = Clock.fixed(LATE_EVENING_UTC, ZoneId.of("Asia/Tokyo"));

        assertThat(service("Asia/Colombo", serverInLosAngeles).today()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(service("Asia/Colombo", serverInTokyo).today()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(service("America/New_York", serverInTokyo).today()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void todayFlipsExactlyAtTheUsersLocalMidnight() {
        // Colombo is UTC+5:30, so local midnight on the 3rd is 18:30Z on the 2nd.
        Instant justBeforeMidnight = Instant.parse("2026-11-02T18:29:59Z");
        Instant midnight = Instant.parse("2026-11-02T18:30:00Z");

        assertThat(service("Asia/Colombo", Clock.fixed(justBeforeMidnight, ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(service("Asia/Colombo", Clock.fixed(midnight, ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void todayOnADaylightSavingTransitionDayIsStillTheCalendarDay() {
        // New York leaves DST on 2026-11-01 at 02:00 local (06:00Z). 05:30Z is 01:30 EDT, 06:30Z is 01:30 EST:
        // the wall clock repeats, but the calendar day never wavers, which is all a LocalDate due date needs.
        assertThat(service("America/New_York", Clock.fixed(Instant.parse("2026-11-01T05:30:00Z"), ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(service("America/New_York", Clock.fixed(Instant.parse("2026-11-01T06:30:00Z"), ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void updateStampsUpdatedAtFromTheClock() {
        PreferencesService service = service("Asia/Colombo", Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC));
        when(preferencesRepository.saveAndFlush(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        PreferencesResponse response = service.update(new UpdatePreferencesRequest("Europe/Berlin", 360));

        assertThat(response.timezone()).isEqualTo("Europe/Berlin");
        assertThat(response.updatedAt()).isEqualTo(LATE_EVENING_UTC);
        assertThat(service.getZoneId()).isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    private PreferencesService service(String timezone, Clock clock) {
        UserPreferences preferences = new UserPreferences(timezone, 480, Instant.parse("2026-10-01T00:00:00Z"));
        when(preferencesRepository.findById(UserPreferences.SINGLETON_ID)).thenReturn(Optional.of(preferences));
        return new PreferencesService(preferencesRepository, new PreferencesMapper(), clock);
    }
}
