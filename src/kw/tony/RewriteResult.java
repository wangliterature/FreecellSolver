package kw.tony;

import java.nio.file.Path;
import java.util.List;

record RewriteResult(boolean solved, String variant, List<String> steps, Path outputFile) {


}
