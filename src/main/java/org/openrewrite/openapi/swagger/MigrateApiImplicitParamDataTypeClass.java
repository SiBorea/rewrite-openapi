package org.openrewrite.openapi.swagger;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Traits;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class MigrateApiImplicitParamDataTypeClass extends Recipe {
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate `@ApiImplicitParam(dataTypeClass=Foo.class)` to `@Parameter(schema=@Schema(implementation=Foo.class))`";
    }

    @Override
    public String getDescription() {
        return "Migrate `@ApiImplicitParam(dataTypeClass=Foo.class)` to `@Parameter(schema=@Schema(implementation=Foo.class))`.";
    }

    private boolean isDataTypeClass(Expression exp) {
        return exp instanceof J.Assignment && ((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName().equals("dataTypeClass");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // This recipe is after ChangeType recipe
        return Preconditions.check(
          new UsesMethod<>("io.swagger.annotations.ApiImplicitParam dataTypeClass()", false),
          new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                  J.Annotation anno = super.visitAnnotation(annotation, ctx);

                  StringBuilder tpl = new StringBuilder();
                  List<Expression> args = new ArrayList<>();
                  for (Expression exp : annotation.getArguments()) {
                      if (!args.isEmpty()) {
                          tpl.append(", ");
                      }
                      if (isDataTypeClass(exp)) {
                          J.FieldAccess fieldAccess = (J.FieldAccess) ((J.Assignment) exp).getAssignment();
                          tpl.append("schema = @Schema(implementation = #{any()})");
                          args.add(fieldAccess);
                      } else {
                          tpl.append("#{any()}");
                          args.add(exp);
                      }
                  }
                  anno = JavaTemplate.builder(tpl.toString())
                    .imports(FQN_SCHEMA)
                    .build()
                    .apply(updateCursor(annotation), annotation.getCoordinates().replaceArguments(), args.toArray());
                  maybeAddImport(FQN_SCHEMA, false);
                  return maybeAutoFormat(annotation, anno, ctx, getCursor().getParentTreeCursor());
              }
          }
        );
    }
}
