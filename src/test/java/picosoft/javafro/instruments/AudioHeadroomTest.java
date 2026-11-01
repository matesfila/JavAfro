package picosoft.javafro.instruments;

import org.junit.Test;
import picosoft.javafro.base.AudioConstants;
import static org.junit.Assert.*;

public class AudioHeadroomTest {
    private short pcm(double amplitude) {
        byte[] buffer = new byte[2];
        AudioConstants.putSample(buffer, 0, amplitude);
        return (short) ((buffer[0] & 0xff) | (buffer[1] << 8));
    }
    @Test public void masterGainPreservesHeadroomForLoudSamplesAndDrumBellSums() {
        assertEquals(Short.MAX_VALUE / 2, pcm(2));
        assertEquals(Short.MAX_VALUE, pcm(4));
        assertEquals(Short.MAX_VALUE, pcm(8));
        assertEquals(-Short.MAX_VALUE, pcm(-8));
    }
}
