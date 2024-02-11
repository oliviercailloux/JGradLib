package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitChecker {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitChecker.class);
  @SuppressWarnings("unused")
  private static final Unchecker<GitAPIException, IllegalStateException> UNCHECKER =
      Unchecker.wrappingWith(IllegalStateException::new);

  public static GitChecker create() {
    return new GitChecker();
  }

  private ImmutableTable<String, String, ObjectId> remoteRefs;
  private ImmutableMap<String, ObjectId> localRefs;

  public GitChecker() {
    remoteRefs = null;
    localRefs = null;
  }

  public void checkCommonRefs(Git git) throws GitAPIException {
    /** Seems like this also includes HEAD when detached. */
    final List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
    final List<Ref> allRefs =
        IO_UNCHECKER.getUsing(() -> git.getRepository().getRefDatabase().getRefs());
    LOGGER.debug("All refs: {}, branches: {}.", allRefs, branches);

    parse(branches);

    final Ref head = IO_UNCHECKER.getUsing(() -> git.getRepository().findRef(Constants.HEAD));
    checkArgument(head != null, "Did you forget to create the repository?");
    final String headRef = head.getTarget().getName();
    checkArgument(headRef.equals("refs/heads/master") || headRef.equals("refs/heads/main"),
        headRef);

    final ImmutableMap<String, ObjectId> originRefs = remoteRefs.row("origin");
    final SetView<String> commonRefShortNames =
        Sets.intersection(originRefs.keySet(), localRefs.keySet());
    final ImmutableSet<String> disagreeingRefShortNames =
        commonRefShortNames.stream().filter((s) -> !originRefs.get(s).equals(localRefs.get(s)))
            .collect(ImmutableSet.toImmutableSet());
    checkArgument(disagreeingRefShortNames.isEmpty(),
        String.format("Disagreeing: %s. Origin refs: %s; local refs: %s.", disagreeingRefShortNames,
            originRefs, localRefs));
  }

  private void parse(List<Ref> branches) {
    final ImmutableTable.Builder<String, String, ObjectId> remoteRefsBuilder =
        ImmutableTable.builder();
    final ImmutableMap.Builder<String, ObjectId> localRefsBuilder = ImmutableMap.builder();
    for (Ref branch : branches) {
      final String fullName = branch.getName();
      final Pattern refPattern =
          Pattern.compile("refs/(?<kind>[^/]+)(/(?<remoteName>[^/]+))?/(?<shortName>[^/]+)");
      final Matcher matcher = refPattern.matcher(fullName);
      checkArgument(matcher.matches(), fullName);
      final String kind = matcher.group("kind");
      final String remoteName = matcher.group("remoteName");
      final String shortName = matcher.group("shortName");
      final ObjectId objectId = branch.getObjectId();
      switch (kind) {
        case "remotes":
          checkState(remoteName.length() >= 1);
          remoteRefsBuilder.put(remoteName, shortName, objectId);
          break;
        case "heads":
          checkState(remoteName == null, fullName);
          localRefsBuilder.put(shortName, objectId);
          break;
        default:
          throw new IllegalArgumentException("Unknown ref kind: " + kind);
      }
    }
    remoteRefs = remoteRefsBuilder.build();
    localRefs = localRefsBuilder.build();
  }
}
