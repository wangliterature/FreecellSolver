package kw.tony;

import java.nio.file.Path;
import java.util.List;

record ParsedFreeCellDeal(Path sourcePath, FreeCellState state, List<String> normalizedLines) {
}
