package net.lipecki.sqcompanion.group;

import java.util.Date;

/**
 * Created by gregorry on 26.09.2015.
 */
public class IssuesHistoryPoint {

    private Date date;

    private Integer blockers;

    private Integer criticals;

    public IssuesHistoryPoint() {
    }

    public IssuesHistoryPoint(final Date date, final Integer blockers, final Integer criticals) {
        this.date = date;
        this.blockers = blockers;
        this.criticals = criticals;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    public Integer getBlockers() {
        return blockers;
    }

    public void setBlockers(final Integer blockers) {
        this.blockers = blockers;
    }

    public Integer getCriticals() {
        return criticals;
    }

    public void setCriticals(final Integer criticals) {
        this.criticals = criticals;
    }
}
