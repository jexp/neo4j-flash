package org.neo4j.flash;

import org.neo4j.driver.Record;
import rx.Observable;

/**
 * @author mh
 * @since 24.09.15
 */
public class AsyncQueryResult {

    public final Observable<String> fields;
    public final Observable<Record> rows;
/*
    private final Observable<Record> errors;
    private final Observable<Record> notifications;
    private final Observable<Record> stats;
    private final Observable<Record> queryPlan;
*/
    public AsyncQueryResult(Observable<String> fields, Observable<Record> rows) {
        this.fields = fields;
        this.rows = rows;
    }
}
