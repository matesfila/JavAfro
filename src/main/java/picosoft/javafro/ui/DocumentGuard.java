package picosoft.javafro.ui;

import java.util.function.*;

final class DocumentGuard {
    enum Decision { CANCEL, SAVE, DISCARD }
    private DocumentGuard() {}
    static boolean approve(boolean dirty, Supplier<Decision> ask, BooleanSupplier save) {
        if (!dirty) return true;
        return switch (ask.get()) { case CANCEL -> false; case SAVE -> save.getAsBoolean(); case DISCARD -> true; };
    }
}
