package org.neo4j.flash;

import org.neo4j.driver.*;

import static org.junit.Assert.assertEquals;

/**
 * @author mh
 * @since 24.09.15
 */
public class BoltConnectionTest {

    @org.junit.Test
    public void testConnectToBolt() throws Exception {
        Driver driver = GraphDatabase.driver("bolt://localhost");

        try (Session session = driver.session()) {
            Result rs = session.run("MATCH (n:Person {name:{name}}) return n",Values.parameters( "name", "Tom Hanks" ));
            assertEquals(true,rs.next());
            Value n = rs.get("n");
            assertEquals("Tom Hanks", n.get("name").javaString());
            assertEquals(1956, n.get("born").javaInteger());
            assertEquals(false,rs.next());
        }
    }
}

