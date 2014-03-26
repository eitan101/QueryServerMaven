package db.infra;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import db.data.Pm;
import db.data.Target;
import db.infra.ChangeEvent;
import events.ChangePair;
import db.infra.DenormalizedEntity;
import events.EventsStream;
import events.PushStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author handasa
 */
public class DenormalizedTest {

    PushStream<ChangeEvent<Pm>> pmPublisher;
    PushStream<ChangeEvent<Target>> tgtPublisher;
    EventsStream<ChangePair<DenormalizedEntity<Pm>>> queryOutput;
    private ArrayList<ChangePair<DenormalizedEntity<Pm>>> res;

    public DenormalizedTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        ExecutorService exec = MoreExecutors.sameThreadExecutor();
        res = new ArrayList<>();
        pmPublisher = new PushStream<>();
        tgtPublisher = new PushStream<>();
        QueryServerExample qs = new QueryServerExample(exec, pmPublisher, tgtPublisher).start();       
        queryOutput = qs.getDenormlizedPm().output();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void registerBefore() {
        queryOutput.register(t -> res.add(t));
        tgtPublisher.publish(new ChangeEvent<>(ChangeEvent.ChangeType.update, new Target(1, null, "myTg")));
        pmPublisher.publish(new ChangeEvent<>(ChangeEvent.ChangeType.update, new Pm(1, "myPm", null, 1, new Date())));
        assertEquals(1, res.size());
        assertEquals("myPm", res.get(0).getNew().getParent().getName());
    }


    @Test
    public void registerAfter() {
        tgtPublisher.publish(new ChangeEvent<>(ChangeEvent.ChangeType.update, new Target(1, null, "myTg")));
        pmPublisher.publish(new ChangeEvent<>(ChangeEvent.ChangeType.update, new Pm(1, "myPm", null, 1, new Date())));
        queryOutput.register(t -> res.add(t));
        assertEquals(1, res.size());
        assertEquals("myPm", res.get(0).getNew().getParent().getName());
        assertEquals(null, res.get(0).getOld());        
    }

    @Test
    public void testSubEntity() {
        final Pm pm = new Pm(1, "name", null, 1, new Date());
        final Target target = new Target(1, null, "target");
        final Pm subPm = new Pm(2, "IamTheSon", null, 1, new Date());
        DenormalizedEntity<Pm> de = new DenormalizedEntity<>(pm, ImmutableMap.of("main",target,"son",subPm));
        assertEquals("target", de.getSubEntity(Target.class, "main").get().getName());
        assertEquals(1, de.getSubEntity(Pm.class, "son").get().getTargetId());
    }
    
    @Test(expected=RuntimeException.class)
    public void testNoSubEntity() {
        final Pm pm = new Pm(1, "name", null, 1, new Date());
        final Target target = new Target(1, null, "target");
        final Pm subPm = new Pm(2, "IamTheSon", null, 1, new Date());
        DenormalizedEntity<Pm> de = new DenormalizedEntity<>(pm,ImmutableMap.of("main",target,"son",subPm));
        de.getSubEntity(Target.class, "secondary");
    }
}

