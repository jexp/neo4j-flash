package org.neo4j.flash;

import org.neo4j.driver.*;
import org.neo4j.driver.internal.SimpleRecord;
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
            // todo tx
            Observable<Record> result = s.run(
                    "MATCH (n:Person {name:{name}}) return n",
                    Values.parameters("name","Tom Hanks"));
            result
                    .map((r) -> r.get("n").get("name").javaString())
//                    .onErrorResumeNext((f)-> Observable.just("no record","no rec))
                    .onErrorReturn((f)-> "no record")
//                    .doOnError((e)-> System.err.println("Error during streaming "+e.getMessage()))
                    .forEach(System.out::println);
        });
    }
}

