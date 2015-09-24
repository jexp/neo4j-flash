package org.neo4j.flash;

import org.neo4j.driver.GraphDatabase;
import rx.Observable;

/**
 * @author mh
 * @since 24.09.15
 */
public class Flash {
    public static Observable<AsyncSession> open(String url) {

        return Observable.just(new AsyncSession(GraphDatabase.driver(url).session()));
    }
}
