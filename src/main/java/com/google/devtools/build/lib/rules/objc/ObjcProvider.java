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

package com.google.devtools.build.lib.rules.objc;

import static com.google.devtools.build.lib.collect.nestedset.Order.LINK_ORDER;
import static com.google.devtools.build.lib.collect.nestedset.Order.STABLE_ORDER;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.xcode.xcodegen.proto.XcodeGenProtos.TargetControl;

import java.util.HashMap;
import java.util.Map;

/**
 * A provider that provides all compiling and linking information in the transitive closure of its
 * deps that are needed for building Objective-C rules.
 */
@Immutable
public final class ObjcProvider implements TransitiveInfoProvider {
  /**
   * Represents one of the things this provider can provide transitively. Things are provided as
   * {@link NestedSet}s of type E.
   */
  public static class Key<E> {
    private final Order order;

    private Key(Order order) {
      this.order = Preconditions.checkNotNull(order);
    }
  }

  public static final Key<Artifact> LIBRARY = new Key<>(LINK_ORDER);
  public static final Key<Artifact> IMPORTED_LIBRARY = new Key<>(LINK_ORDER);

  /**
   * Indicates which libraries to load with {@code -force_load}. This is a subset of the union of
   * the {@link #LIBRARY} and {@link #IMPORTED_LIBRARY} sets.
   */
  public static final Key<Artifact> FORCE_LOAD_LIBRARY = new Key<>(LINK_ORDER);

  /**
   * Libraries to pass with -force_load flags when setting the linkopts in Xcodegen. This is needed
   * in addition to {@link #FORCE_LOAD_LIBRARY} because that one, contains a mixture of import
   * archives (which are not built by Xcode) and built-from-source library archives (which are built
   * by Xcode). Archives that are built by Xcode are placed directly under
   * {@code BUILT_PRODUCTS_DIR} while those not built by Xcode appear somewhere in the Bazel
   * workspace under {@code WORKSPACE_ROOT}.
   */
  public static final Key<String> FORCE_LOAD_FOR_XCODEGEN = new Key<>(LINK_ORDER);

  public static final Key<Artifact> HEADER = new Key<>(STABLE_ORDER);
  public static final Key<PathFragment> INCLUDE = new Key<>(LINK_ORDER);

  /**
   * Paths relative to {@code $(SDKROOT)/usr/include} which are added to header search paths (not
   * user header search paths) for compilation actions.
   */
  public static final Key<PathFragment> SDK_INCLUDE = new Key<>(LINK_ORDER);

  /**
   * Key for values in {@code defines} attributes. These are passed as {@code -D} flags to all
   * invocations of the compiler for this target and all depending targets.
   */
  public static final Key<String> DEFINE = new Key<>(STABLE_ORDER);

  public static final Key<Artifact> ASSET_CATALOG = new Key<>(STABLE_ORDER);

  /**
   * Added to {@link TargetControl#getGeneralResourceFileList()} when running Xcodegen.
   */
  public static final Key<Artifact> GENERAL_RESOURCE_FILE = new Key<>(STABLE_ORDER);

  /**
   * Exec paths of {@code .bundle} directories corresponding to imported bundles to link.
   * These are passed to Xcodegen.
   */
  public static final Key<PathFragment> BUNDLE_IMPORT_DIR = new Key<>(STABLE_ORDER);

  /**
   * Files that are plopped into the final bundle at some arbitrary bundle path. Note that these are
   * not passed to Xcodegen, and these don't include information about where the file originated
   * from.
   */
  public static final Key<BundleableFile> BUNDLE_FILE = new Key<>(STABLE_ORDER);

  public static final Key<PathFragment> XCASSETS_DIR = new Key<>(STABLE_ORDER);
  public static final Key<String> SDK_DYLIB = new Key<>(STABLE_ORDER);
  public static final Key<SdkFramework> SDK_FRAMEWORK = new Key<>(STABLE_ORDER);
  public static final Key<SdkFramework> WEAK_SDK_FRAMEWORK = new Key<>(STABLE_ORDER);
  public static final Key<Xcdatamodel> XCDATAMODEL = new Key<>(STABLE_ORDER);
  public static final Key<Flag> FLAG = new Key<>(STABLE_ORDER);

  /**
   * Merge zips to include in the bundle. The entries of these zip files are included in the final
   * bundle with the same path. The entries in the merge zips should not include the bundle root
   * path (e.g. {@code Foo.app}).
   */
  public static final Key<Artifact> MERGE_ZIP = new Key<>(STABLE_ORDER);

  /**
   * Exec paths of {@code .framework} directories corresponding to frameworks to link. These cause
   * -F arguments (framework search paths) to be added to each compile action, and -framework (link
   * framework) arguments to be added to each link action.
   */
  public static final Key<PathFragment> FRAMEWORK_DIR = new Key<>(LINK_ORDER);

  /**
   * Files in {@code .framework} directories that should be included as inputs when compiling and
   * linking.
   */
  public static final Key<Artifact> FRAMEWORK_FILE = new Key<>(STABLE_ORDER);

  /**
   * Bundles which should be linked in as a nested bundle to the final application.
   */
  public static final Key<Bundling> NESTED_BUNDLE = new Key<>(STABLE_ORDER);

  /**
   * Flags that apply to a transitive build dependency tree. Each item in the enum corresponds to a
   * flag. If the item is included in the key {@link #FLAG}, then the flag is considered set.
   */
  public enum Flag {
    /**
     * Indicates that C++ (or Objective-C++) is used in any source file. This affects how the linker
     * is invoked.
    */
    USES_CPP;
  }

