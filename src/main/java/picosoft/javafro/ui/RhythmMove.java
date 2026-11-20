package picosoft.javafro.ui;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Swaps the rhythm under the caret with its neighbour (a port of web {@code rhythm-move.ts}).
 * A rhythm is a top-level key from the start of its line up to the next top-level key, so
 * comments and blank lines below a rhythm travel with it. Comments above the first rhythm stay put.
 */
final class RhythmMove {
    /** Start offset of a swapped rhythm in the old text and in the new text. */
    record Block(int start, int moved) { }

    /** Replace {@code from..to} with {@code insert}; {@code caret} keeps its place inside the moved rhythm. */
    record Move(int from, int to, String insert, int caret, List<Block> blocks) {
        String apply(String source) { return source.substring(0, from) + insert + source.substring(to); }
    }

    private RhythmMove() { }

    /** @param direction -1 moves the rhythm above the previous one, 1 below the next one; null when it cannot move. */
    static Move move(String source, int caret, int direction) {
        List<Integer> starts = new ArrayList<>();
        try {
            if (!(new Yaml().compose(new StringReader(source)) instanceof MappingNode mapping)
                    || mapping.getFlowStyle() == DumperOptions.FlowStyle.FLOW) return null;
            for (var entry : mapping.getValue()) {
                int key = source.offsetByCodePoints(0, entry.getKeyNode().getStartMark().getIndex());
                starts.add(source.lastIndexOf('\n', key - 1) + 1);
            }
        } catch (RuntimeException exception) {
            return null; // An incomplete YAML document has no reliable rhythm boundaries.
        }
        int index = 0;
        while (index + 1 < starts.size() && starts.get(index + 1) <= caret) index++;
        int other = index + direction;
        if (other < 0 || other >= starts.size()) return null;

        int first = Math.min(index, other), second = first + 1;
        int end = second + 1 < starts.size() ? starts.get(second + 1) : source.length();
        String upper = source.substring(starts.get(first), starts.get(second));
        String lower = source.substring(starts.get(second), end);
        if (!lower.endsWith("\n")) {
            // The last rhythm has no final line break: keep it that way after the swap.
            String lineBreak = upper.endsWith("\r\n") ? "\r\n" : "\n";
            lower += lineBreak;
            upper = upper.substring(0, upper.length() - lineBreak.length());
        }
        int from = starts.get(first);
        int inside = Math.max(0, caret - starts.get(index));
        int caretAt = index == second ? from + Math.min(inside, lower.length())
                : from + lower.length() + Math.min(inside, upper.length());
        return new Move(from, end, lower + upper, caretAt, List.of(
                new Block(starts.get(second), from), new Block(starts.get(first), from + lower.length())));
    }
}
