package ro.dotdesign.updates;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateDecisionTest {
    private UpdateDecision.Mode mode(boolean required, boolean immediate, boolean flexible, long age) {
        return UpdateDecision.decide(100,102,true,immediate,flexible,true,required,age);
    }
    @Test public void updatesAreOptionalByDefault(){assertEquals(UpdateDecision.Mode.OPTIONAL,mode(false,true,true,0));}
    @Test public void explicitInstalledBuildIsRequired(){assertEquals(UpdateDecision.Mode.REQUIRED,mode(true,true,true,0));}
    @Test public void immediateNotAllowedNeverBlocks(){assertEquals(UpdateDecision.Mode.OPTIONAL,mode(true,false,true,0));}
    @Test public void unofferedInReviewOrIneligibleNeverBlocks(){assertEquals(UpdateDecision.Mode.NONE,UpdateDecision.decide(100,102,false,true,true,true,true,0));}
    @Test public void sameVersionNeverPrompts(){assertEquals(UpdateDecision.Mode.NONE,UpdateDecision.decide(102,102,true,true,true,true,true,0));}
    @Test public void olderVersionNeverPrompts(){assertEquals(UpdateDecision.Mode.NONE,UpdateDecision.decide(103,102,true,true,true,true,true,0));}
    @Test public void unverifiedPolicyNeverBlocks(){assertEquals(UpdateDecision.Mode.NONE,UpdateDecision.decide(100,102,true,true,true,false,true,0));}
    @Test public void stalePolicyNeverBlocks(){assertEquals(UpdateDecision.Mode.NONE,mode(true,true,true,3_600_000));}
    @Test public void negativeMonotonicAgeNeverBlocks(){assertEquals(UpdateDecision.Mode.NONE,mode(true,true,true,-1));}
    @Test public void missingPlayFlowNeverBlocks(){assertEquals(UpdateDecision.Mode.NONE,mode(true,false,false,0));}
    @Test public void optionalNeedsFlexibleFlow(){assertEquals(UpdateDecision.Mode.NONE,mode(false,true,false,0));}
    @Test public void firstPromptIsAllowed(){assertTrue(UpdateDecision.shouldPrompt(1_000,0));}
    @Test public void promptIsSuppressedForFullDay(){assertFalse(UpdateDecision.shouldPrompt(86_400_999,1_000));}
    @Test public void promptReturnsAfterDay(){assertTrue(UpdateDecision.shouldPrompt(86_401_000,1_000));}
    @Test public void clockRollbackDoesNotRepeatPrompt(){assertFalse(UpdateDecision.shouldPrompt(999,1000));}
}
