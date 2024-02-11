import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.utils.Utils;

public class PrintExecSampleClass {
  public static void main(String[] args) {
    Utils.asGraph(e -> ImmutableSet.of(), ImmutableSet.of());
  }
}
