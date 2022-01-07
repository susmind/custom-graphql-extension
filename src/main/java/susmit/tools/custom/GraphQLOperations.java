package susmit.tools.custom;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;

import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.route.Route;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import susmit.tools.custom.error.ExecuteErrorProvider;
import susmit.tools.custom.error.GraphQLCustomError;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.meta.model.operation.ExecutionType;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.dataloader.DataLoaderRegistry;
import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.Flow;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.event.EventContextFactory;
import org.mule.runtime.core.internal.routing.ChoiceRouter;
import org.mule.runtime.core.privileged.processor.AbstractInterceptingMessageProcessor;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.execution.Execution;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Content;

/**
 * This class is a container for operations, every public method in this class
 * will be taken as an extension operation.
 */
@Configurations(GraphQLConfiguration.class)
public class GraphQLOperations {

	private static final Logger logger = LoggerFactory.getLogger(GraphQLOperations.class);

	@Inject
	private ExpressionManager expressionManager;
	
	@DisplayName("GraphQL Router")
	@MediaType("application/json")
	@Throws(ExecuteErrorProvider.class)
	public Result<String, Map> route(@Config GraphQLConfiguration config,
			@Content Map<String,Object> content, ComponentLocation location) {
		
		if(content == null)
		{
			throw new ModuleException(GraphQLCustomError.EXECUTION_FAILURE, new RuntimeException("Payload is not valid")); 
		}
		
		try
		{
		logger.debug("Content received: " + content.toString());
		
		String query = (String) content.get("query");
		//String operation = (String) content.get("operationName");
		
		//logger.debug("Operation name: " + operation);
		
		DataLoaderRegistry registry = new DataLoaderRegistry();
		
		ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(registry).build();

		ExecutionResult executionResult = config.getGraphQLServer().execute(executionInput);

		//Object data = executionResult.getData();
		
		Map<String,Object> data = executionResult.toSpecification();
		
		List<GraphQLError> errors = executionResult.getErrors();
		
		logger.debug("Output data: " + data.toString());
		
        BindingContext ctx = BindingContext.builder()
                .addBinding("payload", new TypedValue(data, DataType.fromType(data.getClass())))
                .build();
        
			
		return Result.<String, Map>builder().attributes(new HashMap<String, String>())
                .mediaType(org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON)
                .output(expressionManager.evaluate("payload", DataType.JSON_STRING, ctx).getValue().toString())
                .build();
		}
		catch (Exception ex)
		{
			throw new ModuleException(GraphQLCustomError.EXECUTION_FAILURE, ex);
		}
		
	}

}
