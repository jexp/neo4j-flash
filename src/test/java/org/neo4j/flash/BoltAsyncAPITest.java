package org.neo4j.flash;

import org.neo4j.driver.*;
import rx.Observable;

import static org.junit.Assert.assertEquals;

/**
 * @author mh
 * @since 24.09.15
 */
public class BoltAsyncAPITest {

    @org.junit.Test
    public void testConnectToBolt() throws Exception {

        Observable<AsyncSession> session = Flash.open("bolt://localhost");

        session.forEach((s) -> {
            Observable<Record> result = s.runAsync(
                    "MATCH (n:Person) where n.born > {born} return n",
                    Values.parameters("born", 1980));

            result.forEach((r) -> assertEquals(true,r.get("n").get("born").javaInteger() > 1980 ));

            result.map((r) -> r.get("n")).map((r) -> r.get("name").javaString())
//                  .onErrorResumeNext((f)-> Observable.just("no record","no rec))
//                  .onErrorReturn((f)-> "no record")
                    .doOnError((e) -> System.err.println("Error during streaming " + e.getMessage()))
                    .forEach(System.out::println);
        });
    }
}

