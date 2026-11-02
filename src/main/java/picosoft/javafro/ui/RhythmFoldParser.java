package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.DumperOptions;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Only top-level rhythm definitions are collapsible; comments cannot become fake rhythms. */
final class RhythmFoldParser implements FoldParser {
    @Override public List<Fold> getFolds(RSyntaxTextArea editor) {
        var folds = new ArrayList<Fold>();
        String source = editor.getText();
        try {
            var root = new Yaml().compose(new StringReader(source));
            if (!(root instanceof MappingNode mapping) || mapping.getFlowStyle() == DumperOptions.FlowStyle.FLOW) return folds;
            var entries = mapping.getValue();
            for (int i = 0; i < entries.size(); i++) {
                int start = source.offsetByCodePoints(0, entries.get(i).getKeyNode().getStartMark().getIndex());
                int end = i + 1 < entries.size()
                        ? editor.getLineStartOffset(entries.get(i + 1).getKeyNode().getStartMark().getLine()) - 1
                        : source.length() - 1;
                if (end <= start || editor.getLineOfOffset(end) <= editor.getLineOfOffset(start)) continue;
                var fold = new Fold(FoldType.CODE, editor, start);
                fold.setEndOffset(end);
                folds.add(fold);
            }
        } catch (org.yaml.snakeyaml.error.YAMLException | javax.swing.text.BadLocationException exception) {
            // An incomplete YAML document remains editable while the user is typing.
        }
        return folds;
    }
}
