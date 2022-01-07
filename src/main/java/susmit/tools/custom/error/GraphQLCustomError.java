package susmit.tools.custom.error;

import java.util.Optional;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

/*
 * SDK Error module - https://github.com/mulesoft/docs-mule-sdk/blob/v1.1/modules/ROOT/pages/errors.adoc
 */
public enum GraphQLCustomError implements ErrorTypeDefinition<GraphQLCustomError> {
	
	EXECUTION_FAILURE; //Generic error for GraphQL 
	
	private ErrorTypeDefinition<? extends Enum<?>> parent;

	GraphQLCustomError(ErrorTypeDefinition<? extends Enum<?>> parent) {
        this.parent = parent;
    }

	GraphQLCustomError() {
    }

    @Override
    public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
        return Optional.ofNullable(parent);
    }
	
}
