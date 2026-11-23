package com.red_escuela.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.permission.PermissionSnapshot;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InvitationWavePermissionsIT extends AbstractIntegrationTest {
    private static final List<String> CODES = List.of("notification.read_invitation_waves",
            "notification.manage_invitation_waves", "notification.send_invitation_wave");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PermissionSnapshotCache permissionSnapshotCache;

    @Test
    void invitationWavePermissionsExistInTheNotificationModule() {
        List<String> codes = jdbc.queryForList(
                "select code from permission where module = 'notification' and code like '%invitation_wave%'", String.class);

        assertThat(codes).containsExactlyInAnyOrderElementsOf(CODES);
    }

    @Test
    void onlySendingAnInvitationWaveIsSensitive() {
        List<String> sensitive = jdbc.queryForList(
                "select code from permission where code like '%invitation_wave%' and sensitive", String.class);

        assertThat(sensitive).containsExactly("notification.send_invitation_wave");
    }

    @Test
    void administrativeRoleHoldsAllInvitationWavePermissions() {
        assertThat(rolesHolding()).containsEntry("ADMINISTRATIVO", 3L);
    }

    @Test
    void directorIsGrantedAllInvitationWavePermissions() {
        permissionSnapshotCache.invalidate();
        PermissionSnapshot snapshot = permissionSnapshotCache.current();

        CODES.forEach(code -> assertThat(snapshot.grants("DIRECTOR", code)).as(code).isTrue());
    }

    @Test
    void noOtherRoleReceivesInvitationWavePermissionsThroughTheMigration() {
        assertThat(rolesHolding()).containsOnlyKeys("ADMINISTRATIVO");
    }

    @Test
    void invitationWavePermissionsAreNotFixedToDirector() {
        Integer fixed = jdbc.queryForObject(
                "select count(*) from permission where code like '%invitation_wave%' and fixed_to_director", Integer.class);

        assertThat(fixed).isZero();
    }

    private Map<String, Long> rolesHolding() {
        Map<String, Long> roles = new HashMap<>();
        jdbc.query("""
                select rp.role, count(*) as total from role_permission rp
                join permission p on p.id = rp.permission_id
                where p.code like '%invitation_wave%' group by rp.role
                """, rs -> {
            roles.put(rs.getString("role"), rs.getLong("total"));
        });
        return roles;
    }
}
