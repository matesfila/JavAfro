package picosoft.javafro.ui;

import org.junit.Test;
import picosoft.javafro.base.Dialect;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class DialectControlsTest {
    private List<JSlider> sliders(Container parent) {
        List<JSlider> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (component instanceof JSlider slider) result.add(slider);
            else if (component instanceof Container container) result.addAll(sliders(container));
        }
        return result;
    }
    @Test
    public void everyPulseHasAnIndependentFullRangeSlider() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Dialect> changes = new ArrayList<>();
            var controls = new DialectControls(changes::add);
            controls.setDialect(Dialect.straight(4));
            var sliders = sliders(controls);
            assertEquals(4, sliders.size());
            sliders.get(1).setValue(-40);
            assertEquals(Dialect.of(4, List.of(0, -0.4, 0, 0)), controls.value());
            sliders.get(0).setValue(100);
            sliders.get(3).setValue(-100);
            assertEquals(Dialect.of(4, List.of(1, -0.4, 0, -1)), controls.value());
            assertEquals(controls.value(), changes.getLast());
            assertEquals(-40, sliders.get(1).getValue());
            assertEquals(0, sliders.get(2).getValue());
            for (var slider : sliders) {
                assertEquals(-100, slider.getMinimum()); assertEquals(100, slider.getMaximum());
            }
            controls.setDialect(Dialect.straight(3)); assertEquals(3, sliders(controls).size());
            controls.setDialect(Dialect.straight(1)); assertEquals(1, sliders(controls).size());
        });
    }
}
