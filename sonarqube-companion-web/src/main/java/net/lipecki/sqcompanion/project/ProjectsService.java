package net.lipecki.sqcompanion.project;

import net.lipecki.sqcompanion.group.StatusCode;
import net.lipecki.sqcompanion.sonarqube.SonarQubeService;
import net.lipecki.sqcompanion.sonarqube.issue.SonarQubeIssuesIssueResultDto;
import net.lipecki.sqcompanion.sonarqube.timemachine.SonarQubeTimeMachineResultsDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by gregorry on 26.09.2015.
 */
public class ProjectsService {

    private final SonarQubeService collector;

    public ProjectsService(final SonarQubeService collector) {
        this.collector = collector;
    }

    public SonarQubeTimeMachineResultsDto getHistoricalData(final String id) {
        return collector.getTimeMachineResults(id, Arrays.asList("blocker_violations", "critical_violations"));
    }

    private List<SonarQubeIssuesIssueResultDto> getIssues(final String id, final String severity) {
        final SonarQubeIssuesIssueResultDto[] issues = collector.getProjectIssues(id, severity).getIssues();
        return issues != null ? Arrays.asList(issues) : new ArrayList<>();
    }

    public List<SonarQubeIssuesIssueResultDto> getBlockers(final String id) {
        return getIssues(id, "BLOCKER");
    }

    public List<SonarQubeIssuesIssueResultDto> getCriticals(final String id) {
        return getIssues(id, "CRITICAL");
    }

    public List<SonarQubeIssuesIssueResultDto> getOtherIssues(final String id) {
        return getIssues(id, "MAJOR,MINOR,INFO");
    }

    public int getBlockerCount(final String id) {
        return getBlockers(id).size();
    }

    public int getCriticalCount(final String id) {
        return getCriticals(id).size();
    }

    public int getOtherIssueCount(final String id) {
        return getOtherIssues(id).size();
    }

    public ProjectSummary getProjectSummary(final String id) {
        final ProjectSummary projectSummary = new ProjectSummary(id, id);

        projectSummary.setBlockers(getBlockerCount(id));
        projectSummary.setCriticals(getCriticalCount(id));

        if (projectSummary.getBlockers() > 0) {
            projectSummary.setStatus(StatusCode.BLOCKER);
        } else if (projectSummary.getCriticals() > 0) {
            projectSummary.setStatus(StatusCode.CRITICAL);
        } else {
            projectSummary.setStatus(StatusCode.HEALTHY);
        }

        return projectSummary;
    }

}
