package net.lipecki.sqcompanion.repository;

import com.google.common.collect.Iterables;
import net.lipecki.sqcompanion.sonarqube.SonarQubeService;
import net.lipecki.sqcompanion.sonarqube.issue.SonarQubeIssuesIssueResultDto;
import net.lipecki.sqcompanion.sonarqube.timemachine.SonarQubeTimeMachineResultCellsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.jvm.hotspot.utilities.Interval;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

import static java.lang.Integer.max;
import static java.lang.Integer.parseInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Provides actual groups with projects data.
 * <p>
 * Abstraction layer over remote systems.
 * </p>
 */
public class RepositoryService {

    public static final int IDX_BLOCKER = 0;
    public static final int IDX_CRITICAL = 1;
    public static final int IDX_MAJOR = 2;
    public static final int IDX_MINOR = 3;
    public static final int IDX_INFO = 4;
    private final LayoutProvider layoutProvider;
    private final SonarQubeService sonarQubeService;
    private RepositoryModel repositoryModel;

    public RepositoryService(final LayoutProvider layoutProvider, final SonarQubeService sonarQubeService) {
        this.layoutProvider = layoutProvider;
        this.sonarQubeService = sonarQubeService;
    }

    public void loadData() {
        final Long startTime = System.currentTimeMillis();

        LOGGER.info(">>> Loading repository layout");
        final Map<String, Project> loadedProjects = layoutProvider.getProjects()
                .stream()
                .map(config -> loadProjectData(config))
                .collect(toMap(Project::getKey, identity()));

        final Map<String, Group> loadedGroups = layoutProvider.getGroups()
                .stream()
                .map(config -> loadGroupData(config, loadedProjects))
                .collect(toMap(Group::getKey, identity()));

        final Long endTime = System.currentTimeMillis();
        LOGGER.info("<<< repository layout loaded [time={}]", (endTime - startTime));

        setRepositoryModel(RepositoryModel.of(loadedGroups, loadedProjects));
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryService.class);

    private void setRepositoryModel(final RepositoryModel repositoryModel) {
        this.repositoryModel = repositoryModel;
    }

    public RepositoryModel getRepositoryModel() {
        return repositoryModel;
    }

    private Group loadGroupData(final GroupConfiguration config, final Map<String, Project> projects) {
        final Group group = new Group(config.getKey(), config.getName(), "");

        group.setProjects(config.getProjects()
                .stream()
                .map(key -> projects.get(key))
                .collect(toList()));
        group.setIssues(Issues.of(group.getProjects()
                .stream()
                .map(project -> project.getIssues().getAll())
                .collect(ArrayList::new, List::addAll, List::addAll)));


//        mergeHistoryPart1(group);
//        mergeHistoryPart2(group);
        mergeHistoryPart3(group);

        return group;
    }

    private void mergeHistoryPart3(final Group group) {
        final List<HistoryPoint> fullHistoryList = group.getProjects()
                .stream()
                .map(p -> p.getHistory().getHistoryPoints().values())
                .collect(ArrayList<HistoryPoint>::new, List::addAll, List::addAll)
                .stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(toList());
        if(!fullHistoryList.isEmpty()) {
            // TODO use todays date or max from date list? final LocalDate maxDate = LocalDate.now();
            final LocalDate minDate = Iterables.getFirst(fullHistoryList, null).getDate();
            final LocalDate maxDate = Iterables.getLast(fullHistoryList).getDate();

            List<HistoryPoint> points = new ArrayList<>();
            for (LocalDate date = minDate; !date.isAfter(maxDate); date = date.plusDays(1)) {
                final HistoryPoint historyPoint = new HistoryPoint(date, 0, 0, 0, 0, 0);
                for (final Project project : group.getProjects()) {
                    final History projectHistory = project.getHistory();
                    final HistoryPoint projectHistoryPoint = projectHistory.getHistoryPoints().get(date);
                    if (projectHistoryPoint != null) {
                        historyPoint.addOtherPoint(projectHistoryPoint);
                    } else {
                        final HistoryPoint lastPointBeforeDate = projectHistory.getOrderedHistoryPoints()
                                .stream()
                                .filter(p -> !p.getDate().isAfter(historyPoint.getDate()))
                                .reduce((a, b) -> b)
                                .orElse(new HistoryPoint(historyPoint.getDate()));
                        historyPoint.addOtherPoint(lastPointBeforeDate);
                    }
                }
                points.add(historyPoint);
            }

            group.setHistory(History.of(points.stream().collect(toMap(p -> p.getDate(), Function.<HistoryPoint>identity()))));
        }
    }

