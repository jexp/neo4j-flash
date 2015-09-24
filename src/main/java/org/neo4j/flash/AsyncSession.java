package org.neo4j.flash;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Func0;
import rx.functions.Func1;

import java.util.Map;

/**
 * @author mh
 * @since 24.09.15
 */
public class AsyncSession {
    private final Session session;

    public AsyncSession(Session session) {
        this.session = session;
    }

    public Observable<Record> run(String query, Map<String, Value> params) {
        return Observable.create(observer -> {
            Result rs = session.run(query, params);
            // return Observable.from(rs.retain());
            try {
                for (Record record : rs.retain()) {
                    try {
                        observer.onNext(record);
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                }
            } finally {
                observer.onCompleted();
            }

        });
    }

    FlashCore core;

    protected Observable<AsyncQueryResult> executeQuery(final String query, final Map<String, Value> params) {
        return Observable.defer(() -> core
                .send(new FlashCore.BoltRequest(query, params)))
                .flatMap(response -> {
                    final Observable<Record> rows = Observable.create(observer -> {
                        try {
                            for (Record record : response.result.retain()) { // todo streaming
                                try {
                                    observer.onNext(record);
                                } catch (Exception e) {
                                    observer.onError(e);
                                }
                            }
                        } finally {
                            observer.onCompleted();
                        }

                    });
                    final Observable<String> fields = Observable.from(response.result.fieldNames());
                    return Observable.just(new AsyncQueryResult(fields, rows));
                });
    }

}
