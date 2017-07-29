package pl.consdata.ico.sqcompanion.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

/**
 * @author pogoma
 */
@Entity(name = "synchronization")
@Table
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class SynchronizationState {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @NotNull
    private Long startTimestamp;
    private Long finishTimestamp;
    private Long allTasks;
    private Long finishedTasks;
    private Long failedTasks;

}
