package io.tesla.proviso.archive;

import java.util.Collections;
import java.util.List;
import org.codehaus.plexus.util.SelectorUtils;

public class Selector {

  private final List<String> includes;
  private final List<String> excludes;

  public Selector(List<String> includes, List<String> excludes) {
    this.includes = includes != null ? includes : Collections.<String>emptyList();
    this.excludes = excludes != null ? excludes : Collections.<String>emptyList();
  }

  public boolean include(String entryName) {
    //
    // If we get an exclusion that matches then just carry on.
    //
    boolean exclude = false;
    if (!excludes.isEmpty()) {
      for (String excludePattern : excludes) {
        if (isExcluded(excludePattern, entryName)) {
          exclude = true;
          break;
        }
      }
    }
    if (exclude) {
      return false;
    }
    boolean include = false;
    if (!includes.isEmpty()) {
      for (String includePattern : includes) {
        if (isIncluded(includePattern, entryName)) {
          include = true;
          break;
        }
      }
    } else {
      include = true;
    }
    return include;
  }

  private boolean isExcluded(String excludePattern, String entry) {
    return SelectorUtils.match(excludePattern, entry);
  }

  private boolean isIncluded(String includePattern, String entry) {
    return SelectorUtils.match(includePattern, entry);
  }
}
