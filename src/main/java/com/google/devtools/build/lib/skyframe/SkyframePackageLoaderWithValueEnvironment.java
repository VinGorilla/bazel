// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.skyframe;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Root;
import com.google.devtools.build.lib.blaze.BlazeDirectories;
import com.google.devtools.build.lib.packages.NoSuchPackageException;
import com.google.devtools.build.lib.packages.NoSuchTargetException;
import com.google.devtools.build.lib.packages.Package;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.skyframe.SkyframeExecutor.SkyframePackageLoader;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.syntax.Label.SyntaxException;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.view.config.BuildConfiguration.Fragment;
import com.google.devtools.build.lib.view.config.BuildOptions;
import com.google.devtools.build.lib.view.config.InvalidConfigurationException;
import com.google.devtools.build.lib.view.config.PackageProviderForConfigurations;
import com.google.devtools.build.skyframe.MemoizingEvaluator;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;

import java.io.IOException;

/**
 * Repeats functionality of {@link SkyframePackageLoader} but uses
 * {@link SkyFunction.Environment#getValue} instead of {@link MemoizingEvaluator#evaluate}
 * for node evaluation
 */
class SkyframePackageLoaderWithValueEnvironment implements
    PackageProviderForConfigurations {
  private final SkyFunction.Environment env;

  public SkyframePackageLoaderWithValueEnvironment(SkyFunction.Environment env) {
    this.env = env;
  }

  private Package getPackage(String packageName) throws NoSuchPackageException{
    SkyKey key = PackageValue.key(new PathFragment(packageName));
    PackageValue value = (PackageValue) env.getValueOrThrow(key, NoSuchPackageException.class);
    return value == null ? null : value.getPackage();
  }

  @Override
  public Package getLoadedPackage(final String packageName) throws NoSuchPackageException {
    try {
      return getPackage(packageName);
    } catch (NoSuchPackageException e) {
      if (e.getPackage() != null) {
        return e.getPackage();
      }
      throw e;
    }
  }

  @Override
  public Target getLoadedTarget(Label label) throws NoSuchPackageException,
      NoSuchTargetException {
    Package pkg = getLoadedPackage(label.getPackageName());
    return pkg == null ? null : pkg.getTarget(label.getName());
  }

  @Override
  public boolean isTargetCurrent(Target target) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDependency(Package pkg, String fileName) throws SyntaxException, IOException {
    Label label = Label.create(pkg.getName(), fileName);
    LabelAndConfiguration lac = new LabelAndConfiguration(label, null);
    Path pathToArtifact = pkg.getPackageDirectory().getRelative(fileName);
    Artifact artifact = new Artifact(pathToArtifact,
        Root.asSourceRoot(pkg.getSourceRoot()),
        pkg.getNameFragment().getRelative(fileName),
        lac);

    FileValue result = (FileValue) env.getValue(FileValue.key(artifact));
    if (result != null && !result.exists()) {
      throw new IOException();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Fragment> T getFragment(BuildOptions buildOptions, Class<T> fragmentType)
      throws InvalidConfigurationException {
    ConfigurationFragmentValue fragmentNode = (ConfigurationFragmentValue) env.getValueOrThrow(
        ConfigurationFragmentValue.key(buildOptions, fragmentType),
        InvalidConfigurationException.class);
    if (fragmentNode == null) {
      return null;
    }
    return (T) fragmentNode.getFragment();
  }

  @Override
  public BlazeDirectories getDirectories() {
    return BuildVariableValue.BLAZE_DIRECTORIES.get(env);
  }

  @Override
  public boolean valuesMissing() {
    return env.valuesMissing();
  }
}