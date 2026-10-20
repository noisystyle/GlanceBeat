package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoverDragTest {
 @Test public void followsFingerUntilOneCard() {CoverDrag d=new CoverDrag();assertEquals(-75,d.update(-75,200,true,true).displacement(),.001);}
 @Test public void detentOnlyOnThresholdCrossing() {CoverDrag d=new CoverDrag();assertFalse(d.update(79,200,true,true).detent());assertTrue(d.update(80,200,true,true).detent());assertFalse(d.update(120,200,true,true).detent());}
 @Test public void retreatCancelsWithHysteresis() {CoverDrag d=new CoverDrag();d.update(90,200,true,true);assertEquals(1,d.update(65,200,true,true).armed());assertEquals(0,d.update(59,200,true,true).armed());assertTrue(d.update(85,200,true,true).detent());}
 @Test public void unavailableNeighborNeverArms() {CoverDrag d=new CoverDrag();var s=d.update(500,200,false,true);assertEquals(0,s.armed());assertFalse(s.detent());assertTrue(s.displacement()<18.01);}
 @Test public void overscrollStaysBounded() {CoverDrag d=new CoverDrag();assertTrue(d.update(-2000,200,true,true).displacement()>=-232);}
 @Test public void reversingDirectionRequiresOtherThreshold() {CoverDrag d=new CoverDrag();d.update(90,200,true,true);assertEquals(0,d.update(-20,200,true,true).armed());assertEquals(-1,d.update(-90,200,true,true).armed());d.reset();assertEquals(0,d.armed());}
}