    /**
     * Can't handle empty analysis days.
     * @param group
     */
    private void mergeHistoryPart1(final Group group) {
        group.setHistory(History.of(group.getProjects()
                .stream()
                .map(project -> project.getHistory().getHistoryPoints().values())
                .collect(ArrayList<HistoryPoint>::new, List::addAll, List::addAll).stream()
                .collect(
                        toMap(
                                HistoryPoint::getDate,
                                identity(),
                                HistoryPoint::merge
                        )
                )));
    }

    /**
     * Wat to hard to read!
     * @param group
     */
    private void mergeHistoryPart2(final Group group) {
        // map all projects to dates
        final Map<LocalDate, Map<String, HistoryPoint>> histories = new HashMap<>();
        for (final Project project : group.getProjects()) {
            for (final HistoryPoint point : project.getHistory().getHistoryPoints().values()) {
                if (!histories.containsKey(point.getDate())) {
                    histories.put(point.getDate(), new HashMap<>());
                }
                histories.get(point.getDate()).put(project.getKey(), point);
            }
        }
        // fill missing project entries
        final List<LocalDate> datesToAnalyze = histories.keySet().stream().sorted().collect(toList());
        for (int dateIdx = 0; dateIdx < datesToAnalyze.size(); ++dateIdx) {
            final LocalDate key = datesToAnalyze.get(dateIdx);
            final Map<String, HistoryPoint> value = histories.get(key);

            for (final Project project : group.getProjects()) {
                final String projectKey = project.getKey();
                if (!value.containsKey(projectKey)) {
                    if (dateIdx > 0 && histories.get(datesToAnalyze.get(dateIdx - 1)).containsKey
                            (projectKey)) {
                        final HistoryPoint lastKnownMetric = histories.get(datesToAnalyze.get(dateIdx - 1)).get(projectKey);
                        value.put(
                                projectKey,
                                new HistoryPoint(
                                        key,
                                        lastKnownMetric.getBlockers(),
                                        lastKnownMetric.getCriticals(),
                                        lastKnownMetric.getMajors(),
                                        lastKnownMetric.getMinors(),
                                        lastKnownMetric.getInfos()));
                    } else {
                        value.put(projectKey, new HistoryPoint(key));
                    }
                }
            }
        }
        // sum values for all dates
        final Collection<Map<String, HistoryPoint>> values = histories.values();
        final Map<LocalDate,HistoryPoint> h = values
                .stream()
                .map(col -> col.values())
                .collect(ArrayList<HistoryPoint>::new, List::addAll, List::addAll).stream()
                .collect(
                        toMap(
                                HistoryPoint::getDate,
                                identity(),
                                HistoryPoint::merge
                        )
                );
        group.setHistory(History.of(
                h
        ));
    }

    private Project loadProjectData(final ProjectConfiguration config) {
        final Project project = new Project(config.getKey(), config.getKey(), config.getSonarqubeKey(), "");

        project.setIssues(
                Issues.of(
                        project,
                        Arrays.stream(sonarQubeService
                                .getIssues(project.getSonarqubeKey())
                                .getIssues())
                                .map(issue -> issueAsModel(issue))
                                .collect(toList())));
        project.setHistory(
                History.of(
                        Arrays.stream(sonarQubeService
                                .getHistory(project.getSonarqubeKey())
                                .getCells())
                                .sorted((cell1, cell2) -> cell1.getD().compareTo(cell2.getD()))
                                .map(cell -> historyPointAsModel(cell))
                                .collect(
                                        toMap(
                                                HistoryPoint::getDate,
                                                identity(),
                                                (a, b) -> b
                                        )
                                )));

        return project;
    }


    private HistoryPoint historyPointAsModel(final SonarQubeTimeMachineResultCellsDto cell) {
        final HistoryPoint historyPoint = new HistoryPoint(convertDateToLocalDate(cell.getD()));

        historyPoint.addBlockers(parseInt(cell.getV()[IDX_BLOCKER]));
        historyPoint.addCriticals(parseInt(cell.getV()[IDX_CRITICAL]));
        historyPoint.addMajors(parseInt(cell.getV()[IDX_MAJOR]));
        historyPoint.addMinors(parseInt(cell.getV()[IDX_MINOR]));
        historyPoint.addInfos(parseInt(cell.getV()[IDX_INFO]));

        return historyPoint;
    }

    private Issue issueAsModel(final SonarQubeIssuesIssueResultDto dto) {
        return new Issue(dto.getKey(), dto.getComponent(), dto.getMessage(), IssueSeverity.parse(dto.getSeverity()),
                dto.getCreationDate());
    }

    private LocalDate convertDateToLocalDate(final Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

}
