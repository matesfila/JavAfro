package picosoft.javafro.io;

import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.representer.Representer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Invalid work is always saved verbatim; comments and other rhythms are retained. Controls are written into
 * the selected rhythm when it is valid, even if another rhythm is broken (a YAML syntax error keeps the text).
 */
public final class RhythmExport {
    private RhythmExport() {}
    public static String export(String source, DesktopDraftStore.Controls controls, int defaultBpm) {
        if (controls == null) return source;
        try {
            // Another broken rhythm must not stop the selected one's controls from being saved.
            var rhythms = RhythmLoader.valid(source, defaultBpm);
            var selected = rhythms.get(controls.rhythm());
            if (selected == null || selected.stepsPerBeat() != controls.dialect().size()) return source;
            boolean tempoChanged = selected.bpm() != controls.bpm();
            boolean dialectChanged = !selected.dialect().offsets().equals(controls.dialect());
            if (!tempoChanged && !dialectChanged) return source;
            LoaderOptions loader = new LoaderOptions(); loader.setProcessComments(true); loader.setAllowDuplicateKeys(false);
            DumperOptions dumper = new DumperOptions(); dumper.setProcessComments(true);
            Yaml yaml = new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
            MappingNode root = (MappingNode) yaml.compose(new StringReader(source));
            for (NodeTuple tuple : root.getValue()) {
                if (((ScalarNode) tuple.getKeyNode()).getValue().equals(controls.rhythm())) {
                    MappingNode definition = (MappingNode) tuple.getValueNode();
                    if (tempoChanged) put(definition, "bpm", scalar(Tag.INT, Integer.toString(controls.bpm())));
                    if (dialectChanged) {
                        List<Node> offsets = controls.dialect().stream().map(value -> (Node) scalar(Tag.FLOAT, Double.toString(value))).toList();
                        put(definition, "dialect", new SequenceNode(Tag.SEQ, offsets, DumperOptions.FlowStyle.FLOW));
                    }
                }
            }
            StringWriter out = new StringWriter(); yaml.serialize(root, out); return out.toString();
        } catch (RuntimeException exception) { return source; }
    }
    private static ScalarNode scalar(Tag tag, String value) { return new ScalarNode(tag, value, null, null, DumperOptions.ScalarStyle.PLAIN); }
    private static void put(MappingNode map, String key, Node value) {
        for (int i = 0; i < map.getValue().size(); i++) {
            NodeTuple tuple = map.getValue().get(i);
            if (((ScalarNode) tuple.getKeyNode()).getValue().equals(key)) {
                value.setBlockComments(tuple.getValueNode().getBlockComments());
                value.setInLineComments(tuple.getValueNode().getInLineComments());
                value.setEndComments(tuple.getValueNode().getEndComments());
                map.getValue().set(i, new NodeTuple(tuple.getKeyNode(), value)); return;
            }
        }
        map.getValue().add(new NodeTuple(scalar(Tag.STR, key), value));
    }
}
