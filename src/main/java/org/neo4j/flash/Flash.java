package org.neo4j.flash;

import org.neo4j.driver.Config;
import org.neo4j.driver.GraphDatabase;
import rx.Observable;

/**
 * @author mh
 * @since 24.09.15
 */
public class Flash {
    public static Observable<AsyncSession> open(String url) {
        Config config = Config.build().toConfig();
        return Observable.just(new AsyncSession(GraphDatabase.driver(url).session(), new FlashCore(new FlashCore.FlashEnvironment(), config)));
    }
}
