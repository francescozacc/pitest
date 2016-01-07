package org.pitest.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.scm.ChangeFile;
import org.apache.maven.scm.ChangeSet;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.command.changelog.ChangeLogScmRequest;
import org.apache.maven.scm.command.changelog.ChangeLogScmResult;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;
import org.codehaus.plexus.util.StringUtils;
import org.pitest.functional.F;
import org.pitest.functional.FCollection;
import org.pitest.functional.Option;
import org.pitest.functional.predicate.Predicate;
import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.CombinedStatistics;

/**
 * Goal which runs a coverage mutation report only for files that have been
 * modified or introduced locally based on the source control configured in
 * maven.
 */
@Mojo(name = "scmMutationCoverage", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.TEST)
public class ScmMojo extends AbstractPitMojo {

  @Component
  private ScmManager      manager;

  /**
   * List of scm status to include. Names match those defined by the maven scm
   * plugin.
   *
   * Common values include ADDED,MODIFIED (the defaults) & UNKNOWN.
   */
  @Parameter(property = "include")
  private HashSet<String> include;

  /**
   * Analyze local changes. If set to false analyzes last commited change.
   */
  @Parameter(defaultValue = "false", property = "analyseLastCommit")
  private boolean analyseLastCommit;

  /**
   * Connection type to use when querying scm for changed files. Can either be
   * "connection" or "developerConnection".
   */
  @Parameter(property = "connectionType", defaultValue = "connection")
  private String          connectionType;

  /**
   * Project basedir
   */
  @Parameter(property = "basedir", required = true)
  private File            basedir;

  /**
   * Base of scm root. For a multi module project this is probably the parent
   * project.
   */
  @Parameter(property = "project.parent.basedir")
  private File            scmRootDir;

  public ScmMojo(final RunPitStrategy executionStrategy,
                 final ScmManager manager, Predicate<Artifact> filter,
                 PluginServices plugins, boolean analyseLastCommit) {
    super(executionStrategy, filter, plugins);
    this.manager = manager;
    this.analyseLastCommit = analyseLastCommit;
  }

  public ScmMojo() {

  }

  @Override
  protected Option<CombinedStatistics> analyse() throws MojoExecutionException {

    this.targetClasses = makeConcreteList(findModifiedClassNames());

    if (this.targetClasses.isEmpty()) {
      this.getLog().info(
          "No modified files found - nothing to mutation test, analyseLastCommit=" + this.analyseLastCommit);
      return Option.none();
    }

    logClassNames();
    defaultTargetTestsToGroupNameIfNoValueSet();
    final ReportOptions data = new MojoToReportOptionsConverter(this,
        new SurefireConfigConverter(), filter).convert();
    data.setFailWhenNoMutations(false);

    return Option.some(this.goalStrategy.execute(detectBaseDir(), data,
        plugins, new HashMap<String, String>()));

  }

  private void defaultTargetTestsToGroupNameIfNoValueSet() {
    if (this.getTargetTests() == null) {
      this.targetTests = makeConcreteList(Collections.singletonList(this
          .getProject().getGroupId() + "*"));
    }
  }

  private void logClassNames() {
    for (final String each : this.targetClasses) {
      this.getLog().info("Will mutate changed class " + each);
    }
  }

  private List<String> findModifiedClassNames() throws MojoExecutionException {

    final File sourceRoot = new File(this.project.getBuild()
        .getSourceDirectory());

    final List<String> modifiedPaths = findModifiedPaths();
    return FCollection.flatMap(modifiedPaths, new PathToJavaClassConverter(
        sourceRoot.getAbsolutePath()));

  }

  private List<String> findModifiedPaths() throws MojoExecutionException {
    try {
      final Set<ScmFileStatus> statusToInclude = makeStatusSet();
      final List<String> modifiedPaths = new ArrayList<String>();
      final ScmRepository repository = this.manager
          .makeScmRepository(getSCMConnection());
      final File scmRoot = scmRoot();
      this.getLog().info("Scm root dir is " + scmRoot);

      if (analyseLastCommit) {
        lastCommitChanges(statusToInclude, modifiedPaths, repository, scmRoot);
      } else {
        localChanges(statusToInclude, modifiedPaths, repository, scmRoot);
      }
      return modifiedPaths;
    } catch (final ScmException e) {
      throw new MojoExecutionException("Error while querying scm", e);
    }

  }

  private void lastCommitChanges(Set<ScmFileStatus> statusToInclude, List<String> modifiedPaths, ScmRepository repository, File scmRoot) throws ScmException {
    ChangeLogScmRequest scmRequest = new ChangeLogScmRequest(repository, new ScmFileSet(scmRoot));
    scmRequest.setLimit(1);

    ChangeLogScmResult changeLogScmResult = this.manager.changeLog(scmRequest);
    if (changeLogScmResult.isSuccess()) {
      List<ChangeSet> changeSets = changeLogScmResult.getChangeLog().getChangeSets();
      if (!changeSets.isEmpty()) {
        List<ChangeFile> files = changeSets.get(0).getFiles();

        for (final ChangeFile changeFile : files) {
          if (statusToInclude.contains(changeFile.getAction())) {
            modifiedPaths.add(changeFile.getName());
          }
        }
      }
    }
  }

  private void localChanges(Set<ScmFileStatus> statusToInclude, List<String> modifiedPaths, ScmRepository repository, File scmRoot) throws ScmException {
    final StatusScmResult status = this.manager.status(repository,
            new ScmFileSet(scmRoot));

    for (final ScmFile file : status.getChangedFiles()) {
      if (statusToInclude.contains(file.getStatus())) {
        modifiedPaths.add(file.getPath());
      }
    }
  }

  private Set<ScmFileStatus> makeStatusSet() {
    if ((this.include == null) || this.include.isEmpty()) {
      return new HashSet<ScmFileStatus>(Arrays.asList(
          ScmStatus.ADDED.getStatus(), ScmStatus.MODIFIED.getStatus()));
    }
    final Set<ScmFileStatus> s = new HashSet<ScmFileStatus>();
    FCollection.mapTo(this.include, stringToMavenScmStatus(), s);
    return s;
  }

  private static F<String, ScmFileStatus> stringToMavenScmStatus() {
    return new F<String, ScmFileStatus>() {
      @Override
      public ScmFileStatus apply(final String a) {
        return ScmStatus.valueOf(a.toUpperCase()).getStatus();
      }

    };
  }

  private File scmRoot() {
    if (this.scmRootDir != null) {
      return this.scmRootDir;
    }
    return this.basedir;
  }

  private String getSCMConnection() throws MojoExecutionException {

    if (this.project.getScm() == null) {
      throw new MojoExecutionException("No SCM Connection configured.");
    }

    final String scmConnection = this.project.getScm().getConnection();
    if ("connection".equalsIgnoreCase(this.connectionType)
        && StringUtils.isNotEmpty(scmConnection)) {
      return scmConnection;
    }

    final String scmDeveloper = this.project.getScm().getDeveloperConnection();
    if ("developerconnection".equalsIgnoreCase(this.connectionType)
        && StringUtils.isNotEmpty(scmDeveloper)) {
      return scmDeveloper;
    }

    throw new MojoExecutionException("SCM Connection is not set.");

  }

  public void setConnectionType(final String connectionType) {
    this.connectionType = connectionType;
  }

  public void setScmRootDir(final File scmRootDir) {
    this.scmRootDir = scmRootDir;
  }

  /**
   * A bug in maven 2 requires that all list fields declare a concrete list type
   */
  private static ArrayList<String> makeConcreteList(List<String> list) {
    return new ArrayList<String>(list);
  }

}
