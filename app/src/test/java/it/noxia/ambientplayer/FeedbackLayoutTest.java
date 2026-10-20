package it.noxia.ambientplayer;
import org.junit.Test;import static org.junit.Assert.*;
public class FeedbackLayoutTest {
 @Test public void everyVisibleCombinationIsCenteredAndDoesNotOverlap(){for(int mask=1;mask<8;mask++){boolean a=(mask&1)!=0,b=(mask&2)!=0,c=(mask&4)!=0;int n=0,sum=0,last=-1000;for(int i=0;i<3;i++){if((mask&(1<<i))==0)continue;int left=FeedbackLayout.left(a,b,c,i);assertTrue(left>=last+48);last=left;sum+=left+24;n++;}assertEquals(0,sum/n);}}
 @Test public void shuffleAloneIsCenteredAndExistingPairsKeepTheirSpacing(){assertEquals(-24,FeedbackLayout.left(false,false,true,2));assertEquals(-72,FeedbackLayout.left(true,false,true,0));assertEquals(24,FeedbackLayout.left(true,false,true,2));}
}
