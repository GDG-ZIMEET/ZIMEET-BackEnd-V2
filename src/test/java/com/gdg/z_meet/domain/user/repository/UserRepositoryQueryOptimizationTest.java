package com.gdg.z_meet.domain.user.repository;

import com.gdg.z_meet.domain.fcm.unit.config.QueryDslTestConfig;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.entity.enums.ActiveStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({UserRepositoryImpl.class, QueryDslTestConfig.class})
@DisplayName("UserRepository QueryDSL 최적화 검증")
class UserRepositoryQueryOptimizationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserTeamRepository userTeamRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private User testUser;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        testUser = createUser("2024001", "testUser", "010-1111-1111", "테스트", Gender.MALE);

        for (int i = 0; i < 50; i++) {
            User user = createUser(
                    "2024" + String.format("%03d", i + 100),
                    "user" + i,
                    "010-2000-" + String.format("%04d", i),
                    "유저" + i,
                    Gender.FEMALE
            );

            // 10명은 활성 팀에 속하게 함
            if (i % 5 == 0) {
                Team team = createTeam(TeamType.TWO_TO_TWO, Event.NEUL_2025);
                createUserTeam(user, team);
            }
        }

        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    // 최적화 이전 JPQL 쿼리 (NOT EXISTS 서브쿼리 방식)
    private List<User> findAllByNicknameWithProfileJpql(Gender gender, String nickname, Long userId, TeamType teamType, Event event) {
        return entityManager.createQuery(
                "SELECT u FROM User u JOIN FETCH u.userProfile up WHERE up.gender = :gender " +
                        "AND up.nickname LIKE CONCAT(:nickname, '%') AND u.id != :userId AND u.isDeleted = false " +
                        "AND NOT EXISTS (SELECT 1 FROM Team t JOIN UserTeam ut ON ut.team = t " +
                        "WHERE ut.user = u AND t.teamType = :teamType AND t.activeStatus = 'ACTIVE' AND t.event = :event)",
                User.class)
                .setParameter("gender", gender)
                .setParameter("nickname", nickname)
                .setParameter("userId", userId)
                .setParameter("teamType", teamType)
                .setParameter("event", event)
                .getResultList();
    }

    private List<User> findAllByPhoneNumberWithProfileJpql(Gender gender, String phoneNumber, Long userId, TeamType teamType, Event event) {
        return entityManager.createQuery(
                "SELECT u FROM User u JOIN FETCH u.userProfile up WHERE up.gender = :gender " +
                        "AND u.phoneNumber LIKE CONCAT(:phoneNumber, '%') AND u.id != :userId AND u.isDeleted = false " +
                        "AND NOT EXISTS (SELECT 1 FROM Team t JOIN UserTeam ut ON ut.team = t " +
                        "WHERE ut.user = u AND t.teamType = :teamType AND t.activeStatus = 'ACTIVE' AND t.event = :event)",
                User.class)
                .setParameter("gender", gender)
                .setParameter("phoneNumber", phoneNumber)
                .setParameter("userId", userId)
                .setParameter("teamType", teamType)
                .setParameter("event", event)
                .getResultList();
    }

    @Test
    void JPQL_NOT_EXISTS_서브쿼리_방식_쿼리_실행_횟수_확인() {
        // given
        statistics.clear();
        
        // when: JPQL NOT EXISTS 방식
        List<User> result = findAllByNicknameWithProfileJpql(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then
        long jpqlQueryCount = statistics.getQueryExecutionCount();
        
        assertThat(jpqlQueryCount)
                .as("JPQL NOT EXISTS 방식의 쿼리 실행 횟수")
                .isGreaterThanOrEqualTo(1);
        
        assertThat(result).hasSize(40);
    }

    @Test
    void QueryDSL_LEFT_JOIN으로_NOT_EXISTS_서브쿼리_최적화() {
        // given: 기존 JPQL은 NOT EXISTS 서브쿼리 사용
        // 최적화: LEFT JOIN + IS NULL로 변경
        
        // when
        List<User> result = userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then: LEFT JOIN 방식은 단일 쿼리로 처리
        assertThat(statistics.getQueryExecutionCount())
                .as("LEFT JOIN 방식은 서브쿼리 없이 1번의 쿼리로 실행")
                .isEqualTo(1);
        
        assertThat(result).hasSize(40); // 50명 중 활성 팀 10명 제외
    }

    @Test
    void QueryDSL_JOIN_FETCH로_N플러스1_문제_해결() {
        // given
        statistics.clear();
        
        // when
        List<User> result = userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        long queriesBeforeAccess = statistics.getQueryExecutionCount();
        
        // UserProfile 접근 시 추가 쿼리가 발생하지 않아야 함
        result.forEach(user -> {
            String nickname = user.getUserProfile().getNickname();
            assertThat(nickname).isNotNull();
        });
        
        long queriesAfterAccess = statistics.getQueryExecutionCount();

        // then
        assertThat(queriesAfterAccess)
                .as("JOIN FETCH로 인해 UserProfile 접근 시 추가 쿼리 없음")
                .isEqualTo(queriesBeforeAccess);
    }

    @Test
    void QueryDSL_활성_팀_필터링_정확성_검증() {
        // when
        List<User> result = userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then: 활성 팀에 속하지 않은 사용자만 조회
        result.forEach(user -> {
            List<UserTeam> userTeams = userTeamRepository.findByUserId(user.getId());
            boolean hasActiveTeam = userTeams.stream()
                    .anyMatch(ut -> ut.getTeam().getActiveStatus() == ActiveStatus.ACTIVE
                            && ut.getTeam().getTeamType() == TeamType.TWO_TO_TWO
                            && ut.getTeam().getEvent() == Event.NEUL_2025);
            
            assertThat(hasActiveTeam)
                    .as("LEFT JOIN + IS NULL 방식으로 활성 팀 소속 사용자 제외")
                    .isFalse();
        });
    }

    @Test
    void QueryDSL_전화번호_검색도_동일한_최적화_적용() {
        // when
        List<User> result = userRepository.findAllByPhoneNumberWithProfile(
                Gender.FEMALE,
                "010-2000",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then
        assertThat(statistics.getQueryExecutionCount())
                .as("전화번호 검색도 LEFT JOIN 방식으로 단일 쿼리 실행")
                .isEqualTo(1);
        
        assertThat(result).hasSize(40);
    }

    @Test
    void QueryDSL_startsWith로_LIKE_패턴_최적화() {
        // given
        createUser("2024500", "abc123", "010-5000-0001", "ABC1", Gender.FEMALE);
        createUser("2024501", "abc456", "010-5000-0002", "ABC2", Gender.FEMALE);
        createUser("2024502", "xyz789", "010-5000-0003", "XYZ1", Gender.FEMALE);
        
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        
        // when
        List<User> result = userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "abc",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then: startsWith('abc') → LIKE 'abc%' 형태로 인덱스 활용 가능
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(u -> u.getUserProfile().getNickname())
                .allMatch(nickname -> nickname.startsWith("abc"));
    }

    @Test
    void QueryDSL_최적화_쿼리와_기존_로직_결과_동일성_검증() {
        // when: QueryDSL 최적화 쿼리 실행
        List<User> queryDslResult = userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // when: 동일한 조건으로 수동 필터링 (기대 결과)
        List<User> allFemaleUsers = userRepository.findAll().stream()
                .filter(u -> u.getUserProfile() != null)
                .filter(u -> u.getUserProfile().getGender() == Gender.FEMALE)
                .filter(u -> u.getUserProfile().getNickname().startsWith("user"))
                .filter(u -> !u.getId().equals(testUser.getId()))
                .filter(u -> !u.isDeleted())
                .filter(u -> {
                    List<UserTeam> teams = userTeamRepository.findByUserId(u.getId());
                    return teams.stream().noneMatch(ut ->
                            ut.getTeam().getTeamType() == TeamType.TWO_TO_TWO &&
                            ut.getTeam().getActiveStatus() == ActiveStatus.ACTIVE &&
                            ut.getTeam().getEvent() == Event.NEUL_2025
                    );
                })
                .toList();

        // then: 결과가 동일해야 함
        assertThat(queryDslResult)
                .as("QueryDSL 최적화 쿼리 결과가 기존 로직과 동일")
                .hasSize(allFemaleUsers.size())
                .extracting(User::getId)
                .containsExactlyInAnyOrderElementsOf(
                        allFemaleUsers.stream().map(User::getId).toList()
                );
    }

    @Test
    void JPQL과_QueryDSL_결과_동일성_검증() {
        // when: JPQL 방식
        List<User> jpqlResult = findAllByNicknameWithProfileJpql(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        entityManager.clear();

        // when: QueryDSL 방식
        List<User> queryDslResult = userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then: 두 방식의 결과가 동일해야 함
        assertThat(jpqlResult)
                .as("JPQL과 QueryDSL의 결과 개수가 동일")
                .hasSameSizeAs(queryDslResult);

        assertThat(jpqlResult)
                .as("JPQL과 QueryDSL의 결과 ID가 동일")
                .extracting(User::getId)
                .containsExactlyInAnyOrderElementsOf(
                        queryDslResult.stream().map(User::getId).toList()
                );
    }

    @Test
    void JPQL_대비_QueryDSL_쿼리_실행_횟수_비교() {
        // given
        entityManager.clear();
        statistics.clear();

        // when: JPQL 방식
        findAllByNicknameWithProfileJpql(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );
        long jpqlQueryCount = statistics.getQueryExecutionCount();

        entityManager.clear();
        statistics.clear();

        // when: QueryDSL 방식
        userRepository.findAllByNicknameWithProfile(
                Gender.FEMALE,
                "user",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );
        long queryDslQueryCount = statistics.getQueryExecutionCount();

        // then: 쿼리 실행 횟수 비교
        assertThat(queryDslQueryCount)
                .as("QueryDSL LEFT JOIN 방식이 JPQL NOT EXISTS보다 효율적")
                .isLessThanOrEqualTo(jpqlQueryCount);

        assertThat(queryDslQueryCount)
                .as("QueryDSL은 단일 쿼리로 실행")
                .isEqualTo(1);
    }

    @Test
    void JPQL_전화번호_검색_쿼리_실행_확인() {
        // given
        statistics.clear();

        // when: JPQL 방식
        List<User> result = findAllByPhoneNumberWithProfileJpql(
                Gender.FEMALE,
                "010-2000",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then
        assertThat(result).hasSize(40);
        assertThat(statistics.getQueryExecutionCount())
                .as("JPQL NOT EXISTS 방식 쿼리 실행")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void JPQL과_QueryDSL_전화번호_검색_결과_동일성_검증() {
        // when: JPQL 방식
        List<User> jpqlResult = findAllByPhoneNumberWithProfileJpql(
                Gender.FEMALE,
                "010-2000",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        entityManager.clear();

        // when: QueryDSL 방식
        List<User> queryDslResult = userRepository.findAllByPhoneNumberWithProfile(
                Gender.FEMALE,
                "010-2000",
                testUser.getId(),
                TeamType.TWO_TO_TWO,
                Event.NEUL_2025
        );

        // then
        assertThat(jpqlResult)
                .as("JPQL과 QueryDSL 전화번호 검색 결과가 동일")
                .hasSameSizeAs(queryDslResult)
                .extracting(User::getId)
                .containsExactlyInAnyOrderElementsOf(
                        queryDslResult.stream().map(User::getId).toList()
                );
    }

    // 헬퍼 메서드
    private User createUser(String studentNumber, String nickname, String phoneNumber, String name, Gender gender) {
        User user = User.builder()
                .studentNumber(studentNumber)
                .password("encodedPassword")
                .name(name)
                .phoneNumber(phoneNumber)
                .pushAgree(true)
                .fcmSendTwoTwo(false)
                .build();
        user = userRepository.save(user);
        user.setIsDeleted(false); // Builder에 없으므로 setter 사용

        UserProfile userProfile = UserProfile.builder()
                .nickname(nickname)
                .emoji("😀")
                .music(Music.HIPHOP)
                .mbti(MBTI.ENFP)
                .style(Style.CUTIE)
                .idealType(IdealType.CAT)
                .idealAge(IdealAge.SAME)
                .gender(gender)
                .grade(Grade.FIRST)
                .major(Major.CSIE)
                .age(20)
                .level(Level.LIGHT)
                .user(user)
                .build();
        userProfileRepository.save(userProfile);

        return user;
    }

    private Team createTeam(TeamType teamType, Event event) {
        Team team = Team.builder()
                .name("테스트팀")
                .teamType(teamType)
                .gender(Gender.FEMALE)
                .activeStatus(ActiveStatus.ACTIVE)
                .event(event)
                .build();
        return teamRepository.save(team);
    }

    private void createUserTeam(User user, Team team) {
        UserTeam userTeam = UserTeam.builder()
                .user(user)
                .team(team)
                .build();
        userTeamRepository.save(userTeam);
    }
}