  private final ImmutableMap<Key<?>, NestedSet<?>> items;

  // Items which should be passed to direct dependers, but not transitive dependers.
  private final ImmutableMap<Key<?>, NestedSet<?>> nonPropagatedItems;

  private ObjcProvider(
      ImmutableMap<Key<?>, NestedSet<?>> items,
      ImmutableMap<Key<?>, NestedSet<?>> nonPropagatedItems) {
    this.items = Preconditions.checkNotNull(items);
    this.nonPropagatedItems = Preconditions.checkNotNull(nonPropagatedItems);
  }

  /**
   * All artifacts, bundleable files, etc. of the type specified by {@code key}.
   */
  @SuppressWarnings("unchecked")
  public <E> NestedSet<E> get(Key<E> key) {
    Preconditions.checkNotNull(key);
    NestedSetBuilder<E> builder = new NestedSetBuilder<E>(key.order);
    if (nonPropagatedItems.containsKey(key)) {
      builder.addAll((NestedSet<E>) nonPropagatedItems.get(key));
    }
    if (items.containsKey(key)) {
      builder.addAll((NestedSet<E>) items.get(key));
    }
    return builder.build();
  }

  /**
   * Indicates whether {@code flag} is set on this provider.
   */
  public boolean is(Flag flag) {
    return Iterables.contains(get(FLAG), flag);
  }

  /**
   * Indicates whether this provider has any asset catalogs. This is true whenever some target in
   * its transitive dependency tree specifies a non-empty {@code asset_catalogs} attribute.
   */
  public boolean hasAssetCatalogs() {
    return !get(XCASSETS_DIR).isEmpty();
  }

  /**
   * A builder for this context with an API that is optimized for collecting information from
   * several transitive dependencies.
   */
  public static final class Builder {
    private final Map<Key<?>, NestedSetBuilder<?>> items = new HashMap<>();
    private final Map<Key<?>, NestedSetBuilder<?>> nonPropagatedItems = new HashMap<>();

    private static void maybeAddEmptyBuilder(Map<Key<?>, NestedSetBuilder<?>> set, Key<?> key) {
      if (!set.containsKey(key)) {
        set.put(key, new NestedSetBuilder<>(key.order));
      }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void uncheckedAddAll(Key key, Iterable toAdd) {
      maybeAddEmptyBuilder(items, key);
      items.get(key).addAll(toAdd);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void uncheckedAddTransitive(Key key, NestedSet toAdd, boolean propagate) {
      Map<Key<?>, NestedSetBuilder<?>> set = propagate ? items : nonPropagatedItems;
      maybeAddEmptyBuilder(set, key);
      set.get(key).addTransitive(toAdd);
    }

    /**
     * Adds elements in items, and propagate them to any (transitive) dependers on this
     * ObjcProvider.
     */
    public <E> Builder addTransitiveAndPropagate(Key<E> key, NestedSet<E> items) {
      uncheckedAddTransitive(key, items, true);
      return this;
    }

    /**
     * Add all elements from provider, and propagate them to any (transitive) dependers on this
     * ObjcProvider.
     */
    public Builder addTransitiveAndPropagate(ObjcProvider provider) {
      for (Map.Entry<Key<?>, NestedSet<?>> typeEntry : provider.items.entrySet()) {
        uncheckedAddTransitive(typeEntry.getKey(), typeEntry.getValue(), true);
      }
      return this;
    }

    /**
     * Add all elements from providers, and propagate them to any (transitive) dependers on this
     * ObjcProvider.
     */
    public Builder addTransitiveAndPropagate(Iterable<ObjcProvider> providers) {
      for (ObjcProvider provider : providers) {
        addTransitiveAndPropagate(provider);
      }
      return this;
    }

    /**
     * Add elements from providers, but don't propagate them to any dependers on this ObjcProvider.
     * These elements will be exposed to {@link #get(Key<E>)} calls, but not to any ObjcProviders
     * which add this provider to themself.
     */
    public Builder addTransitiveWithoutPropagating(Iterable<ObjcProvider> providers) {
      for (ObjcProvider provider : providers) {
        for (Map.Entry<Key<?>, NestedSet<?>> typeEntry : provider.items.entrySet()) {
          uncheckedAddTransitive(typeEntry.getKey(), typeEntry.getValue(), false);
        }
      }
      return this;
    }

    /**
     * Add element, and propagate it to any (transitive) dependers on this ObjcProvider.
     */
    public <E> Builder add(Key<E> key, E toAdd) {
      uncheckedAddAll(key, ImmutableList.of(toAdd));
      return this;
    }

    /**
     * Add elements in toAdd, and propagate them to any (transitive) dependers on this ObjcProvider.
     */
    public <E> Builder addAll(Key<E> key, Iterable<? extends E> toAdd) {
      uncheckedAddAll(key, toAdd);
      return this;
    }

    public ObjcProvider build() {
      ImmutableMap.Builder<Key<?>, NestedSet<?>> propagated = new ImmutableMap.Builder<>();
      for (Map.Entry<Key<?>, NestedSetBuilder<?>> typeEntry : items.entrySet()) {
        propagated.put(typeEntry.getKey(), typeEntry.getValue().build());
      }
      ImmutableMap.Builder<Key<?>, NestedSet<?>> nonPropagated = new ImmutableMap.Builder<>();
      for (Map.Entry<Key<?>, NestedSetBuilder<?>> typeEntry : nonPropagatedItems.entrySet()) {
        nonPropagated.put(typeEntry.getKey(), typeEntry.getValue().build());
      }
      return new ObjcProvider(propagated.build(), nonPropagated.build());
    }
  }
}
