/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.RemoveContentVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeDependencyClassifier extends Recipe {

    @Option(displayName = "Group",
            description = "The first part of a dependency coordinate 'com.google.guava:guava:VERSION'.",
            example = "com.google.guava")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The second part of a dependency coordinate 'com.google.guava:guava:VERSION'.",
            example = "guava")
    String artifactId;

    /**
     * If null, strips the scope from an existing dependency.
     */
    @Option(displayName = "New classifier",
            description = "Classifier to apply to specified Maven dependency. " +
                    "May be omitted, which indicates that no classifier should be added and any existing scope be removed from the dependency.",
            example = "jar",
            required = false)
    @Nullable
    String newClassifier;

    @Override
    public String getDisplayName() {
        return "Change Maven dependency classifier";
    }

    @Override
    public String getDescription() {
        return "Add or alter the classifier of the specified dependency.";
    }

    @Override
    public MavenVisitor<ExecutionContext> getVisitor() {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (isDependencyTag()) {
                    if (groupId.equals(tag.getChildValue("groupId").orElse(getResolutionResult().getPom().getGroupId())) &&
                            artifactId.equals(tag.getChildValue("artifactId").orElse(null))) {
                        Optional<Xml.Tag> scope = tag.getChild("classifier");
                        if (scope.isPresent()) {
                            if (newClassifier == null) {
                                doAfterVisit(new RemoveContentVisitor<>(scope.get(), false));
                            } else if (!newClassifier.equals(scope.get().getValue().orElse(null))) {
                                doAfterVisit(new ChangeTagValueVisitor<>(scope.get(), newClassifier));
                            }
                        } else if (newClassifier != null) {
                            doAfterVisit(new AddToTagVisitor<>(tag, Xml.Tag.build("<classifier>" + newClassifier + "</classifier>")));
                        }
                    }
                }

                return super.visitTag(tag, ctx);
            }
        };
    }

}
