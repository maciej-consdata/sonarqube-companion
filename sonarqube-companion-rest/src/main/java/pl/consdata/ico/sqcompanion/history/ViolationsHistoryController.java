package pl.consdata.ico.sqcompanion.history;

import io.swagger.annotations.ApiOperation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import pl.consdata.ico.sqcompanion.SQCompanionException;
import pl.consdata.ico.sqcompanion.repository.Group;
import pl.consdata.ico.sqcompanion.repository.Project;
import pl.consdata.ico.sqcompanion.repository.RepositoryService;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/violations/history")
public class ViolationsHistoryController {

    private final ViolationsHistoryService violationsHistoryService;
    private final RepositoryService repositoryService;

    public ViolationsHistoryController(
            final ViolationsHistoryService violationsHistoryService,
            final RepositoryService repositoryService) {
        this.violationsHistoryService = violationsHistoryService;
        this.repositoryService = repositoryService;
    }

    @RequestMapping(
            value = "/group",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ApiOperation(
            value = "Returns group violations history"
    )
    public ViolationsHistory getRootGroupViolationsHistory(@RequestParam Optional<Integer> daysLimit) {
        return violationsHistoryService.getGroupViolationsHistory(repositoryService.getRootGroup(), daysLimit);
    }

    @RequestMapping(
            value = "/group/{uuid}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ApiOperation(
            value = "Returns group violations history"
    )
    public ViolationsHistory getGroupViolationsHistory(@PathVariable final String uuid, @RequestParam Optional<Integer> daysLimit) {
        final Optional<Group> group = repositoryService.getGroup(uuid);
        if (group.isPresent()) {
            return violationsHistoryService.getGroupViolationsHistory(group.get(), daysLimit);
        } else {
            throw new SQCompanionException("Can't find requested group uuid: " + uuid);
        }
    }

    @RequestMapping(
            value = "/group/{uuid}/{fromDate}/{toDate}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ApiOperation(
            value = "Returns group violations history change in time"
    )
    public GroupViolationsHistoryDiff getGroupViolationsHistoryDiff(
            @PathVariable final String uuid,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate fromDate,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate toDate) {
        final Optional<Group> group = repositoryService.getGroup(uuid);
        if (group.isPresent()) {
            return violationsHistoryService.getGroupViolationsHistoryDiff(group.get(), fromDate, toDate);
        } else {
            throw new SQCompanionException("Can't find requested group uuid: " + uuid);
        }
    }

    @RequestMapping(
            value = "/project/{uuid}/{projectKey:.+}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ApiOperation(
            value = "Returns project violations history"
    )
    public ViolationsHistory getProjectViolationsHistory(
            @PathVariable final String uuid,
            @PathVariable final String projectKey,
            @RequestParam Optional<Integer> daysLimit) {
        final Optional<Project> project = repositoryService.getProject(uuid, projectKey);
        if (project.isPresent()) {
            return violationsHistoryService.getProjectViolationsHistory(project.get(), daysLimit);
        } else {
            throw new SQCompanionException("Can't find project: " + projectKey + " in group: " + uuid);
        }
    }

    @RequestMapping(
            value = "/project/{uuid}/{projectKey:.+}/{fromDate}/{toDate}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ApiOperation(
            value = "Returns project violations history change in time"
    )
    public ProjectViolationsHistoryDiff geProjectViolationsHistoryDiff(
            @PathVariable final String uuid,
            @PathVariable final String projectKey,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate fromDate,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate toDate) {
        final Optional<Project> project = repositoryService.getProject(uuid, projectKey);
        if (project.isPresent()) {
            return violationsHistoryService.getProjectViolationsHistoryDiff(project.get(), fromDate, toDate);
        } else {
            throw new SQCompanionException("Can't find project: " + projectKey + " in group: " + uuid);
        }
    }

}
