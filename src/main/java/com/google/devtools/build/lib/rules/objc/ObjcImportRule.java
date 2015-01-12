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

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.Type.BOOLEAN;
import static com.google.devtools.build.lib.packages.Type.LABEL_LIST;

import com.google.devtools.build.lib.analysis.BlazeRule;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.util.FileType;

/**
 * Rule definition for {@code objc_import}.
 */
@BlazeRule(name = "objc_import",
    factoryClass = ObjcImport.class,
    ancestors = { ObjcRuleClasses.ObjcBaseRule.class })
public class ObjcImportRule implements RuleDefinition {
  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
    return builder
        /*<!-- #BLAZE_RULE(objc_import).IMPLICIT_OUTPUTS -->
        <ul>
         <li><code><var>name</var>.xcodeproj/project.pbxproj</code>: An Xcode project file which
             can be used to develop or build on a Mac.</li>
        </ul>
        <!-- #END_BLAZE_RULE.IMPLICIT_OUTPUTS -->*/
        .setImplicitOutputsFunction(ObjcRuleClasses.PBXPROJ)
        /* <!-- #BLAZE_RULE(objc_import).ATTRIBUTE(archives) -->
        The list of <code>.a</code> files provided to Objective-C targets that
        depend on this target.
        ${SYNOPSIS}
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("archives", LABEL_LIST)
            .mandatory()
            .nonEmpty()
            .allowedFileTypes(FileType.of(".a")))
        /* <!-- #BLAZE_RULE(objc_import).ATTRIBUTE(alwayslink) -->
        If 1, any bundle or binary that depends (directly or indirectly) on this
        library will link in all the archive files listed in
        <code>archives</code>, even if some contain no symbols referenced by the
        binary.
        ${SYNOPSIS}
        This is useful if your code isn't explicitly called by code in
        the binary, e.g., if your code registers to receive some callback
        provided by some service.
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(attr("alwayslink", BOOLEAN))
        .removeAttribute("deps")
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = objc_import, TYPE = LIBRARY, FAMILY = Objective-C) -->

${ATTRIBUTE_SIGNATURE}

<p>This rule encapsulates an already-compiled static library in the form of an
<code>.a</code> file. It also allows exporting headers and resources using the same
attributes supported by <code>objc_library</code>.</p>

${ATTRIBUTE_DEFINITION}

<!-- #END_BLAZE_RULE -->*/
