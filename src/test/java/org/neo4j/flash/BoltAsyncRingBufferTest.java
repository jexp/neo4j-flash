package org.neo4j.flash;

import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import rx.Observable;

/**
 * @author mh
 * @since 24.09.15
 */
public class BoltAsyncRingBufferTest {

    @org.junit.Test
    public void testConnectToBolt() throws Exception {

        Observable<AsyncSession> session = Flash.open("bolt://localhost");

        session.forEach((s) -> {
            // todo tx
            Observable<AsyncQueryResult> result = s.executeQuery(
                    "MATCH (n:Person {name:{name}}) return n",
                    Values.parameters("name", "Tom Hanks"));
            result.forEach((r) -> r.fields.forEach(System.out::println));

            result.flatMap((r) -> r.rows).map((r2) -> r2.get("n").get("name").javaString()).forEach(System.out::println);
        });
    }
}

