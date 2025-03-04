package pl.consdata.ico.sqcompanion.violation.user.summary;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.consdata.ico.sqcompanion.config.AppConfig;
import pl.consdata.ico.sqcompanion.config.ServerDefinition;
import pl.consdata.ico.sqcompanion.repository.Project;
import pl.consdata.ico.sqcompanion.repository.RepositoryService;
import pl.consdata.ico.sqcompanion.sonarqube.SonarQubeFacade;
import pl.consdata.ico.sqcompanion.sonarqube.SonarQubeIssuesFacet;
import pl.consdata.ico.sqcompanion.sonarqube.SonarQubeIssuesFacets;
import pl.consdata.ico.sqcompanion.sonarqube.SonarQubeUser;
import pl.consdata.ico.sqcompanion.sonarqube.issues.IssueFilter;
import pl.consdata.ico.sqcompanion.sonarqube.issues.IssueFilterFacet;
import pl.consdata.ico.sqcompanion.users.UsersService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserViolationSummaryHistorySyncService {

    private final UsersService usersService;
    private final UserViolationHistoryRepository repository;
    private final RepositoryService repositoryService;
    private final SonarQubeFacade sonarQubeFacade;
    private final AppConfig appConfig;
    private final Timer syncUserProjectTimer;

    public UserViolationSummaryHistorySyncService(
            final UsersService usersService,
            final UserViolationHistoryRepository repository,
            final SonarQubeFacade sonarQubeFacade,
            final RepositoryService repositoryService,
            final AppConfig appConfig,
            final MeterRegistry meterRegistry) {
        this.usersService = usersService;
        this.repository = repository;
        this.sonarQubeFacade = sonarQubeFacade;
        this.repositoryService = repositoryService;
        this.appConfig = appConfig;
        this.syncUserProjectTimer = meterRegistry.timer("UserProjectViolationsSummaryHistoryService.syncUserProject");
    }

    public void sync() {
        appConfig.getServers().forEach(this::syncServer);
    }

    private void syncServer(final ServerDefinition server) {
        final List<SonarQubeUser> users = usersService.users(server.getId());
        final List<Project> projects = repositoryService.getRootGroup().getAllProjects();
        users.forEach(user -> syncUser(user, projects));
    }

    private void syncUser(final SonarQubeUser user, final List<Project> projects) {
        projects.forEach(project -> {
            try {
                syncUserProject(project, user);
            } catch (final Exception ex) {
                log.warn("Can't sync user project [user={}, project={}]", user.getUserId(), project.getKey(), ex);
            }
        });
    }

    private void syncUserProject(final Project project, final SonarQubeUser user) {
        syncUserProjectTimer.record(() -> {
            final LocalDate today = LocalDate.now();
            final Optional<UserProjectSummaryViolationHistoryEntry> lastMeasure = lastMeasure(project, user);
            final Optional<LocalDate> lastMeasureDate = lastMeasure.map(UserProjectSummaryViolationHistoryEntry::getDate);
            if (lastMeasureDate.isPresent() && !lastMeasureDate.get().isBefore(today.minusDays(1))) {
                log.debug("All historic analysis already synchronized");
                return;
            }

            log.info("Syncing user summary violations [userId={}, project={}]", user.getUserId(), project.getKey());
            final LocalDate dateToSync = today.minusDays(1);

            if (lastMeasure.isPresent()) {
                useLastKnownMeasureForMissingDates(project, user, lastMeasure, lastMeasureDate, dateToSync);
            }

            final SonarQubeIssuesFacet severities = userIssues(project, user, dateToSync);
            repository.save(
                    UserProjectSummaryViolationHistoryEntry.builder()
                            .id(
                                    UserProjectSummaryViolationHistoryEntry.combineId(
                                            project.getServerId(),
                                            user.getUserId(),
                                            project.getKey(),
                                            dateToSync
                                    )
                            )
                            .date(dateToSync)
                            .serverId(project.getServerId())
                            .userId(user.getUserId())
                            .projectKey(project.getKey())
                            .blockers(facetSeverityCount(severities, "BLOCKER"))
                            .criticals(facetSeverityCount(severities, "CRITICAL"))
                            .majors(facetSeverityCount(severities, "MAJOR"))
                            .minors(facetSeverityCount(severities, "MINOR"))
                            .infos(facetSeverityCount(severities, "INFO"))
                            .build()
            );
        });
    }

    private SonarQubeIssuesFacet userIssues(Project project, SonarQubeUser user, LocalDate dateToSync) {
        final SonarQubeIssuesFacets userIssuesFacets = sonarQubeFacade.issuesFacet(
                project.getServerId(),
                IssueFilter.builder()
                        .componentKey(project.getKey())
                        .author(user.getUserId())
                        .createdBefore(dateToSync)
                        .resolved(false)
                        .facet(IssueFilterFacet.SEVERITIES)
                        .build()
        );
        return userIssuesFacets
                .getFacet(IssueFilterFacet.SEVERITIES)
                .orElseThrow(() -> new IllegalStateException("Expected facet not returned from SonarQube!"));
    }

    private void useLastKnownMeasureForMissingDates(Project project, SonarQubeUser user, Optional<UserProjectSummaryViolationHistoryEntry> lastMeasure, Optional<LocalDate> lastMeasureDate, LocalDate dateToSync) {
        for (LocalDate syncMissingDate = lastMeasureDate.get(); syncMissingDate.isBefore(dateToSync); syncMissingDate = syncMissingDate.plusDays(1)) {
            repository.save(
                    UserProjectSummaryViolationHistoryEntry.builder()
                            .id(
                                    UserProjectSummaryViolationHistoryEntry.combineId(
                                            project.getServerId(),
                                            user.getUserId(),
                                            project.getKey(),
                                            syncMissingDate
                                    )
                            )
                            .date(syncMissingDate)
                            .serverId(project.getServerId())
                            .userId(user.getUserId())
                            .projectKey(project.getKey())
                            .blockers(lastMeasure.get().getBlockers())
                            .criticals(lastMeasure.get().getCriticals())
                            .majors(lastMeasure.get().getMajors())
                            .minors(lastMeasure.get().getMinors())
                            .infos(lastMeasure.get().getInfos())
                            .build()
            );
        }
    }

    private int facetSeverityCount(SonarQubeIssuesFacet severities, String severity) {
        return Integer.parseInt(severities.getByProperty("val", severity, "count"));
    }

    private Optional<UserProjectSummaryViolationHistoryEntry> lastMeasure(final Project project, final SonarQubeUser user) {
        return repository.findFirstByUserIdAndProjectKeyOrderByDateDesc(user.getUserId(), project.getKey());
    }

}
